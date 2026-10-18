package com.blockmod.studio

import org.json.JSONArray
import org.json.JSONObject

class Versions(
    val fabricLoader: String = "",
    val fabricApi: String = "",
    val forge: String = "",
    val loom: String = ""
)

// Сборка запускается для любой версии. Если версия не поддерживается,
// причина появится в карточке результата (см. projectFiles).
fun buildSupported(mod: ModProject): Boolean = true

fun fetchLoomLatest(): String {
    val xml = httpGet("https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml")
    return Regex("<release>([^<]+)</release>").find(xml)?.groupValues?.get(1)
        ?: Regex("<latest>([^<]+)</latest>").find(xml)?.groupValues?.get(1)
        ?: throw IllegalStateException("Не удалось узнать версию Fabric Loom")
}

fun projectFiles(mod: ModProject, blocks: List<ModBlock>, v: Versions): Map<String, String> {
    val p = profileOf(mod)
    if (!p.supported) throw IllegalStateException(p.note)
    val modId = modIdOf(mod)
    val files = LinkedHashMap<String, String>()
    files[".github/workflows/build.yml"] = workflow(p)
    files["src/main/java/com/example/$modId/ModMain.java"] =
        mod.code.ifBlank { generateJava(mod, blocks) }
    files["src/main/resources/assets/$modId/lang/en_us.json"] = langJson(modId, blocks, p)
    if (mod.loader == "Forge") {
        files["settings.gradle"] = forgeSettings(modId, p)
        files["build.gradle"] = forgeBuild(mod, modId, v, p)
        files["src/main/resources/META-INF/mods.toml"] = modsToml(mod, modId, v)
        files["src/main/resources/pack.mcmeta"] = JSONObject()
            .put(
                "pack",
                JSONObject()
                    .put("description", "$modId resources")
                    .put("pack_format", packFormat(mod.mcVersion))
            )
            .toString(2)
    } else {
        files["settings.gradle"] = fabricSettings(modId)
        files["build.gradle"] = fabricBuild(mod, modId, v, p)
        files["src/main/resources/fabric.mod.json"] = fabricModJson(mod, modId, p)
    }
    return files
}

private fun workflow(p: Profile): String = """
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
          java-version: ${p.java}
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: ${p.gradle}
      - run: gradle build --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: mod-jar
          path: build/libs/*.jar
""".trimStart()

private fun langJson(modId: String, blocks: List<ModBlock>, p: Profile): String {
    val o = JSONObject()
    blocks.forEach { b ->
        val id = safeId(b.a, "thing")
        if (b.type == "item" && b.b.isNotBlank()) o.put("item.$modId.$id", b.b)
        if (b.type == "block" && b.b.isNotBlank()) {
            o.put("block.$modId.$id", b.b)
            if (p.api == 2) o.put("item.$modId.$id", b.b)
        }
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

private fun fabricBuild(mod: ModProject, modId: String, v: Versions, p: Profile): String {
    val loom = if (p.loomLatest) v.loom.ifBlank { fetchLoomLatest() } else "1.7-SNAPSHOT"
    val pluginId = if (p.modern) "net.fabricmc.fabric-loom" else "fabric-loom"
    val mappings = if (p.modern) "" else "    mappings loom.officialMojangMappings()\n"
    val impl = if (p.modern) "implementation" else "modImplementation"
    val mc = mod.mcVersion
    return """
plugins {
    id '$pluginId' version '$loom'
}

version = '1.0.0'
group = 'com.example'

base {
    archivesName = '$modId'
}

dependencies {
    minecraft 'com.mojang:minecraft:$mc'
${mappings}    $impl 'net.fabricmc:fabric-loader:${v.fabricLoader}'
    $impl 'net.fabricmc.fabric-api:fabric-api:${v.fabricApi}'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = ${p.release}
}

java {
    sourceCompatibility = JavaVersion.toVersion(${p.release})
    targetCompatibility = JavaVersion.toVersion(${p.release})
}
""".trimStart()
}

private fun fabricModJson(mod: ModProject, modId: String, p: Profile): String {
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
            .put("minecraft", ">=${mod.mcVersion}")
            .put("java", ">=${p.release}")
            .put("fabric-api", "*")
    )
    return j.toString(2)
}

// ---------- Forge ----------

private fun forgeSettings(modId: String, p: Profile): String =
    if (p.fg == 5) {
        "rootProject.name = '$modId'\n"
    } else {
        """
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
    }

private fun forgeBuild(mod: ModProject, modId: String, v: Versions, p: Profile): String {
    val mc = mod.mcVersion
    val toolchain =
        if (p.java >= 17) "java.toolchain.languageVersion = JavaLanguageVersion.of(${p.java})\n" else ""
    return if (p.fg == 5) {
        """
buildscript {
    repositories {
        maven { url = 'https://maven.minecraftforge.net' }
        mavenCentral()
    }
    dependencies {
        classpath group: 'net.minecraftforge.gradle', name: 'ForgeGradle', version: '5.1.+', changing: true
    }
}

apply plugin: 'net.minecraftforge.gradle'
apply plugin: 'java'

version = '1.0.0'
group = 'com.example'
archivesBaseName = '$modId'

$toolchain
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

minecraft {
    mappings channel: 'official', version: '$mc'
}

dependencies {
    minecraft 'net.minecraftforge:forge:$mc-${v.forge}'
}

jar.finalizedBy('reobfJar')
""".trimStart()
    } else {
        """
plugins {
    id 'java'
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
}

version = '1.0.0'
group = 'com.example'

base {
    archivesName = '$modId'
}

$toolchain
tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

minecraft {
    mappings channel: 'official', version: '$mc'
}

dependencies {
    minecraft 'net.minecraftforge:forge:$mc-${v.forge}'
}

jar.finalizedBy('reobfJar')
""".trimStart()
    }
}

private fun tomlEsc(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

private fun modsToml(mod: ModProject, modId: String, v: Versions): String {
    val desc = mod.description.replace("'''", "").ifBlank { mod.name }
    val major = v.forge.substringBefore('.')
    val mc = mod.mcVersion
    return """
modLoader="javafml"
loaderVersion="[$major,)"
license="All rights reserved"

[[mods]]
modId="$modId"
version="1.0.0"
displayName="${tomlEsc(mod.name)}"
description='''$desc'''

[[dependencies.$modId]]
modId="forge"
mandatory=true
versionRange="[$major,)"
ordering="NONE"
side="BOTH"

[[dependencies.$modId]]
modId="minecraft"
mandatory=true
versionRange="[$mc,)"
ordering="NONE"
side="BOTH"
""".trimStart()
}
