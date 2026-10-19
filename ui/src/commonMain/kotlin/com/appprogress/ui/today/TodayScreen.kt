package com.appprogress.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.appprogress.calendar.Occurrence
import com.appprogress.calendar.Recurrence
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import com.appprogress.core.nowMillis
import com.appprogress.focus.FocusTimer
import com.appprogress.kanban.CardRef
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.cardRefs
import com.appprogress.kanban.isDone
import com.appprogress.mail.MailMessage
import com.appprogress.mail.MailPriority
import com.appprogress.mail.MailRepository
import com.appprogress.notes.DailyNote
import com.appprogress.notes.NoteMarkup
import com.appprogress.notes.NotesRepository
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.NavTarget
import com.appprogress.ui.notes.ToTaskDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private class TodayData(
    val overdue: List<CardRef> = emptyList(),
    val dueToday: List<CardRef> = emptyList(),
    val week: List<CardRef> = emptyList(),
    val events: List<Occurrence> = emptyList(),
    val mails: List<MailMessage> = emptyList(),
    val dailyPreview: String? = null,
    val focusMinutesToday: Int = 0,
)

/** Стартовый экран: всё, что важно сегодня, на одной странице. */
@OptIn(ExperimentalTime::class)
@Composable
fun TodayScreen(container: AppContainer) {
    val scope = container.scope
    val navigator = LocalNavigator.current
    val kanban = remember { KanbanRepository(container.db, container.io) }
    val calendar = remember { CalendarRepository(container.db, container.io) }
    val mail = remember { MailRepository(container.db, container.io) }
    var refresh by remember { mutableStateOf(0) }
    var captured by remember { mutableStateOf<String?>(null) }
    var taskFrom by remember { mutableStateOf<MailMessage?>(null) }
    val today = Dates.today()

    val data by produceState(TodayData(), refresh) {
        val open = runCatching { kanban.cardRefs() }.getOrDefault(emptyList()).filter { !it.column.isDone() && it.card.dueDate != null }
        val weekEnd = today.plus(DatePeriod(days = 7))
        val dailyId = container.notes.findDaily(today)
        val tz = TimeZone.currentSystemDefault()
        val dayStart = today.atStartOfDayIn(tz).toEpochMilliseconds()
        value = TodayData(
            overdue = open.filter { it.card.dueDate!! < today }.sortedBy { it.card.dueDate },
            dueToday = open.filter { it.card.dueDate == today },
            week = open.filter { it.card.dueDate!! > today && it.card.dueDate!! <= weekEnd }.sortedBy { it.card.dueDate },
            events = Recurrence.occurrences(calendar.all(), today, today.plus(DatePeriod(days = 1))),
            mails = runCatching { mail.recentMessages(300) }.getOrDefault(emptyList()).filter { !it.isRead && it.priority == MailPriority.High }.take(5),
            dailyPreview = dailyId?.let { container.notes.get(it)?.content }?.lineSequence()
                ?.map { NoteMarkup.plain(it).trim() }?.filter { it.isNotEmpty() && !it.startsWith("Мысли") }?.take(6)?.joinToString("\n"),
            focusMinutesToday = container.time.between(dayStart, Long.MAX_VALUE).sumOf { it.minutes }.toInt(),
        )
    }

    val complete: (CardRef) -> Unit = { r ->
        scope.launch {
            val columns = kanban.observeBoard(r.board.id).first()
            val done = columns.firstOrNull { it.isDone() } ?: columns.last()
            kanban.moveCard(r.card.id, done.id, Int.MAX_VALUE)
            refresh++
        }
    }
    val openDaily: () -> Unit = {
        scope.launch {
            val id = container.notes.openOrCreateDaily(today) { DailyNote.build(today, calendar, kanban) }
            navigator.open(NavTarget.Note(id))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
                Text(greeting(hour), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(RuText.dayMonthWeekday(today).replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FocusWidget(container.focus, data.focusMinutesToday, onFinished = { refresh++ })
        }

        QuickCapture(onCapture = { text ->
            scope.launch {
                val inbox = container.notes.ensureFolder(NotesRepository.INBOX_FOLDER)
                container.notes.create(text.lineSequence().first().take(80), text, folderId = inbox)
                captured = "Записано во «${NotesRepository.INBOX_FOLDER}»"
            }
        }, message = captured)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel("Задачи") {
                    if (data.overdue.isEmpty() && data.dueToday.isEmpty() && data.week.isEmpty()) {
                        Muted("Горящих задач нет — задачи со сроком появятся здесь")
                    }
                    TaskGroup("Просрочено", data.overdue, MaterialTheme.colorScheme.error, complete, container, navigator::open)
                    TaskGroup("Сегодня", data.dueToday, MaterialTheme.colorScheme.primary, complete, container, navigator::open)
                    TaskGroup("Ближайшие 7 дней", data.week, MaterialTheme.colorScheme.onSurfaceVariant, complete, container, navigator::open)
                }
                Panel("События") {
                    if (data.events.isEmpty()) Muted("Сегодня и завтра событий нет")
                    data.events.forEach { o ->
                        val time = o.event.startTime?.let { RuText.time(it) } ?: "весь день"
                        val day = if (o.date == today) "сегодня" else "завтра"
                        Row(Modifier.fillMaxWidth().clickable { navigator.open(NavTarget.Day(o.date)) }.padding(vertical = 6.dp)) {
                            Text("$day, $time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(130.dp))
                            Text(o.event.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel("Дневник") {
                    data.dailyPreview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                        ?: Muted("Ежедневная заметка соберёт события и задачи дня и место для мыслей")
                    Button(onClick = openDaily, modifier = Modifier.padding(top = 6.dp)) {
                        Text(if (data.dailyPreview == null) "Начать дневник" else "Открыть дневник")
                    }
                }
                Panel("Важные письма") {
                    if (data.mails.isEmpty()) Muted("Непрочитанных важных писем нет")
                    data.mails.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f).clickable { navigator.open(NavTarget.Mail(m.id)) }) {
                                Text(m.sender, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.subject, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { taskFrom = m }) { Text("В задачу") }
                        }
                    }
                }
            }
        }
    }

    taskFrom?.let { m ->
        ToTaskDialog(
            container = container,
            initialTitle = m.subject.ifBlank { "Письмо от ${m.sender}" },
            description = "Из письма от ${m.sender} <${m.fromAddress}>, тема «${m.subject}»\napp://mail/${m.id}\n\n${m.snippet}",
            labels = listOf("почта"),
            priority = com.appprogress.kanban.Priority.High,
            onCreated = { _, _ -> taskFrom = null; refresh++ },
            onDismiss = { taskFrom = null },
        )
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

@Composable
private fun Muted(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun TaskGroup(
    title: String,
    tasks: List<CardRef>,
    accent: Color,
    onComplete: (CardRef) -> Unit,
    container: AppContainer,
    open: (NavTarget) -> Unit,
) {
    if (tasks.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.padding(top = 6.dp))
    tasks.take(8).forEach { r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = false, onCheckedChange = { onComplete(r) }, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f).clickable { open(NavTarget.Card(r.card.id)) }) {
                Text(r.card.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.board.title} · ${r.column.title}" + (r.card.dueDate?.let { " · до ${Dates.format(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { container.focus.startFocus(r.card.title, r.card.id) }) {
                Icon(Icons.Filled.PlayArrow, "Фокус 25 минут на задаче", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun QuickCapture(onCapture: (String) -> Unit, message: String?) {
    var text by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Записать мысль… Enter — и она во «Входящих» (в любой программе: Ctrl+Alt+N)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && text.isNotBlank()) {
                    onCapture(text.trim()); text = ""; true
                } else false
            },
        )
        message?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) }
    }
}

/** Таймер Pomodoro: 25 минут фокуса, затем 5 минут перерыва; минуты фокуса за сегодня. */
@Composable
fun FocusWidget(timer: FocusTimer, minutesToday: Int, onFinished: () -> Unit = {}) {
    val state by timer.state.collectAsState()
    var now by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(state) {
        if (state is FocusTimer.State.Idle) onFinished()
        while (state is FocusTimer.State.Running) { now = nowMillis(); delay(1000) }
    }
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outlineVariant), color = colors.surface) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            when (val s = state) {
                FocusTimer.State.Idle -> {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text("Фокус", fontWeight = FontWeight.SemiBold)
                        Text("сегодня $minutesToday мин", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { timer.startFocus("Фокус") }) { Text("25 минут") }
                }
                is FocusTimer.State.Running -> {
                    val left = ((s.endsAt - now).coerceAtLeast(0) / 1000).toInt()
                    Box(Modifier.size(10.dp).background(if (s.phase == FocusTimer.Phase.Focus) colors.primary else colors.secondary, CircleShape))
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text("${left / 60}:${(left % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (s.phase == FocusTimer.Phase.Focus) s.title else "Перерыв",
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp),
                        )
                    }
                    TextButton(onClick = { timer.stop() }) { Text("Стоп") }
                }
            }
        }
    }
}

