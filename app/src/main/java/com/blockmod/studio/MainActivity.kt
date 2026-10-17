package com.blockmod.studio

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
        val theme = ThemeState(getSharedPreferences("settings", Context.MODE_PRIVATE))
        setContent { AppTheme(theme) { AppRoot(theme) } }
    }
}

@Composable
fun AppRoot(theme: ThemeState) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString("token", null)) }

    if (token == null) {
        LoginScreen(onLoggedIn = { tk ->
            prefs.edit().putString("token", tk).apply()
            token = tk
        })
    } else {
        MainScreen(theme = theme, onLogout = {
            prefs.edit().remove("token").apply()
            token = null
        })
    }
}

@Composable
fun MainScreen(theme: ThemeState, onLogout: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var mods by remember { mutableStateOf(ModStore.load(context)) }

    val editing = mods.firstOrNull { it.id == editingId }

    if (creating) {
        BackHandler { creating = false }
        CreateModScreen(
            onBack = { creating = false },
            onSave = { mod ->
                val updated = mods + mod
                mods = updated
                ModStore.save(context, updated)
                creating = false
                editingId = mod.id
            }
        )
    } else if (editing != null) {
        EditorScreen(
            mod = editing,
            onChange = { m ->
                val updated = mods.map { if (it.id == m.id) m else it }
                mods = updated
                ModStore.save(context, updated)
            },
            onBack = { editingId = null }
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
                        onOpen = { editingId = it.id },
                        onDelete = { mod ->
                            mod.avatarPath?.let { File(it).delete() }
                            val updated = mods.filter { it.id != mod.id }
                            mods = updated
                            ModStore.save(context, updated)
                        }
                    )
                    1 -> ModsScreen()
                    else -> SettingsScreen(theme = theme, onLogout = onLogout)
                }
            }
        }
    }
}
