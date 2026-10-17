package com.blockmod.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class BlockKind(
    val type: String,
    val title: String,
    val labelA: String,
    val labelB: String?
)

val blockKinds = listOf(
    BlockKind("item", "Новый предмет", "ID предмета (латиницей)", "Название"),
    BlockKind("block", "Новый блок", "ID блока (латиницей)", "Название"),
    BlockKind("command", "Команда в чате", "Имя команды (без /)", "Что ответить в чате"),
    BlockKind("join", "Сообщение при входе в мир", "Текст сообщения", null)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(mod: ModProject, onChange: (ModProject) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var blocks by remember { mutableStateOf(parseBlocks(mod.blocks)) }
    var code by remember { mutableStateOf(mod.code) }

    fun snapshot() = mod.copy(blocks = blocksToJson(blocks), code = code)

    LaunchedEffect(blocks, code) {
        delay(700)
        onChange(snapshot())
    }

    BackHandler {
        onChange(snapshot())
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(mod.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${mod.loader} · Minecraft ${mod.mcVersion}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onChange(snapshot())
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Блоки") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Код") })
            }
            if (tab == 0) {
                BlocksTab(blocks) { blocks = it }
            } else {
                CodeTab(mod, blocks, code) { code = it }
            }
        }
    }
}

@Composable
fun BlocksTab(blocks: List<ModBlock>, onChange: (List<ModBlock>) -> Unit) {
    var menu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (blocks.isEmpty()) {
            item {
                Text(
                    "Блоков пока нет. Нажми «Добавить блок».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(blocks) { index, b ->
            val kind = blockKinds.firstOrNull { it.type == b.type } ?: return@itemsIndexed
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            kind.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            onChange(blocks.filterIndexed { i, _ -> i != index })
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить блок")
                        }
                    }
                    OutlinedTextField(
                        value = b.a,
                        onValueChange = { v ->
                            onChange(blocks.mapIndexed { i, x -> if (i == index) x.copy(a = v) else x })
                        },
                        label = { Text(kind.labelA) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (kind.labelB != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = b.b,
                            onValueChange = { v ->
                                onChange(blocks.mapIndexed { i, x -> if (i == index) x.copy(b = v) else x })
                            },
                            label = { Text(kind.labelB) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item {
            Box {
                Button(onClick = { menu = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить блок")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    blockKinds.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k.title) },
                            onClick = {
                                onChange(blocks + ModBlock(k.type, "", ""))
                                menu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeTab(mod: ModProject, blocks: List<ModBlock>, code: String, onCode: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (code.isBlank()) onCode(generateJava(mod, blocks))
    }

    val kw = MaterialTheme.colorScheme.primary
    val str = MaterialTheme.colorScheme.tertiary
    val com = MaterialTheme.colorScheme.outline
    val highlight = remember(kw, str, com) { JavaHighlight(kw, str, com) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedButton(onClick = {
            if (code.isBlank()) onCode(generateJava(mod, blocks)) else confirm = true
        }) { Text("Обновить код из блоков") }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = onCode,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            visualTransformation = highlight,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Заменить код?") },
            text = { Text("Код будет создан заново из блоков. Твои ручные правки пропадут.") },
            confirmButton = {
                TextButton(onClick = {
                    onCode(generateJava(mod, blocks))
                    confirm = false
                }) { Text("Заменить") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Отмена") }
            }
        )
    }
}

private val keywordRegex = Regex(
    "\\b(public|private|protected|static|final|class|interface|extends|implements|new|return|void|int|boolean|if|else|for|while|import|package|null|true|false|this|super|try|catch|throw|throws)\\b"
)
private val stringRegex = Regex("\"(\\\\.|[^\"\\\\\\n])*\"")
private val commentRegex = Regex("//[^\\n]*")

class JavaHighlight(
    private val kw: Color,
    private val str: Color,
    private val com: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val styled = buildAnnotatedString {
            append(s)
            keywordRegex.findAll(s).forEach {
                addStyle(SpanStyle(color = kw), it.range.first, it.range.last + 1)
            }
            stringRegex.findAll(s).forEach {
                addStyle(SpanStyle(color = str), it.range.first, it.range.last + 1)
            }
            commentRegex.findAll(s).forEach {
                addStyle(SpanStyle(color = com), it.range.first, it.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
