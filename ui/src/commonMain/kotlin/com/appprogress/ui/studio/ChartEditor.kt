package com.appprogress.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appprogress.studio.ChartType
import com.appprogress.studio.ColumnType
import com.appprogress.studio.DocKind
import com.appprogress.studio.StudioDoc
import com.appprogress.studio.StudioJson
import com.appprogress.studio.StudioRepository
import com.appprogress.studio.extract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartEditor(doc: StudioDoc, repo: StudioRepository, scope: CoroutineScope) {
    var spec by remember { mutableStateOf(StudioJson.chart(doc)) }
    val status = rememberAutosave(spec) { scope.launch { repo.saveData(doc.id, StudioJson.encode(it)) } }
    val tables by remember { repo.observe(DocKind.Table) }.collectAsState(initial = emptyList())
    // График подписан на свою таблицу: правки в таблице сразу видны на графике.
    val tableDoc by remember(spec.tableId) { spec.tableId?.let(repo::observeDoc) ?: flowOf(null) }.collectAsState(initial = null)
    val table = StudioJson.table(tableDoc)
    val data = spec.extract(table)
    val colors = MaterialTheme.colorScheme

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(doc.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)

            Caption("Таблица с данными")
            if (tables.isEmpty()) Text("Сначала создайте таблицу во вкладке «Таблицы»", color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tables.forEach { t ->
                    FilterChip(
                        selected = t.id == spec.tableId,
                        onClick = { spec = spec.copy(tableId = t.id, labelColumnId = null, valueColumnIds = emptyList()) },
                        label = { Text(t.title) },
                    )
                }
            }

            Caption("Вид")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartType.entries.forEach { t -> FilterChip(selected = t == spec.type, onClick = { spec = spec.copy(type = t) }, label = { Text(t.title, maxLines = 1) }) }
            }

            if (table.columns.isNotEmpty()) {
                Caption("Подписи")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    table.columns.filter { it.type == ColumnType.Text }.forEach { c ->
                        FilterChip(selected = c.id == spec.labelColumnId, onClick = { spec = spec.copy(labelColumnId = c.id) }, label = { Text(c.name) })
                    }
                }
                Caption(if (spec.type == ChartType.Pie) "Значения (берётся первая выбранная)" else "Значения")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    table.columns.filter { it.type == ColumnType.Number }.forEach { c ->
                        val on = c.id in spec.valueColumnIds
                        FilterChip(
                            selected = on,
                            onClick = { spec = spec.copy(valueColumnIds = if (on) spec.valueColumnIds - c.id else spec.valueColumnIds + c.id) },
                            label = { Text(c.name) },
                        )
                    }
                }
                if (spec.valueColumnIds.isEmpty()) {
                    Text("Не выбрано — показываются все числовые колонки", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            if (data.isEmpty) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Выберите таблицу с числовой колонкой", color = colors.onSurfaceVariant)
                }
            } else {
                ChartCanvas(data, spec.type, Modifier.fillMaxSize().padding(24.dp))
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}
