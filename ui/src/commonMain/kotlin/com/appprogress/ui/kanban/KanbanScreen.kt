package com.appprogress.ui.kanban

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.appprogress.kanban.Board
import com.appprogress.kanban.BoardColumn
import com.appprogress.kanban.Card
import com.appprogress.kanban.KanbanRepository
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.ConfirmDialog
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.NavTarget
import com.appprogress.ui.common.TextInputDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface KanbanDialog {
    data object NewBoard : KanbanDialog
    data class RenameBoard(val board: Board) : KanbanDialog
    data class DeleteBoard(val board: Board) : KanbanDialog
    data class NewColumn(val boardId: String) : KanbanDialog
    data class RenameColumn(val column: BoardColumn) : KanbanDialog
    data class DeleteColumn(val column: BoardColumn) : KanbanDialog
}

@Composable
fun KanbanScreen(container: AppContainer) {
    val repo = remember { KanbanRepository(container.db, container.io) }
    val scope = container.scope
    val boards by remember { repo.observeBoards() }.collectAsState(initial = null)
    var selectedId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<KanbanDialog?>(null) }
    // Карточка, которую нужно открыть по ссылке из заметки, календаря или палитры команд.
    var openCardId by remember { mutableStateOf<String?>(null) }
    val navigator = LocalNavigator.current
    LaunchedEffect(navigator.pending) {
        navigator.consume<NavTarget.Card>()?.let { target ->
            repo.allCards().firstOrNull { it.id == target.id }?.let { card ->
                selectedId = card.boardId
                openCardId = card.id
            }
        }
    }

    LaunchedEffect(boards) {
        val list = boards ?: return@LaunchedEffect
        if (list.none { it.id == selectedId }) selectedId = list.firstOrNull()?.id
    }
    val selected = boards?.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize()) {
        BoardsHeader(
            boards = boards.orEmpty(),
            selected = selected,
            onSelect = { selectedId = it.id },
            onNewBoard = { dialog = KanbanDialog.NewBoard },
            onDialog = { dialog = it },
        )
        when {
            boards == null -> Unit
            boards!!.isEmpty() -> EmptyBoards { dialog = KanbanDialog.NewBoard }
            selected != null -> BoardView(repo, scope, selected.id, openCardId, onOpened = { openCardId = null }, onDialog = { dialog = it })
        }
    }

    when (val d = dialog) {
        null -> Unit
        KanbanDialog.NewBoard -> TextInputDialog(
            title = "Новая доска", initial = if (boards.isNullOrEmpty()) "Мои задачи" else "", confirmLabel = "Создать",
            onConfirm = { name -> dialog = null; scope.launch { selectedId = repo.createBoard(name) } },
            onDismiss = { dialog = null },
        )
        is KanbanDialog.RenameBoard -> TextInputDialog(
            title = "Переименовать доску", initial = d.board.title,
            onConfirm = { name -> dialog = null; scope.launch { repo.renameBoard(d.board.id, name) } },
            onDismiss = { dialog = null },
        )
        is KanbanDialog.DeleteBoard -> ConfirmDialog(
            title = "Удалить доску?", text = "Доска «${d.board.title}» и все её карточки будут удалены.",
            onConfirm = { scope.launch { repo.deleteBoard(d.board.id) } },
            onDismiss = { dialog = null },
        )
        is KanbanDialog.NewColumn -> TextInputDialog(
            title = "Новая колонка", confirmLabel = "Добавить",
            onConfirm = { name -> dialog = null; scope.launch { repo.addColumn(d.boardId, name) } },
            onDismiss = { dialog = null },
        )
        is KanbanDialog.RenameColumn -> TextInputDialog(
            title = "Переименовать колонку", initial = d.column.title,
            onConfirm = { name -> dialog = null; scope.launch { repo.renameColumn(d.column.id, name) } },
            onDismiss = { dialog = null },
        )
        is KanbanDialog.DeleteColumn -> ConfirmDialog(
            title = "Удалить колонку?",
            text = "Колонка «${d.column.title}» и ${d.column.cards.size} карточек в ней будут удалены.",
            onConfirm = { scope.launch { repo.deleteColumn(d.column.id) } },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun BoardsHeader(
    boards: List<Board>,
    selected: Board?,
    onSelect: (Board) -> Unit,
    onNewBoard: () -> Unit,
    onDialog: (KanbanDialog) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Канбан", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            boards.forEach { b -> FilterChip(selected = b.id == selected?.id, onClick = { onSelect(b) }, label = { Text(b.title) }) }
            TextButton(onClick = onNewBoard) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Text("Доска", Modifier.padding(start = 4.dp))
            }
        }
        if (selected != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Действия с доской") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Добавить колонку") }, onClick = { menu = false; onDialog(KanbanDialog.NewColumn(selected.id)) })
                    DropdownMenuItem(text = { Text("Переименовать доску") }, onClick = { menu = false; onDialog(KanbanDialog.RenameBoard(selected)) })
                    DropdownMenuItem(
                        text = { Text("Удалить доску", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDialog(KanbanDialog.DeleteBoard(selected)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBoards(onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Здесь будут ваши доски с задачами", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCreate) { Text("Создать доску") }
    }
}

@Composable
private fun BoardView(
    repo: KanbanRepository,
    scope: CoroutineScope,
    boardId: String,
    openCardId: String?,
    onOpened: () -> Unit,
    onDialog: (KanbanDialog) -> Unit,
) {
    val columns by remember(boardId) { repo.observeBoard(boardId) }.collectAsState(initial = emptyList())
    val drag = remember(boardId) { DragController() }
    drag.columns = columns
    var editing by remember { mutableStateOf<Card?>(null) }
    LaunchedEffect(openCardId, columns) {
        val card = columns.flatMap { it.cards }.firstOrNull { it.id == openCardId } ?: return@LaunchedEffect
        editing = card
        onOpened()
    }
    val target = drag.target()

    val onDrop: () -> Unit = {
        drag.finish()?.let { (card, t) -> scope.launch { repo.moveCard(card.id, t.columnId, t.index) } }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { drag.origin = it.positionInRoot() }) {
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            columns.forEach { column ->
                ColumnView(
                    column = column,
                    drag = drag,
                    dropIndex = target?.takeIf { it.columnId == column.id }?.index,
                    onDrop = onDrop,
                    onCardClick = { editing = it },
                    onAddCard = { title -> scope.launch { repo.addCard(boardId, column.id, title) } },
                    onDialog = onDialog,
                )
            }
            AddColumnTile(onClick = { onDialog(KanbanDialog.NewColumn(boardId)) })
        }

        // «Летящая» карточка рисуется поверх доски там, где сейчас курсор.
        drag.dragged?.let { d ->
            val density = LocalDensity.current
            val pos = d.pointer - d.grab - drag.origin
            CardView(
                d.card,
                Modifier
                    .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                    .width(with(density) { d.widthPx.toDp() })
                    .rotate(2f),
                elevated = true,
            )
        }
    }

    editing?.let { card ->
        CardDialog(
            card = card,
            columns = columns,
            onSave = { edit, columnId ->
                editing = null
                scope.launch {
                    repo.updateCard(card.id, edit)
                    if (columnId != card.columnId) repo.moveCard(card.id, columnId, Int.MAX_VALUE)
                }
            },
            onDelete = { editing = null; scope.launch { repo.deleteCard(card.id) } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ColumnView(
    column: BoardColumn,
    drag: DragController,
    dropIndex: Int?,
    onDrop: () -> Unit,
    onCardClick: (Card) -> Unit,
    onAddCard: (String) -> Unit,
    onDialog: (KanbanDialog) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isTarget = dropIndex != null
    Surface(
        modifier = Modifier.width(288.dp).fillMaxHeight()
            .onGloballyPositioned { drag.columnBounds[column.id] = it.boundsInRoot() },
        shape = RoundedCornerShape(14.dp),
        color = if (isTarget) colors.primaryContainer.copy(alpha = 0.45f) else colors.surfaceVariant.copy(alpha = 0.55f),
        border = if (isTarget) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                Text(column.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    column.cards.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, "Действия с колонкой", Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onDialog(KanbanDialog.RenameColumn(column)) })
                        DropdownMenuItem(
                            text = { Text("Удалить колонку", color = colors.error) },
                            onClick = { menu = false; onDialog(KanbanDialog.DeleteColumn(column)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val draggedId = drag.dragged?.card?.id
                var othersIndex = 0
                column.cards.forEach { card ->
                    val isDragged = card.id == draggedId
                    if (!isDragged) {
                        if (othersIndex == dropIndex) DropIndicator()
                        othersIndex++
                    }
                    CardView(
                        card,
                        Modifier
                            .onGloballyPositioned { drag.cardBounds[card.id] = it.boundsInRoot() }
                            .alpha(if (isDragged) 0.35f else 1f)
                            .pointerInput(card.id) {
                                detectDragGestures(
                                    onDragStart = { offset -> drag.start(card, offset) },
                                    onDrag = { change, amount -> change.consume(); drag.move(amount) },
                                    onDragEnd = { onDrop() },
                                    onDragCancel = { drag.cancel() },
                                )
                            }
                            .clickable { onCardClick(card) },
                    )
                }
                if (dropIndex != null && dropIndex >= othersIndex) DropIndicator()
            }

            AddCardInline(onAddCard)
        }
    }
}

@Composable
private fun DropIndicator() {
    Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
}

/** Быстрое добавление: Enter — добавить и остаться в поле для следующей карточки, Esc — закрыть. */
@Composable
private fun AddCardInline(onAdd: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    if (!adding) {
        TextButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text("Добавить карточку", Modifier.padding(start = 4.dp).weight(1f))
        }
        return
    }
    val focus = remember { FocusRequester() }
    val submit = {
        if (text.isNotBlank()) onAdd(text.trim())
        text = ""
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Название карточки") },
            singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> { submit(); true }
                    Key.Escape -> { adding = false; text = ""; true }
                    else -> false
                }
            },
        )
        IconButton(onClick = { adding = false; text = "" }) { Icon(Icons.Filled.Close, "Отмена") }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun AddColumnTile(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(220.dp).height(52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Колонка", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
