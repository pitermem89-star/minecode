package com.blockmod.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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

data class ModBlock(val type: String, val a: String, val b: String)

val loaders = listOf("Fabric", "Forge")

val fallbackVersions = listOf("1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5")

fun parseBlocks(json: String): List<ModBlock> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ModBlock(o.getString("type"), o.optString("a", ""), o.optString("b", ""))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun blocksToJson(list: List<ModBlock>): String {
    val arr = JSONArray()
    list.forEach { b ->
        arr.put(JSONObject().put("type", b.type).put("a", b.a).put("b", b.b))
    }
    return arr.toString()
}

object ModStore {
    private const val PREFS = "mods"
    private const val KEY = "list"

    fun load(context: Context): List<ModProject> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ModProject(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    mcVersion = o.getString("mcVersion"),
                    loader = o.optString("loader", "Fabric"),
                    description = o.optString("description", ""),
                    avatarPath = if (o.isNull("avatarPath")) null else o.getString("avatarPath"),
                    blocks = o.optString("blocks", "[]"),
                    code = o.optString("code", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<ModProject>) {
        val arr = JSONArray()
        list.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("name", m.name)
            o.put("mcVersion", m.mcVersion)
            o.put("loader", m.loader)
            o.put("description", m.description)
            o.put("avatarPath", m.avatarPath ?: JSONObject.NULL)
            o.put("blocks", m.blocks)
            o.put("code", m.code)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

// Копирует выбранную картинку в память приложения и уменьшает её
fun saveAvatar(context: Context, uri: Uri, id: String): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val file = File(context.filesDir, "avatar_${id}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}
