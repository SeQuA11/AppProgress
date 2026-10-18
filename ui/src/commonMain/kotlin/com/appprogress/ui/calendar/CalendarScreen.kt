package com.appprogress.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.calendar.CalendarEvent
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.EventKind
import com.appprogress.calendar.Occurrence
import com.appprogress.calendar.Recurrence
import com.appprogress.calendar.Repeat
import com.appprogress.calendar.RuText
import com.appprogress.calendar.firstOfMonth
import com.appprogress.calendar.startOfWeek
import com.appprogress.core.Dates
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.NavTarget
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

fun EventKind.color(): Color = when (this) {
    EventKind.Event -> Color(0xFF4F46E5)
    EventKind.Birthday -> Color(0xFFE5484D)
    EventKind.Important -> Color(0xFFF59E0B)
    EventKind.Note -> Color(0xFF0E9F8E)
}

@Composable
fun CalendarScreen(container: AppContainer) {
    val repo = remember { CalendarRepository(container.db, container.io) }
    val events by remember { repo.observeEvents() }.collectAsState(initial = emptyList())
    val today = Dates.today()
    var month by remember { mutableStateOf(today.firstOfMonth()) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    val navigator = LocalNavigator.current
    LaunchedEffect(navigator.pending) {
        navigator.consume<NavTarget.Day>()?.let { selected = it.date; month = it.date.firstOfMonth() }
    }
    // Не только события: заметки, привязанные к дате, тоже видны в календаре.
    val notes by remember { container.notes.observeSummaries() }.collectAsState(initial = emptyList())
    val extras: Map<LocalDate, List<CalendarExtra>> = notes.filter { it.date != null }.groupBy({ it.date!! }) { n ->
        CalendarExtra(n.displayTitle, NOTE_COLOR, "заметка") { navigator.open(NavTarget.Note(n.id)) }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            MonthHeader(
                month = month,
                onPrev = { month = month.plus(DatePeriod(months = -1)) },
                onNext = { month = month.plus(DatePeriod(months = 1)) },
                onToday = { month = today.firstOfMonth(); selected = today },
                onAdd = { editing = CalendarEvent(title = "", date = selected) },
            )
            Spacer(Modifier.height(12.dp))
            MonthGrid(
                month = month,
                today = today,
                selected = selected,
                events = events,
                extras = extras,
                onSelect = { selected = it },
                onOpen = { editing = it.event },
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DayPanel(
            day = selected,
            today = today,
            events = events,
            extras = extras[selected].orEmpty(),
            onAdd = { editing = CalendarEvent(title = "", date = selected) },
            onOpen = { editing = it },
        )
    }

    editing?.let { e ->
        EventDialog(
            initial = e,
            onSave = { saved -> editing = null; container.scope.launch { repo.save(saved) } },
            onDelete = if (e.id.isNotEmpty()) ({ editing = null; container.scope.launch { repo.delete(e.id) } }) else null,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MonthHeader(month: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            RuText.monthTitle(month),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(240.dp),
        )
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий месяц") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц") }
        OutlinedButton(onClick = onToday, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
            Text("Сегодня", Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text("Событие", Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    events: List<CalendarEvent>,
    extras: Map<LocalDate, List<CalendarExtra>>,
    onSelect: (LocalDate) -> Unit,
    onOpen: (Occurrence) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val first = month.firstOfMonth().startOfWeek()
    val last = first.plus(DatePeriod(days = 41))
    val byDay = remember(events, first) { Recurrence.occurrences(events, first, last).groupBy { it.date } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            RuText.weekdaysShort.forEachIndexed { i, name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i >= 5) colors.error.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp, bottom = 6.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
        ) {
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { dow ->
                        val day = first.plus(DatePeriod(days = week * 7 + dow))
                        DayCell(
                            day = day,
                            inMonth = day.month == month.month,
                            isToday = day == today,
                            isSelected = day == selected,
                            occurrences = byDay[day].orEmpty(),
                            extras = extras[day].orEmpty(),
                            onClick = { onSelect(day) },
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        if (dow < 6) VerticalDivider(color = colors.outlineVariant)
                    }
                }
                if (week < 5) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    occurrences: List<Occurrence>,
    extras: List<CalendarExtra>,
    onClick: () -> Unit,
    onOpen: (Occurrence) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val bg = when {
        isSelected -> colors.primaryContainer.copy(alpha = 0.5f)
        !inMonth -> colors.surfaceVariant.copy(alpha = 0.35f)
        else -> colors.surface
    }
    Column(modifier.background(bg).clickable(onClick = onClick).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.size(24.dp).background(if (isToday) colors.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> colors.onPrimary
                    inMonth -> colors.onSurface
                    else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
        val shown = occurrences.take(3)
        shown.forEach { occ -> EventChip(occ, onClick = { onOpen(occ) }) }
        val shownExtras = extras.take((3 - shown.size).coerceAtLeast(0))
        shownExtras.forEach { ExtraChip(it) }
        val hidden = occurrences.size + extras.size - shown.size - shownExtras.size
        if (hidden > 0) {
            Text("ещё $hidden", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Дополнительные записи дня, не являющиеся событиями: заметки на дату и т.п. */
data class CalendarExtra(val title: String, val color: Color, val label: String, val onClick: () -> Unit)

private val NOTE_COLOR = Color(0xFF0E9F8E)

@Composable
private fun ExtraChip(extra: CalendarExtra) {
    Text(
        extra.title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, extra.color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = extra.onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun ExtraRow(extra: CalendarExtra) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, extra.color.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = extra.onClick),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(extra.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(extra.label, style = MaterialTheme.typography.bodySmall, color = extra.color)
        }
    }
}

@Composable
private fun EventChip(occ: Occurrence, onClick: () -> Unit) {
    val color = occ.event.kind.color()
    val text = buildString {
        occ.event.startTime?.let { append(RuText.time(it)).append(' ') }
        append(occ.event.title)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun DayPanel(
    day: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    extras: List<CalendarExtra>,
    onAdd: () -> Unit,
    onOpen: (CalendarEvent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dayItems = remember(events, day) { Recurrence.occurrences(events, day, day) }
    val upcoming = remember(events, today) {
        Recurrence.occurrences(events, today, today.plus(DatePeriod(days = 60))).take(8)
    }
    Column(
        Modifier.width(340.dp).fillMaxHeight().background(colors.surface).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (day == today) "Сегодня" else RuText.dayMonth(day),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(RuText.dayMonthWeekday(day), color = colors.onSurfaceVariant)
        if (dayItems.isEmpty() && extras.isEmpty()) {
            Text("Ничего не запланировано", color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        dayItems.forEach { occ -> OccurrenceRow(occ, showDate = false, onClick = { onOpen(occ.event) }) }
        extras.forEach { ExtraRow(it) }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text("Добавить на этот день", Modifier.padding(start = 6.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text("Ближайшие", style = MaterialTheme.typography.titleMedium)
        if (upcoming.isEmpty()) Text("В ближайшие два месяца событий нет", color = colors.onSurfaceVariant)
        upcoming.forEach { occ -> OccurrenceRow(occ, showDate = true, onClick = { onOpen(occ.event) }) }
    }
}

@Composable
private fun OccurrenceRow(occ: Occurrence, showDate: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val e = occ.event
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).background(e.kind.color(), RoundedCornerShape(2.dp)))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(e.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = buildList {
                    if (showDate) add(RuText.dayMonth(occ.date))
                    add(e.startTime?.let { s -> RuText.time(s) + (e.endTime?.let { "–" + RuText.time(it) } ?: "") } ?: "весь день")
                    e.ageOn(occ.date)?.let { add("исполнится ${RuText.years(it)}") }
                    if (e.repeat != Repeat.None && e.kind != EventKind.Birthday) add(e.repeat.title.lowercase())
                }
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (e.remindMinutes != null) Icon(Icons.Filled.Notifications, "Есть напоминание", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
        }
    }
}
