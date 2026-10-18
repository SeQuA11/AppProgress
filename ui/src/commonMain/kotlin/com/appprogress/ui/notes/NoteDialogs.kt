package com.appprogress.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.Recurrence
import com.appprogress.core.Dates
import com.appprogress.kanban.Board
import com.appprogress.kanban.BoardColumn
import com.appprogress.kanban.CardEdit
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.Priority
import com.appprogress.kanban.cardRefs
import com.appprogress.mail.MailRepository
import com.appprogress.notes.LinkKind
import com.appprogress.notes.NoteMarkup
import com.appprogress.notes.NoteSummary
import com.appprogress.notes.NoteVersion
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.formatDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
fun VersionsDialog(versions: List<NoteVersion>, onRestore: (NoteVersion) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(versions.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История версий") },
        text = {
            if (versions.isEmpty()) {
                Text("Версий пока нет. Снимок текста сохраняется перед правкой, не чаще раза в 10 минут.")
                return@AlertDialog
            }
            Row(Modifier.width(720.dp).height(420.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.width(220.dp)) {
                    items(versions) { v ->
                        Text(
                            formatDateTime(v.createdAt),
                            fontWeight = if (v == selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (v == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .clickable { selected = v }.padding(10.dp),
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.weight(1f)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        selected?.let {
                            Text(it.title, fontWeight = FontWeight.SemiBold)
                            Text(it.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let(onRestore) }) { Text("Восстановить эту версию") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Пароль: [confirm] = true — задать новый (дважды), false — ввести для открытия. */
@Composable
fun PasswordDialog(
    title: String,
    confirm: Boolean,
    error: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val mismatch = confirm && second.isNotEmpty() && first != second
    val ok = first.length >= 4 && (!confirm || first == second)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (confirm) {
                    Text(
                        "Пароль нигде не сохраняется. Если забыть его, заметку будет невозможно открыть.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                PasswordField(first, { first = it }, "Пароль (не короче 4 символов)")
                if (confirm) PasswordField(second, { second = it }, "Повторите пароль", isError = mismatch)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(first) }) { Text(if (confirm) "Зашифровать" else "Открыть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier,
    )
}

/** Дата заметки: показывается в календаре в этот день. */
@Composable
fun NoteDateDialog(current: LocalDate?, onSet: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.let(Dates::format) ?: Dates.format(Dates.today())) }
    val parsed = Dates.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Привязать к дате") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Заметка появится в календаре в этот день.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, label = { Text("Дата дд.мм.гггг") }, isError = parsed == null, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val today = Dates.today()
                    TextButton(onClick = { text = Dates.format(today) }) { Text("Сегодня") }
                    TextButton(onClick = { text = Dates.format(today.plus(DatePeriod(days = 1))) }) { Text("Завтра") }
                }
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onSet(parsed) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null) }) { Text("Убрать дату") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

private data class LinkCandidate(val kind: LinkKind, val id: String, val label: String, val details: String, val date: String? = null)

/** Поиск по заметкам, задачам, событиям и письмам; выбранное вставляется в заметку ссылкой. */
@Composable
fun LinkPickerDialog(container: AppContainer, notes: List<NoteSummary>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val candidates by produceState(emptyList<LinkCandidate>()) {
        val list = mutableListOf<LinkCandidate>()
        notes.forEach { list += LinkCandidate(LinkKind.Note, it.id, it.displayTitle, "заметка") }
        runCatching {
            KanbanRepository(container.db, container.io).cardRefs().forEach { r ->
                list += LinkCandidate(LinkKind.Card, r.card.id, r.card.title, "задача · ${r.board.title} / ${r.column.title}")
            }
        }
        runCatching {
            val today = Dates.today()
            CalendarRepository(container.db, container.io).all().forEach { e ->
                val next = Recurrence.occurrences(e, today, today.plus(DatePeriod(years = 1))).firstOrNull() ?: e.date
                list += LinkCandidate(LinkKind.Event, e.id, e.title, "событие · ${Dates.format(next)}", next.toString())
            }
        }
        runCatching {
            MailRepository(container.db, container.io).recentMessages(500).forEach { m ->
                list += LinkCandidate(LinkKind.Mail, m.id, m.subject.ifBlank { "(без темы)" }, "письмо от ${m.sender}")
            }
        }
        value = list
    }
    val q = query.trim().lowercase()
    val shown = candidates.filter { q.isEmpty() || q in it.label.lowercase() || q in it.details.lowercase() }.take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Связать с…") },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Название задачи, события, письма или заметки") }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown) { c ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                onPick(
                                    if (c.kind == LinkKind.Note && c.label != "Без названия") "[[${c.label}]]"
                                    else NoteMarkup.appLink(c.kind, c.id, c.label, c.date),
                                )
                            }.padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Text(c.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** «Строка → задача»: выбираем доску и колонку, создаём карточку со ссылкой обратно на заметку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToTaskDialog(
    container: AppContainer,
    initialTitle: String,
    noteId: String,
    noteTitle: String,
    onCreated: (cardId: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kanban = remember { KanbanRepository(container.db, container.io) }
    var title by remember { mutableStateOf(initialTitle) }
    var dueText by remember { mutableStateOf("") }
    val boards by produceState(emptyList<Pair<Board, List<BoardColumn>>>()) {
        value = kanban.observeBoards().first().map { b -> b to kanban.observeBoard(b.id).first() }
    }
    var boardId by remember { mutableStateOf<String?>(null) }
    var columnId by remember { mutableStateOf<String?>(null) }
    val board = boards.firstOrNull { it.first.id == boardId } ?: boards.firstOrNull()
    val column = board?.second?.firstOrNull { it.id == columnId } ?: board?.second?.firstOrNull()
    val due = Dates.parse(dueText)
    val scope = container.scope

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сделать задачей") },
        text = {
            Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Задача") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (boards.isEmpty()) Text("Досок ещё нет — будет создана доска «Мои задачи».", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    boards.forEach { (b, _) -> FilterChip(selected = b.id == board?.first?.id, onClick = { boardId = b.id; columnId = null }, label = { Text(b.title) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    board?.second?.forEach { c -> FilterChip(selected = c.id == column?.id, onClick = { columnId = c.id }, label = { Text(c.title) }) }
                }
                OutlinedTextField(dueText, { dueText = it }, label = { Text("Срок дд.мм.гггг (необязательно)") }, isError = dueText.isNotBlank() && due == null, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && (dueText.isBlank() || due != null), onClick = {
                scope.launch {
                    val bId = board?.first?.id ?: kanban.createBoard("Мои задачи")
                    val cId = column?.id ?: kanban.observeBoard(bId).first().first().id
                    val id = kanban.addCard(bId, cId, title)
                    kanban.updateCard(
                        id,
                        CardEdit(title, "Из заметки «$noteTitle»\napp://note/$noteId", Priority.None, due, emptyList()),
                    )
                    onCreated(id, title)
                }
            }) { Text("Создать задачу") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
