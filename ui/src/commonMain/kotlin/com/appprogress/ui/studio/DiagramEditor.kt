package com.appprogress.ui.studio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.appprogress.studio.DiagramData
import com.appprogress.studio.DiagramNode
import com.appprogress.studio.ShapeKind
import com.appprogress.studio.StudioDoc
import com.appprogress.studio.StudioJson
import com.appprogress.studio.StudioRepository
import com.appprogress.ui.common.TextInputDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 3f

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DiagramEditor(doc: StudioDoc, repo: StudioRepository, scope: CoroutineScope) {
    var diagram by remember { mutableStateOf(StudioJson.diagram(doc)) }
    val status = rememberAutosave(diagram) { scope.launch { repo.saveData(doc.id, StudioJson.encode(it)) } }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(40f, 40f)) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var connectMode by remember { mutableStateOf(false) }
    var connectFrom by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf<DiagramNode?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    fun zoomAt(point: Offset, factor: Float) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        pan = point - (point - pan) * (newScale / scale)
        scale = newScale
    }

    fun addShape(kind: ShapeKind) {
        // Новая фигура — в центре видимой области, со сдвигом, чтобы фигуры не ложились стопкой.
        val shift = (diagram.nodes.size % 6) * 18f
        val cx = (viewport.width / 2f - pan.x) / scale - 80f + shift
        val cy = (viewport.height / 2f - pan.y) / scale - 36f + shift
        val (updated, id) = diagram.addNode(kind, cx, cy)
        diagram = updated
        selectedId = id
    }

    fun onNodeClick(node: DiagramNode) {
        if (!connectMode) {
            selectedId = node.id
            return
        }
        val from = connectFrom
        if (from == null) {
            connectFrom = node.id
        } else {
            diagram = diagram.toggleEdge(from, node.id)
            connectFrom = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(doc.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(info ?: status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeKind.entries.forEach { kind ->
                OutlinedButton(onClick = { addShape(kind) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(kind.title) }
            }
            FilterChip(
                selected = connectMode,
                onClick = { connectMode = !connectMode; connectFrom = null },
                label = { Text("Соединить") },
            )
            TextButton(
                enabled = selectedId != null,
                onClick = { selectedId?.let { diagram = diagram.removeNode(it) }; selectedId = null },
            ) { Text("Удалить", color = if (selectedId != null) colors.error else colors.onSurfaceVariant) }
            TextButton(onClick = { zoomAt(Offset(viewport.width / 2f, viewport.height / 2f), 1 / 1.2f) }) { Text("−") }
            Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
            TextButton(onClick = { zoomAt(Offset(viewport.width / 2f, viewport.height / 2f), 1.2f) }) { Text("+") }
            TextButton(onClick = {
                info = exportDiagramPng(diagram, doc.title)?.let { "Сохранено: $it" }
            }, enabled = diagram.nodes.isNotEmpty()) { Text("Экспорт PNG") }
        }

        // Палитра цвета для выбранной фигуры и подсказка для режима соединения.
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val selected = selectedId?.let(diagram::node)
            if (connectMode) {
                Text(
                    if (connectFrom == null) "Щёлкните фигуру, откуда идёт стрелка" else "Теперь щёлкните фигуру, куда она ведёт. Повторное соединение удаляет связь",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
            } else if (selected != null) {
                Text("Цвет:", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                DiagramNode.DEFAULT_COLORS.forEach { argb ->
                    Box(
                        Modifier.padding(start = 6.dp).size(20.dp).background(Color(argb), CircleShape)
                            .border(if (selected.color == argb) 2.dp else 1.dp, if (selected.color == argb) colors.primary else colors.outlineVariant, CircleShape)
                            .clickable { diagram = diagram.updateNode(selected.id) { it.copy(color = argb) } },
                    )
                }
                Text(
                    "  Двойной щелчок — изменить текст, Delete — удалить",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Text(
                    "Тяните фон, чтобы двигать холст; колесо мыши — масштаб",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        DiagramView(
            diagram = diagram,
            scale = scale,
            pan = pan,
            selectedId = selectedId,
            highlightId = connectFrom,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
                .background(colors.background, MaterialTheme.shapes.medium)
                .onSizeChanged { viewport = it }
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    val id = selectedId
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Delete || e.key == Key.Backspace) && id != null) {
                        diagram = diagram.removeNode(id)
                        selectedId = null
                        true
                    } else false
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        selectedId = null
                        connectFrom = null
                        focus.requestFocus()
                    })
                }
                .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); pan += amount } }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.first()
                                zoomAt(change.position, if (change.scrollDelta.y < 0) 1.1f else 1 / 1.1f)
                                change.consume()
                            }
                        }
                    }
                },
            nodeModifier = { node ->
                Modifier
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = { if (!connectMode) selectedId = node.id },
                            onDrag = { change, amount ->
                                change.consume()
                                diagram = diagram.moveNode(node.id, amount.x / scale, amount.y / scale)
                            },
                        )
                    }
                    .combinedClickable(
                        onClick = { onNodeClick(node); focus.requestFocus() },
                        onDoubleClick = { editingText = node },
                    )
            },
        )
    }

    editingText?.let { node ->
        TextInputDialog(
            title = "Текст фигуры",
            initial = node.text,
            label = "Текст",
            onConfirm = { text -> diagram = diagram.updateNode(node.id) { it.copy(text = text) }; editingText = null },
            onDismiss = { editingText = null },
        )
    }
}

/** Сохраняет диаграмму в PNG (на ПК — через диалог «Сохранить как»). Возвращает путь или null, если отменили. */
expect fun exportDiagramPng(diagram: DiagramData, title: String): String?
