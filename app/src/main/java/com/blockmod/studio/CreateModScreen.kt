package com.blockmod.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateModScreen(onBack: () -> Unit, onSave: (ModProject) -> Unit) {
    val context = LocalContext.current
    val id = remember { UUID.randomUUID().toString() }

    var versions by remember { mutableStateOf(loadCachedVersions(context)) }
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(versions.first()) }
    var loader by remember { mutableStateOf(loaders.first()) }
    var description by remember { mutableStateOf("") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val fresh = fetchReleaseVersions()
        if (fresh != null) {
            versions = fresh
            saveCachedVersions(context, fresh)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val newPath = saveAvatar(context, uri, id)
            if (newPath != null) {
                avatarPath?.let { File(it).delete() }
                avatarPath = newPath
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый мод") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
            ) {
                ModAvatar(avatarPath, name, 112.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Нажми, чтобы выбрать аватарку",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название мода") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Загрузчик модов",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                loaders.forEach { l ->
                    FilterChip(
                        selected = loader == l,
                        onClick = { loader = l },
                        label = { Text(l) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = version,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Версия Minecraft") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    versions.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v) },
                            onClick = {
                                version = v
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание (необязательно)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    onSave(
                        ModProject(
                            id = id,
                            name = name.trim(),
                            mcVersion = version,
                            loader = loader,
                            description = description.trim(),
                            avatarPath = avatarPath,
                            blocks = "[]",
                            code = ""
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Создать мод") }
        }
    }
}
