package com.appprogress.assistant

import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.Recurrence
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import com.appprogress.db.AppDatabase
import com.appprogress.kanban.KanbanRepository
import com.appprogress.mail.MailPriority
import com.appprogress.mail.MailRepository
import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonObject

/** Сводка дня: события сегодня, горящие задачи, непрочитанные важные письма — одним вызовом. */
fun daySummaryTool(calendar: CalendarRepository, kanban: KanbanRepository, mail: MailRepository): AssistantTool = object : AssistantTool {
    override val spec = ToolSpec(
        "day_summary",
        "Сводка на сегодня: события календаря, просроченные и сегодняшние задачи, непрочитанные важные письма.",
        stringParams(required = emptyList()),
    )

    override fun activity(args: JsonObject) = "Собираю сводку дня"

    override suspend fun run(args: JsonObject): String {
        val today = Dates.today()
        val events = Recurrence.occurrences(calendar.all(), today, today)
        val tasks = kanban.allTasks().filter { t -> !t.column.isDone() && t.card.dueDate?.let { it <= today } == true }
        val mailItems = mail.recentMessages(500).filter { !it.isRead && it.priority == MailPriority.High }
        return buildString {
            appendLine("Сегодня ${Dates.format(today)}, ${RuText.weekday(today)}.")
            appendLine("События (${events.size}):")
            if (events.isEmpty()) appendLine("- нет") else events.forEach { appendLine(it.line()) }
            appendLine("Задачи на сегодня и просроченные (${tasks.size}):")
            if (tasks.isEmpty()) appendLine("- нет") else tasks.take(15).forEach { appendLine(it.line()) }
            appendLine("Непрочитанные важные письма (${mailItems.size}):")
            if (mailItems.isEmpty()) appendLine("- нет") else mailItems.take(10).forEach { appendLine(it.line()) }
        }.trim()
    }
}

/** Все инструменты ассистента: заметки, канбан, календарь, почта, сводка дня и дата. */
fun assistantTools(
    db: AppDatabase,
    io: CoroutineDispatcher,
    notes: NotesRepository = NotesRepository(db, io),
    mail: MailRepository = MailRepository(db, io),
): List<AssistantTool> {
    val kanban = KanbanRepository(db, io)
    val calendar = CalendarRepository(db, io)
    return listOf(daySummaryTool(calendar, kanban, mail), dateTool()) +
        notesTools(notes) + kanbanTools(kanban) + calendarTools(calendar) + mailTools(mail, kanban)
}
