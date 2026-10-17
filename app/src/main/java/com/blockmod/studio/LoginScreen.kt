package com.blockmod.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val CLIENT_ID = "Ov23liJ1l1EHkvOWoDNU"

fun post(url: String, params: Map<String, String>): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Accept", "application/json")
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    conn.doOutput = true
    val body = params.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
    conn.outputStream.use { it.write(body.toByteArray()) }
    val stream = if (conn.responseCode < 400) conn.inputStream else conn.errorStream
    val text = stream.bufferedReader().use { it.readText() }
    return JSONObject(text)
}

@Composable
fun LoginScreen(onLoggedIn: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun startLogin() {
        scope.launch {
            loading = true
            error = null
            try {
                val r = withContext(Dispatchers.IO) {
                    post(
                        "https://github.com/login/device/code",
                        mapOf("client_id" to CLIENT_ID, "scope" to "repo workflow")
                    )
                }
                if (!r.has("device_code")) {
                    error = r.optString("error_description", "Ошибка GitHub")
                    loading = false
                    return@launch
                }
                val deviceCode = r.getString("device_code")
                val code = r.getString("user_code")
                val uri = r.getString("verification_uri")
                var interval = r.getInt("interval")
                val deadline = System.currentTimeMillis() + r.getInt("expires_in") * 1000L
                userCode = code

                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("code", code))
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

                while (System.currentTimeMillis() < deadline) {
                    delay(interval * 1000L)
                    val t = try {
                        withContext(Dispatchers.IO) {
                            post(
                                "https://github.com/login/oauth/access_token",
                                mapOf(
                                    "client_id" to CLIENT_ID,
                                    "device_code" to deviceCode,
                                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (t == null) continue
                    if (t.has("access_token")) {
                        userCode = null
                        onLoggedIn(t.getString("access_token"))
                        break
                    }
                    when (t.optString("error")) {
                        "authorization_pending" -> {}
                        "slow_down" -> interval += 5
                        else -> {
                            error = t.optString("error_description", "Вход не удался")
                            userCode = null
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                error = "Ошибка: " + (e.message ?: "нет интернета?")
            }
            loading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BlockMod Studio", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Делай моды для Minecraft блоками и кодом",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            if (userCode != null) {
                Text("Введи этот код на странице GitHub:")
                Spacer(Modifier.height(8.dp))
                Text(userCode!!, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("(код уже скопирован)")
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            } else {
                Button(onClick = { startLogin() }, enabled = !loading) {
                    Text("Войти через GitHub")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
