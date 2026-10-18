package com.blockmod.studio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

class BuildUi {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")
    var result by mutableStateOf<BuildResult?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(mod: ModProject, onChange: (ModProject) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buildUi = remember { BuildUi() }
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

    fun startBuild() {
        val token = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .getString("token", null)
        if (token == null) {
            buildUi.result = BuildResult(false, "Нет входа в GitHub. Выйди и войди заново в настройках.", null, emptyList())
            return
        }
        val snap = snapshot()
        onChange(snap)
        buildUi.running = true
        buildUi.result = null
        buildUi.status = "Начинаю…"
        scope.launch {
            val r = buildMod(context, token, snap, blocks) { buildUi.status = it }
            buildUi.result = r
            buildUi.running = false
        }
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
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Сборка") })
            }
            when (tab) {
                0 -> BlocksTab(blocks) { blocks = it }
                1 -> CodeTab(mod, blocks, code) { code = it }
                else -> BuildTab(mod, buildUi) { startBuild() }
            }
        }
    }
}

@Composable
fun CodeTab(mod: ModProject, blocks: List<ModBlock>, code: String, onCode: (String) -> Unit) {
    val manual = code.isNotBlank()
    val shown = if (manual) code else generateJava(mod, blocks)

    val kw = MaterialTheme.colorScheme.primary
    val str = MaterialTheme.colorScheme.tertiary
    val com = MaterialTheme.colorScheme.outline
    val highlight = remember(kw, str, com) { JavaHighlight(kw, str, com) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            if (manual) "Код изменён вручную: блоки больше на него не влияют."
            else "Код создаётся из блоков сам. Если изменишь его здесь, блоки перестанут на него влиять.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (manual) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onCode("") }) { Text("Вернуть код из блоков") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = shown,
            onValueChange = onCode,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            visualTransformation = highlight,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
fun BuildTab(mod: ModProject, ui: BuildUi, onStart: () -> Unit) {
    val context = LocalContext.current
    val supported = buildSupported(mod)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Сборка мода", style = MaterialTheme.typography.titleLarge)
        Text(
            "Мод отправится в твой приватный репозиторий на GitHub, там соберётся .jar, " +
                "и приложение скачает его в «Загрузки/BlockModStudio».",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!supported) {
            Text(
                "Для ${mod.loader} ${mod.mcVersion} сборка пока не поддерживается. " +
                    "Сейчас работает: Fabric 1.21.1, Fabric 1.20.1 и Forge 1.20.1.",
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(onClick = onStart, enabled = supported && !ui.running) {
            Text(if (ui.running) "Идёт сборка…" else "Собрать мод")
        }
        if (ui.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(ui.status)
        }
        ui.result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (r.ok) "Готово ✅" else "Сборка не удалась ❌",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(r.message)
                    if (r.errors.isNotEmpty()) {
                        Text("Ошибки из лога (можно выделить и скопировать):",
                            style = MaterialTheme.typography.labelMedium)
                        SelectionContainer {
                            Text(
                                r.errors.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (r.runUrl != null) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.runUrl)))
                        }) { Text("Открыть на GitHub") }
                    }
                }
            }
        }
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
