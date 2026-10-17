package com.blockmod.studio

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }
}

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
}                      }
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
