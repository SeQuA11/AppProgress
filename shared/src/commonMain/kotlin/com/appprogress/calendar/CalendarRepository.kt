package com.appprogress.calendar

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.appprogress.core.Dates
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class CalendarRepository(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = ::nowMillis,
) {
    private val q get() = db.calendarQueries

    fun observeEvents(): Flow<List<CalendarEvent>> = q.selectAll(::toEvent).asFlow().mapToList(io)

    suspend fun all(): List<CalendarEvent> = withContext(io) { q.selectAll(::toEvent).executeAsList() }

    suspend fun save(event: CalendarEvent): String = withContext(io) {
        val now = clock()
        val e = event.normalized()
        if (e.id.isEmpty()) {
            val id = newId()
            q.insert(
                id, e.title, e.description, e.kind.code, e.date.toString(), e.startTime?.let(RuText::time),
                e.endTime?.let(RuText::time), e.repeat.code, e.repeatUntil?.toString(), e.remindMinutes?.toLong(), now, now,
            )
            id
        } else {
            db.transaction {
                q.update(
                    e.title, e.description, e.kind.code, e.date.toString(), e.startTime?.let(RuText::time),
                    e.endTime?.let(RuText::time), e.repeat.code, e.repeatUntil?.toString(), e.remindMinutes?.toLong(), now, e.id,
                )
                // Дату или время могли поменять — напоминания нужно показать заново.
                q.forgetSent(e.id)
            }
            e.id
        }
    }

    suspend fun delete(id: String) = withContext(io) {
        val now = clock()
        q.softDelete(now, now, id)
    }

    /**
     * Напоминания, которые пора показать в момент [now]. Каждое (событие, дата) возвращается один раз —
     * повторный вызов его уже не вернёт. Пропущенные, пока приложение было закрыто, показываем,
     * если событие началось не больше [missedWindowHours] часов назад.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun takeDueReminders(now: LocalDateTime, missedWindowHours: Int = 12): List<Occurrence> = withContext(io) {
        val tz = TimeZone.UTC // только для арифметики над локальным временем, без перевода поясов
        val nowInstant = now.toInstant(tz)
        val events = q.selectAll(::toEvent).executeAsList().filter { it.remindMinutes != null }
        val maxLead = events.maxOfOrNull { it.remindMinutes!! } ?: return@withContext emptyList()
        val from = now.date.minus(DatePeriod(days = 1))
        val to = now.date.plus(DatePeriod(days = maxLead / (24 * 60) + 1))

        Recurrence.occurrences(events, from, to).filter { occ ->
            val startInstant = occ.start.toInstant(tz)
            val fireAt = startInstant - occ.event.remindMinutes!!.minutes
            fireAt <= nowInstant && startInstant >= nowInstant - missedWindowHours.hours
        }.filter { occ ->
            val key = occ.date.toString()
            val sent = q.isSent(occ.event.id, key).executeAsOne() > 0
            if (!sent) q.markSent(occ.event.id, key, clock())
            !sent
        }
    }

    private fun toEvent(
        id: String, title: String, description: String, kind: String, startDate: String, startTime: String?,
        endTime: String?, repeat: String, repeatUntil: String?, remindMinutes: Long?,
    ) = CalendarEvent(
        id = id,
        title = title,
        description = description,
        kind = EventKind.of(kind),
        date = Dates.parseIso(startDate) ?: Dates.today(),
        startTime = startTime?.let(RuText::parseTime),
        endTime = endTime?.let(RuText::parseTime),
        repeat = Repeat.of(repeat),
        repeatUntil = Dates.parseIso(repeatUntil),
        remindMinutes = remindMinutes?.toInt(),
    )
}

/** День рождения всегда повторяется каждый год; конец не раньше начала. */
private fun CalendarEvent.normalized(): CalendarEvent {
    val repeat = if (kind == EventKind.Birthday) Repeat.Yearly else repeat
    val end = endTime?.takeIf { startTime != null && it > startTime }
    return copy(title = title.trim().ifEmpty { "Без названия" }, repeat = repeat, endTime = end)
}
