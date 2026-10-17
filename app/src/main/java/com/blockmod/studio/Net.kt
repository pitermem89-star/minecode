package com.blockmod.studio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ModrinthMod(
    val slug: String,
    val title: String,
    val description: String,
    val downloads: Long
)

fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent", "BlockModStudio/0.3")
    conn.connectTimeout = 15000
    conn.readTimeout = 15000
    val stream = if (conn.responseCode < 400) conn.inputStream else conn.errorStream
    return stream.bufferedReader().use { it.readText() }
}

// Все релизные версии Minecraft с серверов Mojang
suspend fun fetchReleaseVersions(): List<String>? = withContext(Dispatchers.IO) {
    try {
        val text = httpGet("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val arr = JSONObject(text).getJSONArray("versions")
        val result = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            if (v.getString("type") == "release") result.add(v.getString("id"))
        }
        if (result.isEmpty()) null else result
    } catch (e: Exception) {
        null
    }
}

fun loadCachedVersions(context: Context): List<String> {
    return try {
        val raw = context.getSharedPreferences("cache", Context.MODE_PRIVATE)
            .getString("versions", null) ?: return fallbackVersions
        val arr = JSONArray(raw)
        val list = (0 until arr.length()).map { arr.getString(it) }
        if (list.isEmpty()) fallbackVersions else list
    } catch (e: Exception) {
        fallbackVersions
    }
}

fun saveCachedVersions(context: Context, list: List<String>) {
    context.getSharedPreferences("cache", Context.MODE_PRIVATE)
        .edit().putString("versions", JSONArray(list).toString()).apply()
}

// Поиск модов на Modrinth
suspend fun searchModrinth(query: String): List<ModrinthMod>? = withContext(Dispatchers.IO) {
    try {
        val facets = URLEncoder.encode("[[\"project_type:mod\"]]", "UTF-8")
        val q = URLEncoder.encode(query, "UTF-8")
        val index = if (query.isBlank()) "downloads" else "relevance"
        val text = httpGet(
            "https://api.modrinth.com/v2/search?query=$q&limit=20&index=$index&facets=$facets"
        )
        val hits = JSONObject(text).getJSONArray("hits")
        (0 until hits.length()).map { i ->
            val h = hits.getJSONObject(i)
            ModrinthMod(
                slug = h.getString("slug"),
                title = h.getString("title"),
                description = h.optString("description", ""),
                downloads = h.optLong("downloads", 0)
            )
        }
    } catch (e: Exception) {
        null
    }
}
