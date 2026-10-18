package com.blockmod.studio

import org.json.JSONArray
import org.json.JSONObject

class Versions(
    val fabricLoader: String = "",
    val fabricApi: String = "",
    val forge: String = ""
)

fun buildSupported(mod: ModProject): Boolean =
    (mod.loader == "Fabric" && (mod.mcVersion == "1.21.1" || mod.mcVersion == "1.20.1")) ||
        (mod.loader == "Forge" && mod.mcVersion == "1.20.1")

private fun javaOf(mod: ModProject): Int =
    if (mod.loader == "Fabric" && mod.mcVersion == "1.21.1") 21 else 17

private fun gradleOf(mod: ModProject): String =
    if (mod.loader == "Forge") "8.1.1" else "8.10"

fun projectFiles(mod: ModProject, blocks: List<ModBlock>, v: Versions): Map<String, String> {
    val modId = modIdOf(mod)
    val files = LinkedHashMap<String, String>()
    files[".github/workflows/build.yml"] = workflow(mod)
    files["src/main/java/com/example/$modId/ModMain.java"] =
        mod.code.ifBlank { generateJava(mod, blocks) }
    files["src/main/resources/assets/$modId/lang/en_us.json"] = langJson(modId, blocks)
    if (mod.loader == "Forge") {
        files["settings.gradle"] = forgeSettings(modId)
        files["build.gradle"] = forgeBuild(mod, modId, v)
        files["src/main/resources/META-INF/mods.toml"] = modsToml(mod, modId)
        files["src/main/resources/pack.mcmeta"] = JSONObject()
            .put("pack", JSONObject().put("description", "$modId resources").put("pack_format", 15))
            .toString(2)
    } else {
        files["settings.gradle"] = fabricSettings(modId)
        files["build.gradle"] = fabricBuild(mod, modId, v)
        files["src/main/resources/fabric.mod.json"] = fabricModJson(mod, modId)
    }
    return files
}

private fun workflow(mod: ModProject): String = """
name: Build mod
on:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${javaOf(mod)}
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: ${gradleOf(mod)}
      - run: gradle build --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: mod-jar
          path: build/libs/*.jar
""".trimStart()

private fun langJson(modId: String, blocks: List<ModBlock>): String {
    val o = JSONObject()
    blocks.forEach { b ->
        val id = safeId(b.a, "thing")
        if (b.type == "item" && b.b.isNotBlank()) o.put("item.$modId.$id", b.b)
        if (b.type == "block" && b.b.isNotBlank()) o.put("block.$modId.$id", b.b)
    }
    return o.toString(2)
}

// ---------- Fabric ----------

private fun fabricSettings(modId: String): String = """
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = '$modId'
""".trimStart()

private fun fabricBuild(mod: ModProject, modId: String, v: Versions): String {
    val java = javaOf(mod)
    return """
plugins {
    id 'fabric-loom' version '1.7-SNAPSHOT'
}

version = '1.0.0'
group = 'com.example'

base {
    archivesName = '$modId'
}

dependencies {
    minecraft 'com.mojang:minecraft:${mod.mcVersion}'
    mappings loom.officialMojangMappings()
    modImplementation 'net.fabricmc:fabric-loader:${v.fabricLoader}'
    modImplementation 'net.fabricmc.fabric-api:fabric-api:${v.fabricApi}'
}

tasks.withType(JavaCompile).configureEach {
    options.release = $java
}

java {
    sourceCompatibility = JavaVersion.VERSION_$java
    targetCompatibility = JavaVersion.VERSION_$java
}
""".trimStart()
}

private fun fabricModJson(mod: ModProject, modId: String): String {
    val j = JSONObject()
    j.put("schemaVersion", 1)
    j.put("id", modId)
    j.put("version", "1.0.0")
    j.put("name", mod.name)
    j.put("description", mod.description)
    j.put("authors", JSONArray())
    j.put("license", "All-Rights-Reserved")
    j.put("environment", "*")
    j.put("entrypoints", JSONObject().put("main", JSONArray().put("com.example.$modId.ModMain")))
    j.put(
        "depends",
        JSONObject()
            .put("fabricloader", ">=0.15.0")
            .put("minecraft", "~${mod.mcVersion}")
            .put("java", ">=${javaOf(mod)}")
            .put("fabric-api", "*")
    )
    return j.toString(2)
}

// ---------- Forge ----------

private fun forgeSettings(modId: String): String = """
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = 'MinecraftForge'
            url = 'https://maven.minecraftforge.net/'
        }
    }
}
rootProject.name = '$modId'
""".trimStart()

private fun forgeBuild(mod: ModProject, modId: String, v: Versions): String = """
plugins {
    id 'java'
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
}

version = '1.0.0'
group = 'com.example'

base {
    archivesName = '$modId'
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: 'official', version: '${mod.mcVersion}'
}

dependencies {
    minecraft 'net.minecraftforge:forge:${mod.mcVersion}-${v.forge}'
}

jar.finalizedBy('reobfJar')
""".trimStart()

private fun tomlEsc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

private fun modsToml(mod: ModProject, modId: String): String {
    val desc = mod.description.replace("'''", "").ifBlank { mod.name }
    return """
modLoader="javafml"
loaderVersion="[47,)"
license="All rights reserved"

[[mods]]
modId="$modId"
version="1.0.0"
displayName="${tomlEsc(mod.name)}"
description='''$desc'''

[[dependencies.$modId]]
modId="forge"
mandatory=true
versionRange="[47,)"
ordering="NONE"
side="BOTH"

[[dependencies.$modId]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.21)"
ordering="NONE"
side="BOTH"
""".trimStart()
}
