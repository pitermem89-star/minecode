package com.blockmod.studio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Text("Аккаунт GitHub", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Подключён ✅", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onLogout) { Text("Выйти из GitHub") }
        Spacer(Modifier.height(32.dp))
        Text("Версия приложения 0.2", style = MaterialTheme.typography.bodySmall)
    }
}
