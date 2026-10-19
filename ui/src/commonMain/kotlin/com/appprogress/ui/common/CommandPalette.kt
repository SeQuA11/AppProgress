package com.appprogress.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.Recurrence
import com.appprogress.core.Dates
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.cardRefs
import com.appprogress.mail.MailRepository
import com.appprogress.notes.DailyNote
import com.appprogress.notes.NotesRepository
import com.appprogress.studio.DocKind
import com.appprogress.studio.StudioRepository
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/** Строка палитры: что показать и что сделать при выборе. */
class PaletteItem(val title: String, val subtitle: String, val kind: String, val run: suspend () -> Unit)

/**
 * Палитра команд (Ctrl+K): поиск по всему приложению и быстрые действия без мыши.
 * ↑/↓ — выбор, Enter — выполнить, Esc — закрыть.
 */
@Composable
fun CommandPalette(container: AppContainer, navigator: Navigator, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val notes = container.notes
    val kanban = remember { KanbanRepository(container.db, container.io) }
    val calendar = remember { CalendarRepository(container.db, container.io) }

    val q = query.trim()
    val actions = buildList {
        if (q.isNotEmpty()) {
            add(PaletteItem("Новая заметка «$q»", "создать и открыть", "действие") {
                navigator.open(NavTarget.Note(notes.create(q)))
            })
            add(PaletteItem("Новая задача «$q»", "в первую колонку первой доски", "действие") {
                val boardId = kanban.observeBoards().first().firstOrNull()?.id ?: kanban.createBoard("Мои задачи")
                val column = kanban.observeBoard(boardId).first().first()
                navigator.open(NavTarget.Card(kanban.addCard(boardId, column.id, q)))
            })
            add(PaletteItem("Записать во «Входящие»: «$q»", "быстрая запись", "действие") {
                notes.create(q.take(80), q, folderId = notes.ensureFolder(NotesRepository.INBOX_FOLDER))
            })
        }
        add(PaletteItem("Дневник на сегодня", "ежедневная заметка", "действие") {
            val today = Dates.today()
            navigator.open(NavTarget.Note(notes.openOrCreateDaily(today) { DailyNote.build(today, calendar, kanban) }))
        })
        add(PaletteItem("Фокус 25 минут", "таймер Pomodoro", "действие") { container.focus.startFocus("Фокус") })
        Section.entries.forEach { s -> add(PaletteItem("Перейти: ${s.title}", "раздел", "раздел") { navigator.go(s) }) }
    }.filter { q.isEmpty() || it.kind == "действие" && it.title.contains("«") || it.title.contains(q, ignoreCase = true) }

    // Поиск по данным — с задержкой, чтобы не искать на каждую букву.
    val found by produceState(emptyList<PaletteItem>(), q) {
        if (q.length < 2) { value = emptyList(); return@produceState }
        delay(120)
        val out = mutableListOf<PaletteItem>()
        val summaries = notes.observeSummaries().first().associateBy { it.id }
        notes.search(q).mapNotNull(summaries::get).take(8).forEach { n ->
            out += PaletteItem(n.displayTitle, n.previewLine, "заметка") { navigator.open(NavTarget.Note(n.id)) }
        }
        summaries.values.filter { it.title.contains(q, ignoreCase = true) && out.none { o -> o.title == it.displayTitle } }.take(4).forEach { n ->
            out += PaletteItem(n.displayTitle, n.previewLine, "заметка") { navigator.open(NavTarget.Note(n.id)) }
        }
        runCatching { kanban.cardRefs() }.getOrDefault(emptyList()).filter { it.card.title.contains(q, ignoreCase = true) }.take(8).forEach { r ->
            out += PaletteItem(r.card.title, "${r.board.title} · ${r.column.title}", "задача") { navigator.open(NavTarget.Card(r.card.id)) }
        }
        val today = Dates.today()
        calendar.all().filter { it.title.contains(q, ignoreCase = true) }.take(6).forEach { e ->
            val next = Recurrence.occurrences(e, today, today.plus(DatePeriod(years = 1))).firstOrNull() ?: e.date
            out += PaletteItem(e.title, Dates.format(next), "событие") { navigator.open(NavTarget.Day(next)) }
        }
        runCatching { MailRepository(container.db, container.io).recentMessages(500) }.getOrDefault(emptyList())
            .filter { it.subject.contains(q, ignoreCase = true) || it.sender.contains(q, ignoreCase = true) }.take(6).forEach { m ->
                out += PaletteItem(m.subject.ifBlank { "(без темы)" }, "от ${m.sender}", "письмо") { navigator.open(NavTarget.Mail(m.id)) }
            }
        val studio = StudioRepository(container.db, container.io)
        DocKind.entries.forEach { kind ->
            studio.observe(kind).first().filter { it.title.contains(q, ignoreCase = true) }.take(4).forEach { d ->
                out += PaletteItem(d.title, kind.title, "студия") { navigator.go(Section.Studio) }
            }
        }
        value = out
    }
    val items = found + actions
    LaunchedEffect(items.size) { index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(index) { if (items.isNotEmpty()) listState.animateScrollToItem(index) }

    val runItem: (PaletteItem) -> Unit = { item ->
        onClose()
        container.scope.launch { runCatching { item.run() } }
    }

    // Затемнение фона; щелчок мимо окна закрывает палитру.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp,
            modifier = Modifier.padding(top = 80.dp).width(640.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; index = 0 },
                    placeholder = { Text("Найти заметку, задачу, событие, письмо… или выбрать действие") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { index = (index + 1).coerceAtMost(items.size - 1); true }
                            Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                            Key.Enter, Key.NumPadEnter -> { items.getOrNull(index)?.let(runItem); true }
                            Key.Escape -> { onClose(); true }
                            else -> false
                        }
                    },
                )
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp), state = listState) {
                    itemsIndexed(items) { i, item ->
                        val selected = i == index
                        Row(
                            Modifier.fillMaxWidth().background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp),
                            ).clickable { runItem(item) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.subtitle.isNotBlank()) {
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(item.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "↑↓ — выбор · Enter — открыть · Esc — закрыть",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Команды уровня окна: открыть палитру. Платформа (desktopApp) включает их по горячим клавишам окна. */
class AppCommands {
    var paletteOpen by mutableStateOf(false)
}
