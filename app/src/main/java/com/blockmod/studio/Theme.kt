package com.blockmod.studio

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

class AccentOption(
    val name: String,
    val light: Color,
    val lightContainer: Color,
    val dark: Color,
    val darkContainer: Color
)

val accents = listOf(
    AccentOption("Системный (цвета обоев на Android 12+)", Color(0xFF9E9E9E), Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF424242)),
    AccentOption("Зелёный", Color(0xFF2E7D32), Color(0xFFC8E6C9), Color(0xFF81C784), Color(0xFF1B5E20)),
    AccentOption("Синий", Color(0xFF1565C0), Color(0xFFBBDEFB), Color(0xFF90CAF9), Color(0xFF0D47A1)),
    AccentOption("Красный", Color(0xFFC62828), Color(0xFFFFCDD2), Color(0xFFEF9A9A), Color(0xFFB71C1C)),
    AccentOption("Фиолетовый", Color(0xFF6A1B9A), Color(0xFFE1BEE7), Color(0xFFCE93D8), Color(0xFF4A148C)),
    AccentOption("Оранжевый", Color(0xFFE65100), Color(0xFFFFE0B2), Color(0xFFFFB74D), Color(0xFFBF360C)),
    AccentOption("Бирюзовый", Color(0xFF00695C), Color(0xFFB2DFDB), Color(0xFF4DB6AC), Color(0xFF004D40))
)

class ThemeState(private val prefs: SharedPreferences) {
    var mode by mutableIntStateOf(prefs.getInt("mode", 0))
        private set
    var accent by mutableIntStateOf(prefs.getInt("accent", 0))
        private set

    fun updateMode(v: Int) {
        mode = v
        prefs.edit().putInt("mode", v).apply()
    }

    fun updateAccent(v: Int) {
        accent = v
        prefs.edit().putInt("accent", v).apply()
    }
}

@Composable
fun AppTheme(state: ThemeState, content: @Composable () -> Unit) {
    val dark = when (state.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val index = state.accent.coerceIn(0, accents.lastIndex)
    val a = accents[index]
    val colors = if (index == 0) {
        if (Build.VERSION.SDK_INT >= 31) {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
            if (dark) darkColorScheme() else lightColorScheme()
        }
    } else if (dark) {
        darkColorScheme(
            primary = a.dark,
            onPrimary = Color.Black,
            primaryContainer = a.darkContainer,
            onPrimaryContainer = Color.White,
            secondary = a.dark,
            onSecondary = Color.Black,
            secondaryContainer = a.darkContainer,
            onSecondaryContainer = Color.White
        )
    } else {
        lightColorScheme(
            primary = a.light,
            onPrimary = Color.White,
            primaryContainer = a.lightContainer,
            onPrimaryContainer = Color.Black,
            secondary = a.light,
            onSecondary = Color.White,
            secondaryContainer = a.lightContainer,
            onSecondaryContainer = Color.Black
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
