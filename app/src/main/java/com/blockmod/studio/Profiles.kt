package com.blockmod.studio

class Profile(
    val supported: Boolean,
    val note: String,
    val java: Int = 17,
    val release: Int = 17,
    val gradle: String = "8.10",
    val loomLatest: Boolean = false,
    val modern: Boolean = false,
    val api: Int = 0,
    val fg: Int = 6
)

private fun verParts(v: String): List<Int> =
    v.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

// Сравнение версий вроде "1.21.1" и "26.1.2": меньше нуля, ноль или больше нуля
fun mcCompare(a: String, b: String): Int {
    val x = verParts(a)
    val y = verParts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (d != 0) return d
    }
    return 0
}

private fun fabricProfile(mc: String): Profile = when {
    mcCompare(mc, "1.14") < 0 ->
        Profile(false, "Fabric существует только для Minecraft 1.14 и новее.")
    mcCompare(mc, "1.17") < 0 ->
        Profile(true, "Экспериментально.", java = 17, release = 8)
    mcCompare(mc, "1.18") < 0 ->
        Profile(true, "Экспериментально.", java = 17, release = 16)
    mcCompare(mc, "1.20") < 0 ->
        Profile(true, "Экспериментально.", java = 17, release = 17)
    mcCompare(mc, "1.20.5") < 0 ->
        Profile(true, "", java = 17, release = 17, api = 1)
    mcCompare(mc, "1.21.2") < 0 ->
        Profile(true, "", java = 21, release = 21, api = 1)
    mcCompare(mc, "1.21.11") < 0 ->
        Profile(true, "Экспериментально.", java = 21, release = 21, gradle = "current", loomLatest = true, api = 2)
    mcCompare(mc, "26.1") < 0 ->
        Profile(true, "Экспериментально.", java = 21, release = 21, gradle = "current", loomLatest = true)
    else ->
        Profile(true, "Экспериментально.", java = 25, release = 25, gradle = "current", loomLatest = true, modern = true)
}

private fun forgeProfile(mc: String): Profile = when {
    mcCompare(mc, "1.16.5") < 0 ->
        Profile(false, "Forge до 1.16.5 собирается очень старой системой. Её я пока не поддерживаю.")
    mcCompare(mc, "1.17") < 0 ->
        Profile(true, "Экспериментально.", java = 8, release = 8, gradle = "7.6.4", fg = 5)
    mcCompare(mc, "1.20") < 0 ->
        Profile(true, "Экспериментально.", java = 17, release = 17, gradle = "7.6.4", fg = 5)
    mcCompare(mc, "1.20.5") < 0 ->
        Profile(true, "", java = 17, release = 17, gradle = "8.1.1", api = 1, fg = 6)
    mcCompare(mc, "1.21.2") < 0 ->
        Profile(true, "Экспериментально.", java = 21, release = 21, gradle = "8.8", api = 1, fg = 6)
    else ->
        Profile(false, "Для Forge новее 1.21.1 нужна другая система сборки, её я пока не поддерживаю. Используй Fabric.")
}

fun profileOf(mod: ModProject): Profile =
    if (mod.loader == "Forge") forgeProfile(mod.mcVersion) else fabricProfile(mod.mcVersion)

fun packFormat(mc: String): Int = when {
    mcCompare(mc, "1.17") < 0 -> 6
    mcCompare(mc, "1.18") < 0 -> 7
    mcCompare(mc, "1.18.2") < 0 -> 8
    mcCompare(mc, "1.19.3") < 0 -> 9
    mcCompare(mc, "1.19.4") < 0 -> 12
    mcCompare(mc, "1.20") < 0 -> 13
    mcCompare(mc, "1.20.2") < 0 -> 15
    mcCompare(mc, "1.20.3") < 0 -> 18
    mcCompare(mc, "1.20.5") < 0 -> 22
    mcCompare(mc, "1.21") < 0 -> 41
    else -> 48
}
