package com.minecode.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

const val CLIENT_ID = "Ov23liJ1l1EHkvOWoDNU"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }
}

// ---------- Тема ----------

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

// ---------- Данные ----------

data class ModProject(
    val id: String,
    val name: String,
    val mcVersion: String,
    val description: String,
    val avatarPath: String?
)

data class ExternalMod(val name: String, val description: String)

val otherMods = listOf(
    ExternalMod("Sodium", "Ускоряет графику и повышает FPS (Fabric)")
)

val mcVersions = listOf("1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5")

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
                    description = o.optString("description", ""),
                    avatarPath = if (o.isNull("avatarPath")) null else o.getString("avatarPath")
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
            o.put("description", m.description)
            o.put("avatarPath", m.avatarPath ?: JSONObject.NULL)
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

// ---------- GitHub вход ----------

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
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("token", null)) }

    if (token == null) {
        LoginScreen(onLoggedIn = { tk ->
            prefs.edit().putString("token", tk).apply()
            token = tk
        })
    } else {
        MainScreen(onLogout = {
            prefs.edit().remove("token").apply()
            token = null
        })
    }
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

// ---------- Главный экран с меню ----------

@Composable
fun MainScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var mods by remember { mutableStateOf(ModStore.load(context)) }

    if (creating) {
        BackHandler { creating = false }
        CreateModScreen(
            onBack = { creating = false },
            onSave = { mod ->
                val updated = mods + mod
                mods = updated
                ModStore.save(context, updated)
                creating = false
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Главная") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("Моды") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Настройки") }
                    )
                }
            },
            floatingActionButton = {
                if (tab == 0) {
                    FloatingActionButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Создать мод")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(
                        mods = mods,
                        onDelete = { mod ->
                            mod.avatarPath?.let { File(it).delete() }
                            val updated = mods.filter { it.id != mod.id }
                            mods = updated
                            ModStore.save(context, updated)
                        }
                    )
                    1 -> ModsScreen()
                    else -> SettingsScreen(onLogout = onLogout)
                }
            }
        }
    }
}

// ---------- Аватарка ----------

@Composable
fun ModAvatar(path: String?, name: String, size: Dp) {
    val bitmap = remember(path) {
        path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.take(1).uppercase().ifEmpty { "+" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ---------- Вкладка «Главная» ----------

@Composable
fun HomeScreen(mods: List<ModProject>, onDelete: (ModProject) -> Unit) {
    var toDelete by remember { mutableStateOf<ModProject?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Мои моды",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        if (mods.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Пока нет модов.\nНажми + внизу справа, чтобы создать первый.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(mods, key = { it.id }) { mod ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModAvatar(mod.avatarPath, mod.name, 56.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mod.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Minecraft ${mod.mcVersion}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (mod.description.isNotBlank()) {
                                    Text(
                                        mod.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { toDelete = mod }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить мод?") },
            text = { Text("«${mod.name}» будет удалён с этого устройства.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(mod)
                    toDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Отмена") }
            }
        )
    }
}

// ---------- Вкладка «Моды» ----------

@Composable
fun ModsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Моды", style = MaterialTheme.typography.headlineMedium) }
        item { Text("От сообщества", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Скоро здесь появятся моды других пользователей приложения.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Text("Другие моды", style = MaterialTheme.typography.titleMedium) }
        items(otherMods) { m ->
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(m.name) },
                    supportingContent = { Text(m.description) },
                    leadingContent = { ModAvatar(null, m.name, 48.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

// ---------- Вкладка «Настройки» ----------

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Text("Аккаунт GitHub", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.
