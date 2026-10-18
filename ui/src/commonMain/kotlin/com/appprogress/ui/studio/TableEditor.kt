package com.appprogress.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appprogress.studio.ColumnType
import com.appprogress.studio.StudioDoc
import com.appprogress.studio.StudioJson
import com.appprogress.studio.StudioRepository
import com.appprogress.studio.TableData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val CELL_WIDTH = 170.dp
private val ROW_HEADER = 44.dp

@Composable
fun TableEditor(doc: StudioDoc, repo: StudioRepository, scope: CoroutineScope, onBuildChart: (String) -> Unit) {
    var table by remember { mutableStateOf(StudioJson.table(doc)) }
    val status = rememberAutosave(table) { scope.launch { repo.saveData(doc.id, StudioJson.encode(it)) } }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var info by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(doc.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(info ?: status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { table = table.addRow() }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text(" Строка") }
            OutlinedButton(onClick = { table = table.addColumn("Колонка ${table.columns.size + 1}") }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text(" Колонка") }
            TextButton(onClick = { clipboard.setText(AnnotatedString(table.toCsv())); info = "Скопировано как CSV — можно вставить в Excel" }) { Text("Копировать CSV") }
            TextButton(onClick = {
                val text = clipboard.getText()?.text
                if (text.isNullOrBlank()) info = "В буфере обмена нет текста" else { table = TableData.fromCsv(text); info = "Вставлено из буфера: ${table.rows.size} строк" }
            }) { Text("Вставить CSV") }
            TextButton(onClick = { onBuildChart(doc.id) }, enabled = table.columns.any { it.type == ColumnType.Number }) { Text("Построить график") }
        }

        Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.weight(1f)) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                LazyColumn {
                    item { HeaderRow(table) { table = it } }
                    itemsIndexed(table.rows) { r, _ ->
                        Row {
                            RowHeader(r) { table = table.removeRow(r) }
                            table.columns.forEachIndexed { c, column ->
                                Cell(
                                    value = table.cell(r, c),
                                    numeric = column.type == ColumnType.Number,
                                    invalid = column.type == ColumnType.Number && table.cell(r, c).isNotBlank() && table.number(r, c) == null,
                                    onChange = { table = table.withCell(r, c, it) },
                                )
                            }
                        }
                    }
                    item { FooterRow(table) }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(table: TableData, onChange: (TableData) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.background(colors.surfaceVariant)) {
        Box(Modifier.width(ROW_HEADER).height(44.dp).border(0.5.dp, colors.outlineVariant))
        table.columns.forEachIndexed { c, column ->
            Row(
                Modifier.width(CELL_WIDTH).height(44.dp).border(0.5.dp, colors.outlineVariant).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = column.name,
                    onValueChange = { name -> onChange(table.updateColumn(c) { it.copy(name = name) }) },
                    singleLine = true,
                    textStyle = TextStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f),
                )
                // Переключатель типа: «Aa» — текст, «123» — числа (для сумм и графиков).
                TextButton(
                    onClick = {
                        onChange(table.updateColumn(c) { it.copy(type = if (it.type == ColumnType.Text) ColumnType.Number else ColumnType.Text) })
                    },
                    modifier = Modifier.width(48.dp),
                ) { Text(if (column.type == ColumnType.Number) "123" else "Aa", style = MaterialTheme.typography.labelSmall) }
                IconButton(onClick = { onChange(table.removeColumn(c)) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, "Удалить колонку", Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RowHeader(index: Int, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.width(ROW_HEADER).height(36.dp).background(colors.surfaceVariant.copy(alpha = 0.5f)).border(0.5.dp, colors.outlineVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Filled.Close, "Удалить строку", Modifier.size(12.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun Cell(value: String, numeric: Boolean, invalid: Boolean, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.width(CELL_WIDTH).height(36.dp)
            .background(if (invalid) colors.error.copy(alpha = 0.08f) else colors.surface)
            .border(0.5.dp, colors.outlineVariant)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.onSurface, textAlign = if (numeric) TextAlign.End else TextAlign.Start),
            cursorBrush = SolidColor(colors.primary),
        )
    }
}

@Composable
private fun FooterRow(table: TableData) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.background(colors.surfaceVariant.copy(alpha = 0.5f))) {
        Box(Modifier.width(ROW_HEADER).height(40.dp).border(0.5.dp, colors.outlineVariant), contentAlignment = Alignment.Center) {
            Text("Σ", fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
        }
        table.columns.forEachIndexed { c, column ->
            val stats = if (column.type == ColumnType.Number) table.stats(c) else null
            Box(Modifier.width(CELL_WIDTH).height(40.dp).border(0.5.dp, colors.outlineVariant).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                if (stats != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatNumber(stats.sum), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text("ср. ${formatNumber(stats.average)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 1234567.891 → «1 234 567,89» */
fun formatNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val negative = rounded < 0
    val abs = kotlin.math.abs(rounded)
    val whole = abs.toLong()
    val frac = kotlin.math.round((abs - whole) * 100).toInt()
    val grouped = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return (if (negative) "-" else "") + grouped + if (frac != 0) "," + frac.toString().padStart(2, '0').trimEnd('0') else ""
}
