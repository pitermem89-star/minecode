package com.blockmod.studio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.zip.ZipInputStream

class BuildResult(
    val ok: Boolean,
    val message: String,
    val runUrl: String?,
    val errors: List<String>
)

private class GhResp(val code: Int, val body: String)

private const val API = "https://api.github.com"

private fun gh(method: String, url: String, token: String, body: String? = null): GhResp {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = method
    conn.connectTimeout = 20000
    conn.readTimeout = 60000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "BlockModStudio/0.3")
    conn.setRequestProperty("Authorization", "Bearer $token")
    if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
    }
    val code = conn.responseCode
    val stream = if (code < 400) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
    return GhResp(code, text)
}

private fun ok(r: GhResp, what: String): GhResp {
    if (r.code !in 200..299) {
        val msg = try {
            JSONObject(r.body).optString("message", "")
        } catch (e: Exception) {
            ""
        }
        throw IllegalStateException("$what: ошибка ${r.code} $msg")
    }
    return r
}

// Скачивание файла: GitHub отвечает перенаправлением, по нему идём без токена
private fun download(url: String, token: String): ByteArray {
    var current = url
    var auth: String? = token
    for (i in 0 until 5) {
        val conn = URL(current).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 20000
        conn.readTimeout = 120000
        conn.setRequestProperty("User-Agent", "BlockModStudio/0.3")
        if (auth != null) {
            conn.setRequestProperty("Authorization", "Bearer $auth")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
        }
        val code = conn.responseCode
        if (code in 300..399) {
            current = conn.getHeaderField("Location")
                ?: throw IllegalStateException("Пустая ссылка на скачивание")
            auth = null
            continue
        }
        if (code >= 400) throw IllegalStateException("Скачивание не удалось: $code")
        return conn.inputStream.use { it.readBytes() }
    }
    throw IllegalStateException("Слишком много перенаправлений")
}

private fun fetchVersions(mod: ModProject): Versions {
    if (mod.loader == "Forge") {
        var forge = "47.3.7"
        try {
            val promos = JSONObject(
                httpGet("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
            ).getJSONObject("promos")
            val v = promos.optString(
                "${mod.mcVersion}-recommended",
                promos.optString("${mod.mcVersion}-latest", "")
            )
            if (v.isNotEmpty()) forge = v
        } catch (e: Exception) {
        }
        return Versions(forge = forge)
    }
    var loader = "0.16.10"
    try {
        val arr = JSONArray(httpGet("https://meta.fabricmc.net/v2/versions/loader"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("stable", false)) {
                loader = o.getString("version")
                break
            }
        }
    } catch (e: Exception) {
    }
    val game = URLEncoder.encode("[\"${mod.mcVersion}\"]", "UTF-8")
    val ld = URLEncoder.encode("[\"fabric\"]", "UTF-8")
    val vers = JSONArray(
        httpGet("https://api.modrinth.com/v2/project/fabric-api/version?game_versions=$game&loaders=$ld")
    )
    if (vers.length() == 0) throw IllegalStateException("Не нашёл Fabric API для ${mod.mcVersion}")
    return Versions(fabricLoader = loader, fabricApi = vers.getJSONObject(0).getString("version_number"))
}

private suspend fun putFile(
    token: String, owner: String, repo: String, branch: String, path: String, content: String
) {
    val url = "$API/repos/$owner/$repo/contents/$path"
    val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    for (attempt in 0 until 4) {
        val existing = gh("GET", "$url?ref=$branch", token)
        var sha: String? = null
        if (existing.code == 200) {
            val o = JSONObject(existing.body)
            sha = o.optString("sha", "")
            if (o.optString("content", "").replace("\n", "") == b64) return
        }
        val payload = JSONObject()
            .put("message", "BlockMod Studio")
            .put("content", b64)
            .put("branch", branch)
        if (!sha.isNullOrEmpty()) payload.put("sha", sha)
        val r = gh("PUT", url, token, payload.toString())
        if (r.code in 200..299) return
        if (r.code == 409) {
            delay(1500)
            continue
        }
        ok(r, "Отправка файла $path")
    }
    throw IllegalStateException("Не удалось отправить файл $path")
}

private fun extractJar(zip: ByteArray): Pair<String, ByteArray>? {
    ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
        var e = zis.nextEntry
        while (e != null) {
            if (e.name.endsWith(".jar") && !e.name.endsWith("-sources.jar")) {
                return Pair(e.name.substringAfterLast('/'), zis.readBytes())
            }
            e = zis.nextEntry
        }
    }
    return null
}

private val errRegex = Regex(
    "error:|\\be: |FAILURE:|What went wrong|Could not |cannot find symbol|does not exist|Execution failed",
    RegexOption.IGNORE_CASE
)
private val tsRegex = Regex("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ")

private fun extractErrors(zip: ByteArray): List<String> {
    val out = LinkedHashSet<String>()
    ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
        var e = zis.nextEntry
        while (e != null && out.size < 50) {
            if (e.name.contains("/") && e.name.contains("gradle build", ignoreCase = true)) {
                val text = String(zis.readBytes(), Charsets.UTF_8)
                val lines = text.lineSequence().map { it.replace(tsRegex, "").trimEnd() }.toList()
                for (i in lines.indices) {
                    if (errRegex.containsMatchIn(lines[i])) {
                        for (j in i..minOf(i + 2, lines.lastIndex)) {
                            if (lines[j].isNotBlank() && out.size < 50) out.add(lines[j].trim().take(220))
                        }
                    }
                }
            }
            e = zis.nextEntry
        }
    }
    return out.toList()
}

