package com.appprogress.ui.kanban

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appprogress.core.Dates
import com.appprogress.kanban.BoardColumn
import com.appprogress.kanban.Card
import com.appprogress.kanban.CardEdit
import com.appprogress.kanban.Priority
import com.appprogress.kanban.parseLabels
import com.appprogress.ui.common.APP_URI
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.NavTarget
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/** Форма карточки. [onSave] получает новые поля и колонку, если карточку перенесли. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardDialog(
    card: Card,
    columns: List<BoardColumn>,
    onSave: (CardEdit, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(card.title) }
    var description by remember { mutableStateOf(card.description) }
    var priority by remember { mutableStateOf(card.priority) }
    var dueText by remember { mutableStateOf(card.dueDate?.let(Dates::format).orEmpty()) }
    var labels by remember { mutableStateOf(card.labels.joinToString(", ")) }
    var columnId by remember { mutableStateOf(card.columnId) }

    val due = Dates.parse(dueText)
    val dueInvalid = dueText.isNotBlank() && due == null
    val canSave = title.isNotBlank() && !dueInvalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Карточка") },
        text = {
            Column(
                Modifier.width(480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    description, { description = it },
                    label = { Text("Описание") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                // Карточка из заметки или письма помнит, откуда она: app://note/ID, app://mail/ID в описании.
                val navigator = LocalNavigator.current
                val sources = APP_URI.findAll(description).map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()
                if (sources.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEach { (kind, id) ->
                            val (label, target) = when (kind) {
                                "note" -> "Открыть заметку" to NavTarget.Note(id)
                                "mail" -> "Открыть письмо" to NavTarget.Mail(id)
                                else -> return@forEach
                            }
                            AssistChip(onClick = { onDismiss(); navigator.open(target) }, label = { Text(label) })
                        }
                    }
                }

                Label("Приоритет")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(selected = p == priority, onClick = { priority = p }, label = { Text(p.title) })
                    }
                }

                OutlinedTextField(
                    dueText, { dueText = it },
                    label = { Text("Срок (дд.мм.гггг)") },
                    isError = dueInvalid,
                    supportingText = { if (dueInvalid) Text("Такой даты нет") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = Dates.today()
                    AssistChip(onClick = { dueText = Dates.format(today) }, label = { Text("Сегодня") })
                    AssistChip(onClick = { dueText = Dates.format(today.plus(DatePeriod(days = 1))) }, label = { Text("Завтра") })
                    AssistChip(onClick = { dueText = Dates.format(today.plus(DatePeriod(days = 7))) }, label = { Text("Через неделю") })
                    if (dueText.isNotEmpty()) AssistChip(onClick = { dueText = "" }, label = { Text("Без срока") })
                }

                OutlinedTextField(
                    labels, { labels = it },
                    label = { Text("Метки через запятую") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                Label("Колонка")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    columns.forEach { col ->
                        FilterChip(selected = col.id == columnId, onClick = { columnId = col.id }, label = { Text(col.title) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(CardEdit(title, description, priority, due, parseLabels(labels)), columnId) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
