package com.blockmod.studio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsScreen() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ModrinthMod>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        failed = false
        delay(400)
        val r = searchModrinth(query.trim())
        if (r == null) failed = true else results = r
        loading = false
    }

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
        item { Text("Modrinth", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск на Modrinth") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (failed) {
            item {
                Text(
                    "Не удалось загрузить. Проверь интернет.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            items(results, key = { it.slug }) { m ->
                Card(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://modrinth.com/mod/${m.slug}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(m.title) },
                        supportingContent = {
                            Text(m.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { ModAvatar(null, m.title, 48.dp) },
                        trailingContent = { Text("⬇ " + formatCount(m.downloads)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(theme: ThemeState, onLogout: () -> Unit) {
    val modeNames = listOf("Как в системе", "Светлая", "Тёмная")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text("Тема", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modeNames.forEachIndexed { i, n ->
                FilterChip(
                    selected = theme.mode == i,
                    onClick = { theme.updateMode(i) },
                    label = { Text(n) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Цвет акцента", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            accents.forEachIndexed { i, a ->
                val selected = theme.accent == i
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(a.light)
                        .then(
                            if (selected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { theme.updateAccent(i) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    } else if (i == 0) {
                        Text("A", color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            accents[theme.accent.coerceIn(0, accents.lastIndex)].name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        Text("Аккаунт GitHub", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Подключён ✅", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onLogout) { Text("Выйти из GitHub") }
    }
}
