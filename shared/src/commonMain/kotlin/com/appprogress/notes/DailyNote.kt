package com.appprogress.notes

import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.Recurrence
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.isDone
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** Текст новой ежедневной заметки: события дня и горящие задачи — ссылками, плюс место для мыслей. */
object DailyNote {
    suspend fun build(date: LocalDate, calendar: CalendarRepository, kanban: KanbanRepository): String {
        val events = Recurrence.occurrences(calendar.all(), date, date)
        val tasks = kanban.observeBoards().first().flatMap { b ->
            kanban.observeBoard(b.id).first().filter { !it.isDone() }.flatMap { col -> col.cards }
        }.filter { c -> c.dueDate?.let { it <= date } == true }.sortedBy { it.dueDate }
        return buildString {
            appendLine("# ${RuText.dayMonthWeekday(date).replaceFirstChar { it.uppercase() }}")
            appendLine()
            appendLine("## События")
            if (events.isEmpty()) appendLine("- нет") else events.forEach { o ->
                val time = o.event.startTime?.let { RuText.time(it) + " " }.orEmpty()
                appendLine("- $time${NoteMarkup.appLink(LinkKind.Event, o.event.id, o.event.title, o.date.toString())}")
            }
            appendLine()
            appendLine("## Задачи")
            if (tasks.isEmpty()) appendLine("- нет горящих задач") else tasks.forEach { c ->
                val due = c.dueDate?.let { if (it < date) " — просрочено с ${Dates.format(it)}" else "" }.orEmpty()
                appendLine("- [ ] ${NoteMarkup.appLink(LinkKind.Card, c.id, c.title)}$due")
            }
            appendLine()
            appendLine("## Мысли и заметки")
            appendLine()
        }
    }
}
