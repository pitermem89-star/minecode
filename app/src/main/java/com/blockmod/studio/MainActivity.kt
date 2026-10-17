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
}
