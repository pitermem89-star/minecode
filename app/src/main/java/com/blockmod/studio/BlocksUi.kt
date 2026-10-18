package com.blockmod.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// kind: 0 событие, 1 объявление, 2 управление, 3 действие
class BlockSpec(
    val type: String,
    val template: String,
    val kind: Int,
    val hintA: String = "",
    val hintB: String = "",
    val defA: String = "",
    val defB: String = "",
    val hasBody: Boolean = false
) {
    val tokens: List<String> = Regex("\\{[ab]\\}|[^{]+").findAll(template)
        .map { it.value.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    val topLevel: Boolean get() = kind < 2
    val color: Color
        get() = when (kind) {
            0 -> Color(0xFFF9A825)
            1 -> Color(0xFF43A047)
            2 -> Color(0xFFEF6C00)
            else -> Color(0xFF1E88E5)
        }
    val textColor: Color get() = if (kind == 0) Color.Black else Color.White
}

val blockSpecs = listOf(
    BlockSpec("on_join", "Когда игрок заходит в мир", 0, hasBody = true),
    BlockSpec("on_command", "Когда игрок пишет команду /{a}", 0, hintA = "имя", defA = "hello", hasBody = true),
    BlockSpec("item", "Новый предмет id {a} название {b}", 1, hintA = "id", hintB = "название", defA = "ruby", defB = "Рубин"),
    BlockSpec("block", "Новый блок id {a} название {b}", 1, hintA = "id", hintB = "название", defA = "ruby_block", defB = "Рубиновый блок"),
    BlockSpec("repeat", "Повторить {a} раз", 2, hintA = "число", defA = "3", hasBody = true),
    BlockSpec("if_sneak", "Если игрок крадётся", 2, hasBody = true),
    BlockSpec("say", "Написать игроку {a}", 3, hintA = "текст", defA = "Привет!"),
    BlockSpec("give", "Выдать предмет {a} × {b}", 3, hintA = "minecraft:diamond", hintB = "1", defA = "minecraft:diamond", defB = "1"),
    BlockSpec("heal", "Вылечить игрока", 3)
)

fun specOf(type: String): BlockSpec? = blockSpecs.firstOrNull { it.type == type }

fun <T> List<T>.replaceAt(i: Int, v: T): List<T> =
    mapIndexed { idx, x -> if (idx == i) v else x }

fun <T> List<T>.swap(i: Int, j: Int): List<T> {
    val m = toMutableList()
    val t = m[i]
    m[i] = m[j]
    m[j] = t
    return m
}

@Composable
fun BlocksTab(blocks: List<ModBlock>, onChange: (List<ModBlock>) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Скрипт начинается с жёлтого блока-события. Под ним ставь команды.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        BlockList(blocks, true, onChange)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun BlockList(blocks: List<ModBlock>, top: Boolean, onChange: (List<ModBlock>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, b ->
            val spec = specOf(b.type) ?: return@forEachIndexed
            val up: (() -> Unit)? =
                if (index > 0) ({ onChange(blocks.swap(index, index - 1)) }) else null
            val down: (() -> Unit)? =
                if (index < blocks.lastIndex) ({ onChange(blocks.swap(index, index + 1)) }) else null

            BrickView(
                spec = spec,
                block = b,
                onBlock = { nb -> onChange(blocks.replaceAt(index, nb)) },
                onUp = up,
                onDown = down,
                onDelete = { onChange(blocks.filterIndexed { i, _ -> i != index }) }
            )
            if (spec.hasBody) {
                val barColor = spec.color
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .drawBehind {
                            drawRect(
                                barColor,
                                topLeft = Offset.Zero,
                                size = Size(5.dp.toPx(), size.height)
                            )
                        }
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BlockList(b.kids, false) { nk ->
                        onChange(blocks.replaceAt(index, b.copy(kids = nk)))
                    }
                }
            }
            if (top) Spacer(Modifier.height(8.dp))
        }
        AddBlockButton(top) { onChange(blocks + it) }
    }
}

@Composable
fun AddBlockButton(top: Boolean, onAdd: (ModBlock) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (top) "Добавить скрипт" else "Добавить команду")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            blockSpecs.filter { it.topLevel == top }.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.template.replace("{a}", "…").replace("{b}", "…")) },
                    onClick = {
                        onAdd(ModBlock(s.type, s.defA, s.defB))
                        menu = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrickView(
    spec: BlockSpec,
    block: ModBlock,
    onBlock: (ModBlock) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = spec.color,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                spec.tokens.forEach { t ->
                    when (t) {
                        "{a}" -> InlineField(block.a, spec.hintA) { onBlock(block.copy(a = it)) }
                        "{b}" -> InlineField(block.b, spec.hintB) { onBlock(block.copy(b = it)) }
                        else -> Text(
                            t,
                            color = spec.textColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = spec.textColor)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Вверх") },
                        enabled = onUp != null,
                        onClick = { menu = false; onUp?.invoke() }
                    )
                    DropdownMenuItem(
                        text = { Text("Вниз") },
                        enabled = onDown != null,
                        onClick = { menu = false; onDown?.invoke() }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun InlineField(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
        cursorBrush = SolidColor(Color.Black),
        modifier = Modifier.widthIn(min = 56.dp, max = 220.dp),
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (value.isEmpty()) {
                    Text(hint, color = Color.Gray, fontSize = 14.sp)
                }
                inner()
            }
        }
    )
}
