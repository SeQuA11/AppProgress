package com.appprogress.desktop

import com.appprogress.calendar.CalendarEvent
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.EventKind
import com.appprogress.calendar.Repeat
import com.appprogress.core.Dates
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.theme.ThemeMode
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlin.test.Test

class CalendarScreenshotTest {
    private val seed: suspend (AppContainer) -> Unit = { c ->
        val repo = CalendarRepository(c.db, c.io)
        val today = Dates.today()
        fun inDays(n: Int) = today.plus(DatePeriod(days = n))
        repo.save(CalendarEvent(title = "Созвон по проекту", date = today, startTime = LocalTime(11, 0), endTime = LocalTime(11, 30), remindMinutes = 15))
        repo.save(CalendarEvent(title = "Спортзал", date = inDays(-6), startTime = LocalTime(19, 0), repeat = Repeat.Weekly))
        repo.save(CalendarEvent(title = "ДР мамы", kind = EventKind.Birthday, date = LocalDate(1968, inDays(4).month.number, inDays(4).day), remindMinutes = 24 * 60))
        repo.save(CalendarEvent(title = "Оплатить интернет", kind = EventKind.Important, date = inDays(9), remindMinutes = 24 * 60))
        repo.save(CalendarEvent(title = "Идеи для отпуска", kind = EventKind.Note, date = inDays(12)))
        repo.save(CalendarEvent(title = "Врач", date = inDays(2), startTime = LocalTime(9, 30), remindMinutes = 60))
    }

    @Test fun calendarLight() = Screenshots.shoot("calendar-light", Section.Calendar, seed = seed)
    @Test fun calendarDark() = Screenshots.shoot("calendar-dark", Section.Calendar, ThemeMode.Dark, seed = seed)
}
