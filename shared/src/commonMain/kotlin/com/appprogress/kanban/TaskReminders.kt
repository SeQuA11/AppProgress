package com.appprogress.kanban

import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Напоминания о сроках задач канбана. В 9:00 (или при первом запуске после 9:00):
 *  - по каждой задаче со сроком сегодня — отдельное уведомление;
 *  - одно общее уведомление о просроченных задачах.
 * Каждое показывается один раз в день — отметки хранятся в той же таблице reminder_sent, что у календаря.
 */
class TaskReminders(
    private val db: AppDatabase,
    private val kanban: KanbanRepository,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = ::nowMillis,
) {
    data class Reminder(val title: String, val text: String)

    suspend fun takeDue(now: LocalDateTime): List<Reminder> {
        if (now.time < MORNING) return emptyList()
        val today = now.date
        val open = kanban.cardRefs().filter { !it.column.isDone() && it.card.dueDate != null }
        val out = mutableListOf<Reminder>()
        withContext(io) {
            val q = db.calendarQueries
            fun once(key: String): Boolean {
                if (q.isSent(key, today.toString()).executeAsOne() > 0) return false
                q.markSent(key, today.toString(), clock())
                return true
            }
            open.filter { it.card.dueDate == today }.forEach { r ->
                if (once("card:${r.card.id}")) out += Reminder("Срок сегодня: ${r.card.title}", "${r.board.title} · ${r.column.title}")
            }
            val overdue = open.filter { it.card.dueDate!! < today }
            if (overdue.isNotEmpty() && once("overdue")) {
                val names = overdue.take(3).joinToString(", ") { "«${it.card.title}»" }
                out += Reminder("Просрочено задач: ${overdue.size}", names + if (overdue.size > 3) " и ещё ${overdue.size - 3}" else "")
            }
        }
        return out
    }

    companion object {
        val MORNING = LocalTime(9, 0)
    }
}
