package com.blockmod.studio

import org.json.JSONArray
import org.json.JSONObject

data class ModProject(
    val id: String,
    val name: String,
    val mcVersion: String,
    val loader: String,
    val description: String,
    val avatarPath: String?,
    val blocks: String,
    val code: String
)

private var uidCounter = 0L

fun nextBlockUid(): Long {
    uidCounter += 1
    return uidCounter
}

data class ModBlock(
    val type: String,
    val a: String = "",
    val b: String = "",
    val kids: List<ModBlock> = emptyList(),
    val uid: Long = nextBlockUid()
)

val loaders = listOf("Fabric", "Forge")

val fallbackVersions = listOf("1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5")

private fun parseBlock(o: JSONObject): ModBlock? {
    val type = o.optString("type", "")
    val a = o.optString("a", "")
    val b = o.optString("b", "")
    val kids = ArrayList<ModBlock>()
    val kidsArr = o.optJSONArray("kids")
    if (kidsArr != null) {
        for (i in 0 until kidsArr.length()) {
            parseBlock(kidsArr.getJSONObject(i))?.let { kids.add(it) }
        }
    }
    return when {
        type.isEmpty() -> null
        type == "command" && kids.isEmpty() -> ModBlock(
            "on_command", a, "",
            if (b.isBlank()) emptyList() else listOf(ModBlock("say", b))
        )
        type == "join" && kids.isEmpty() -> ModBlock(
            "on_join", "", "",
            if (a.isBlank()) emptyList() else listOf(ModBlock("say", a))
        )
        else -> ModBlock(type, a, b, kids)
    }
}

fun parseBlocks(json: String): List<ModBlock> {
    return try {
        val arr = JSONArray(json)
        val out = ArrayList<ModBlock>()
        for (i in 0 until arr.length()) {
            parseBlock(arr.getJSONObject(i))?.let { out.add(it) }
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}

private fun blockToJson(b: ModBlock): JSONObject {
    val o = JSONObject().put("type", b.type).put("a", b.a).put("b", b.b)
    val arr = JSONArray()
    b.kids.forEach { arr.put(blockToJson(it)) }
    return o.put("kids", arr)
}

fun blocksToJson(list: List<ModBlock>): String {
    val arr = JSONArray()
    list.forEach { arr.put(blockToJson(it)) }
    return arr.toString()
}
