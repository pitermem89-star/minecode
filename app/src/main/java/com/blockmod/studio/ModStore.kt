package com.blockmod.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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
                    code = o.optString("code", "").let { c ->
                        if (c.startsWith("// Fabric, Minecraft") || c.startsWith("// Forge, Minecraft")) "" else c
                    }
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
