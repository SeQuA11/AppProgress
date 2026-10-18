package com.appprogress.calendar

import com.appprogress.core.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarTest {
    private val repo = CalendarRepository(DatabaseFactory.inMemory(), Dispatchers.IO)

    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun yearlyBirthdayOnFeb29FallsOnFeb28InNonLeapYears() {
        val bd = CalendarEvent(title = "ДР", kind = EventKind.Birthday, date = d(2000, 2, 29), repeat = Repeat.Yearly)
        assertTrue(Recurrence.occursOn(bd, d(2024, 2, 29)))
        assertTrue(Recurrence.occursOn(bd, d(2026, 2, 28)))
        assertFalse(Recurrence.occursOn(bd, d(2024, 2, 28)))
        assertEquals(26, bd.ageOn(d(2026, 2, 28)))
    }

    @Test
    fun monthlyOn31stUsesLastDayOfShortMonths() {
        val e = CalendarEvent(title = "Отчёт", date = d(2026, 1, 31), repeat = Repeat.Monthly)
        val dates = Recurrence.occurrences(e, d(2026, 1, 1), d(2026, 5, 31))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31)), dates)
    }

    @Test
    fun weeklyRespectsRepeatUntil() {
        val e = CalendarEvent(title = "Спорт", date = d(2026, 10, 5), repeat = Repeat.Weekly, repeatUntil = d(2026, 10, 20))
        assertEquals(listOf(d(2026, 10, 5), d(2026, 10, 12), d(2026, 10, 19)), Recurrence.occurrences(e, d(2026, 10, 1), d(2026, 10, 31)))
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(d(2026, 10, 12), d(2026, 10, 18).startOfWeek()) // воскресенье → понедельник той же недели
        assertEquals(d(2026, 10, 12), d(2026, 10, 12).startOfWeek())
    }

    @Test
    fun russianTexts() {
        assertEquals("Октябрь 2026", RuText.monthTitle(d(2026, 10, 18)))
        assertEquals("18 октября, воскресенье", RuText.dayMonthWeekday(d(2026, 10, 18)))
        assertEquals("21 год", RuText.years(21))
        assertEquals("12 лет", RuText.years(12))
        assertEquals("33 года", RuText.years(33))
        assertEquals(LocalTime(9, 5), RuText.parseTime("9:05"))
        assertNull(RuText.parseTime("25:00"))
    }

    @Test
    fun saveNormalizesBirthdayToYearly() = runBlocking {
        repo.save(CalendarEvent(title = "  Мама ", kind = EventKind.Birthday, date = d(1970, 3, 12)))
        val saved = repo.observeEvents().first().single()
        assertEquals("Мама", saved.title)
        assertEquals(Repeat.Yearly, saved.repeat)
    }

    @Test
    fun reminderFiresOnceAtTheRightTime() = runBlocking {
        repo.save(CalendarEvent(title = "Встреча", date = d(2026, 10, 20), startTime = LocalTime(15, 0), remindMinutes = 60))

        assertTrue(repo.takeDueReminders(LocalDateTime(2026, 10, 20, 13, 59)).isEmpty(), "рано")
        assertEquals(listOf("Встреча"), repo.takeDueReminders(LocalDateTime(2026, 10, 20, 14, 0)).map { it.event.title })
        assertTrue(repo.takeDueReminders(LocalDateTime(2026, 10, 20, 14, 1)).isEmpty(), "второй раз не напоминаем")
    }

    @Test
    fun allDayBirthdayReminderDayBeforeAt9() = runBlocking {
        repo.save(CalendarEvent(title = "ДР Саши", kind = EventKind.Birthday, date = d(1995, 10, 25), remindMinutes = 24 * 60))

        assertTrue(repo.takeDueReminders(LocalDateTime(2026, 10, 24, 8, 59)).isEmpty())
        val due = repo.takeDueReminders(LocalDateTime(2026, 10, 24, 9, 0)).single()
        assertEquals(d(2026, 10, 25), due.date)
        assertEquals("День рождения: 25 октября, воскресенье, исполнится 31 год", ReminderService.describe(due))
    }

    @Test
    fun missedReminderShownAfterStartButNotTooLate() = runBlocking {
        repo.save(CalendarEvent(title = "Звонок", date = d(2026, 10, 20), startTime = LocalTime(10, 0), remindMinutes = 0))
        // Приложение было закрыто в 10:00, открыли в 13:00 — напомним (окно 12 часов).
        assertEquals(1, repo.takeDueReminders(LocalDateTime(2026, 10, 20, 13, 0)).size)

        repo.save(CalendarEvent(title = "Старое", date = d(2026, 10, 18), startTime = LocalTime(10, 0), remindMinutes = 0))
        assertTrue(repo.takeDueReminders(LocalDateTime(2026, 10, 20, 13, 0)).isEmpty(), "два дня назад — уже не актуально")
    }

    @Test
    fun editingEventResetsSentReminder() = runBlocking {
        val id = repo.save(CalendarEvent(title = "Врач", date = d(2026, 10, 20), startTime = LocalTime(10, 0), remindMinutes = 30))
        assertEquals(1, repo.takeDueReminders(LocalDateTime(2026, 10, 20, 9, 30)).size)

        val event = repo.all().single()
        repo.save(event.copy(id = id, startTime = LocalTime(11, 0)))
        assertEquals(1, repo.takeDueReminders(LocalDateTime(2026, 10, 20, 10, 30)).size)
    }
}
