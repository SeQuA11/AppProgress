package com.appprogress.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.studio.ChartSpec
import com.appprogress.studio.DiagramData
import com.appprogress.studio.DocKind
import com.appprogress.studio.StudioDoc
import com.appprogress.studio.StudioRepository
import com.appprogress.studio.TableData
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.ConfirmDialog
import com.appprogress.ui.common.TextInputDialog
import com.appprogress.ui.common.formatDateTime
import kotlinx.coroutines.launch

/** Шаблон нового документа: название в меню и как создать. */
private class Template(val title: String, val create: suspend StudioRepository.() -> String)

private fun templates(kind: DocKind): List<Template> = when (kind) {
    DocKind.Table -> listOf(
        Template("Пустая таблица") { createTable(DocKind.Table.newTitle) },
        Template("Бюджет (пример)") { createTable("Бюджет", TableData.budgetSample()) },
    )
    DocKind.Chart -> listOf(Template("Новый график") { createChart(DocKind.Chart.newTitle, ChartSpec()) })
    DocKind.Diagram -> listOf(
        Template("Пустой холст") { createDiagram(DocKind.Diagram.newTitle) },
        Template("Блок-схема") { createDiagram("Блок-схема", DiagramData.flowchartTemplate()) },
        Template("Карта мыслей") { createDiagram("Карта мыслей", DiagramData.mindMapTemplate()) },
    )
}

@Composable
fun StudioScreen(container: AppContainer, initialKind: DocKind = DocKind.Table, initialTime: Boolean = false) {
    val repo = remember { StudioRepository(container.db, container.io) }
    val scope = container.scope
    var kind by remember { mutableStateOf(initialKind) }
    // Выбранный документ запоминаем для каждой вкладки отдельно.
    val selectedByKind = remember { mutableStateMapOf<DocKind, String?>() }
    val docs by remember(kind) { repo.observe(kind) }.collectAsState(initial = null)
    var renaming by remember { mutableStateOf<StudioDoc?>(null) }
    var deleting by remember { mutableStateOf<StudioDoc?>(null) }
    var showTime by remember { mutableStateOf(initialTime) }

    LaunchedEffect(docs) {
        val list = docs ?: return@LaunchedEffect
        if (list.none { it.id == selectedByKind[kind] }) selectedByKind[kind] = list.firstOrNull()?.id
    }
    val selected = docs?.firstOrNull { it.id == selectedByKind[kind] }

    val openChartFor: (String) -> Unit = { tableId ->
        scope.launch {
            val title = repo.get(tableId)?.title?.let { "График: $it" } ?: DocKind.Chart.newTitle
            val chartId = repo.createChart(title, ChartSpec(tableId = tableId))
            selectedByKind[DocKind.Chart] = chartId
            kind = DocKind.Chart
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(280.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Студия", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            KindTabs(kind.takeUnless { showTime }) { kind = it; showTime = false }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (showTime) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                    .clickable { showTime = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) { Text("Учёт времени", fontWeight = if (showTime) FontWeight.SemiBold else FontWeight.Normal) }
            if (!showTime) NewDocButton(kind) { template ->
                scope.launch { selectedByKind[kind] = template.create(repo) }
            }
            if (!showTime) LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(docs.orEmpty(), key = { it.id }) { doc ->
                    DocRow(
                        doc = doc,
                        selected = doc.id == selected?.id,
                        onClick = { selectedByKind[kind] = doc.id },
                        onRename = { renaming = doc },
                        onDelete = { deleting = doc },
                    )
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            when {
                showTime -> TimeStats(container)
                selected == null -> EmptyHint(kind)
                // key: при смене документа редактор создаётся заново со своим состоянием.
                else -> key(selected.id) {
                    when (selected.kind) {
                        DocKind.Table -> TableEditor(selected, repo, scope, onBuildChart = openChartFor)
                        DocKind.Chart -> ChartEditor(selected, repo, scope)
                        DocKind.Diagram -> DiagramEditor(selected, repo, scope)
                    }
                }
            }
        }
    }

    renaming?.let { doc ->
        TextInputDialog(
            title = "Переименовать", initial = doc.title,
            onConfirm = { renaming = null; scope.launch { repo.rename(doc.id, it) } },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { doc ->
        ConfirmDialog(
            title = "Удалить «${doc.title}»?", text = "Документ будет удалён.",
            onConfirm = { scope.launch { repo.delete(doc.id) } },
            onDismiss = { deleting = null },
        )
    }
}

/** Переключатель вкладок в одну строку: три равные части. */
@Composable
private fun KindTabs(current: DocKind?, onSelect: (DocKind) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(3.dp)) {
        DocKind.entries.forEach { k ->
            val selected = k == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.surface else colors.surfaceVariant)
                    .clickable { onSelect(k) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    k.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NewDocButton(kind: DocKind, onCreate: (Template) -> Unit) {
    val options = templates(kind)
    var menu by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { if (options.size == 1) onCreate(options.single()) else menu = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text(kind.newTitle, Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { t -> DropdownMenuItem(text = { Text(t.title) }, onClick = { menu = false; onCreate(t) }) }
        }
    }
}

@Composable
private fun DocRow(doc: StudioDoc, selected: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else colors.background)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(doc.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDateTime(doc.updatedAt), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Действия", Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Удалить", color = colors.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun EmptyHint(kind: DocKind) {
    val text = when (kind) {
        DocKind.Table -> "Создайте таблицу: колонки с текстом и числами, суммы и средние, обмен с Excel через CSV"
        DocKind.Chart -> "График строится по таблице и обновляется вместе с ней"
        DocKind.Diagram -> "Блок-схемы, карты мыслей и любые схемы из фигур и стрелок"
    }
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
