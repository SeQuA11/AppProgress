package com.appprogress.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appprogress.calendar.CalendarEvent
import com.appprogress.calendar.EventKind
import com.appprogress.calendar.Repeat
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates

/** Варианты «напомнить за…»: минуты до начала события; null — без напоминания. */
private val remindOptions: List<Pair<Int?, String>> = listOf(
    null to "Не напоминать",
    0 to "В момент начала",
    15 to "За 15 минут",
    60 to "За час",
    24 * 60 to "За день",
    7 * 24 * 60 to "За неделю",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventDialog(
    initial: CalendarEvent,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var kind by remember { mutableStateOf(initial.kind) }
    var dateText by remember { mutableStateOf(Dates.format(initial.date)) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var startText by remember { mutableStateOf(initial.startTime?.let(RuText::time) ?: "10:00") }
    var endText by remember { mutableStateOf(initial.endTime?.let(RuText::time).orEmpty()) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var remind by remember { mutableStateOf(initial.remindMinutes) }
    var description by remember { mutableStateOf(initial.description) }

    val date = Dates.parse(dateText)
    val start = if (allDay) null else RuText.parseTime(startText)
    val end = if (allDay || endText.isBlank()) null else RuText.parseTime(endText)
    val startInvalid = !allDay && start == null
    val endInvalid = !allDay && endText.isNotBlank() && end == null
    val canSave = title.isNotBlank() && date != null && !startInvalid && !endInvalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isEmpty()) "Новое событие" else "Событие") },
        text = {
            Column(Modifier.width(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventKind.entries.forEach { k ->
                        FilterChip(
                            selected = k == kind,
                            onClick = {
                                kind = k
                                // День рождения и важная дата обычно на весь день и с повтором каждый год.
                                if (k == EventKind.Birthday || k == EventKind.Important) {
                                    allDay = true
                                    repeat = Repeat.Yearly
                                    if (remind == null) remind = 24 * 60
                                }
                            },
                            label = { Text(k.title) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        dateText, { dateText = it },
                        label = { Text(if (kind == EventKind.Birthday) "Дата рождения" else "Дата") },
                        isError = date == null, singleLine = true, modifier = Modifier.width(170.dp),
                    )
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                    Text("Весь день")
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(startText, { startText = it }, label = { Text("Начало ЧЧ:ММ") }, isError = startInvalid, singleLine = true, modifier = Modifier.width(150.dp))
                        OutlinedTextField(endText, { endText = it }, label = { Text("Конец (необяз.)") }, isError = endInvalid, singleLine = true, modifier = Modifier.width(150.dp))
                    }
                }

                if (kind != EventKind.Birthday) {
                    Caption("Повтор")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Repeat.entries.forEach { r -> FilterChip(selected = r == repeat, onClick = { repeat = r }, label = { Text(r.title) }) }
                    }
                } else {
                    Caption("День рождения повторяется каждый год")
                }

                Caption("Напоминание")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    remindOptions.forEach { (minutes, label) ->
                        FilterChip(selected = remind == minutes, onClick = { remind = minutes }, label = { Text(label) })
                    }
                }
                if (allDay && remind != null) Caption("Для событий на весь день время отсчёта — 9:00")

                OutlinedTextField(description, { description = it }, label = { Text("Заметка") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        initial.copy(
                            title = title, kind = kind, date = date!!, startTime = start, endTime = end,
                            repeat = repeat, remindMinutes = remind, description = description,
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