private fun saveJar(context: Context, fileName: String, bytes: ByteArray): String {
    return if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/java-archive")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlockModStudio")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать файл в Загрузках")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        "Загрузки/BlockModStudio/$fileName"
    } else {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, fileName).writeBytes(bytes)
        dir.absolutePath + "/" + fileName
    }
}

suspend fun buildMod(
    context: Context,
    token: String,
    mod: ModProject,
    blocks: List<ModBlock>,
    report: (String) -> Unit
): BuildResult = withContext(Dispatchers.IO) {
    var runUrl: String? = null
    try {
        if (!buildSupported(mod)) throw IllegalStateException("Эта версия пока не поддерживается")

        report("Проверяю аккаунт GitHub…")
        val owner = JSONObject(ok(gh("GET", "$API/user", token), "Аккаунт").body).getString("login")
        val modId = modIdOf(mod)
        val repo = "blockmod-" + modId.replace('_', '-')

        report("Узнаю версии инструментов…")
        val versions = fetchVersions(mod)
        val files = projectFiles(mod, blocks, versions)

        report("Готовлю репозиторий…")
        var info = gh("GET", "$API/repos/$owner/$repo", token)
        if (info.code == 404) {
            val payload = JSONObject()
                .put("name", repo)
                .put("private", true)
                .put("auto_init", true)
                .put("description", "Мод ${mod.name} (BlockMod Studio)")
            info = ok(gh("POST", "$API/user/repos", token, payload.toString()), "Создание репозитория")
            delay(3000)
        } else {
            ok(info, "Проверка репозитория")
        }
        val branch = JSONObject(info.body).optString("default_branch", "main")

        var n = 0
        for ((path, content) in files) {
            n++
            report("Отправляю файлы ($n из ${files.size})…")
            putFile(token, owner, repo, branch, path, content)
        }

        report("Запускаю сборку…")
        val startedAt = Instant.now().minusSeconds(30)
        var dispatched = false
        for (attempt in 0 until 8) {
            val r = gh(
                "POST",
                "$API/repos/$owner/$repo/actions/workflows/build.yml/dispatches",
                token,
                JSONObject().put("ref", branch).toString()
            )
            if (r.code in 200..299) {
                dispatched = true
                break
            }
            delay(3000)
        }
        if (!dispatched) throw IllegalStateException("GitHub не принял запуск сборки. Попробуй ещё раз через минуту.")

        var runId = 0L
        for (attempt in 0 until 30) {
            delay(3000)
            val r = gh("GET", "$API/repos/$owner/$repo/actions/runs?event=workflow_dispatch&per_page=5", token)
            if (r.code == 200) {
                val runs = JSONObject(r.body).getJSONArray("workflow_runs")
                for (i in 0 until runs.length()) {
                    val run = runs.getJSONObject(i)
                    if (Instant.parse(run.getString("created_at")).isAfter(startedAt)) {
                        runId = run.getLong("id")
                        runUrl = run.getString("html_url")
                        break
                    }
                }
            }
            if (runId != 0L) break
        }
        if (runId == 0L) throw IllegalStateException("Не нашёл запущенную сборку на GitHub.")

        val t0 = System.currentTimeMillis()
        var conclusion = ""
        while (true) {
            val r = ok(gh("GET", "$API/repos/$owner/$repo/actions/runs/$runId", token), "Статус сборки")
            val o = JSONObject(r.body)
            val status = o.getString("status")
            val sec = (System.currentTimeMillis() - t0) / 1000
            if (status == "completed") {
                conclusion = o.optString("conclusion", "")
                break
            }
            val label = if (status == "queued") "Сборка в очереди…" else "Идёт сборка…"
            report("$label ${sec / 60}:${"%02d".format(sec % 60)}")
            if (sec > 40 * 60) throw IllegalStateException("Сборка идёт слишком долго")
            delay(5000)
        }

        if (conclusion == "success") {
            report("Скачиваю готовый мод…")
            val arts = JSONObject(
                ok(gh("GET", "$API/repos/$owner/$repo/actions/runs/$runId/artifacts", token), "Список файлов").body
            ).getJSONArray("artifacts")
            if (arts.length() == 0) throw IllegalStateException("Сборка прошла, но файла мода нет")
            val artId = arts.getJSONObject(0).getLong("id")
            val zip = download("$API/repos/$owner/$repo/actions/artifacts/$artId/zip", token)
            val jar = extractJar(zip) ?: throw IllegalStateException("В архиве нет .jar")
            val where = saveJar(context, jar.first, jar.second)
            BuildResult(true, "Мод сохранён: $where", runUrl, emptyList())
        } else {
            report("Читаю ошибки…")
            val errors = try {
                extractErrors(download("$API/repos/$owner/$repo/actions/runs/$runId/logs", token))
            } catch (e: Exception) {
                emptyList()
            }
            BuildResult(false, "Сборка завершилась с ошибкой ($conclusion).", runUrl, errors)
        }
    } catch (e: Exception) {
        BuildResult(false, e.message ?: "Неизвестная ошибка", runUrl, emptyList())
    }
}
