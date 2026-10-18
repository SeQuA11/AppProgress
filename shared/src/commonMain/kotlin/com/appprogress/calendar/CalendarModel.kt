package com.appprogress.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.number
import kotlinx.datetime.plus

enum class EventKind(val code: String, val title: String) {
    Event("event", "Событие"),
    Birthday("birthday", "День рождения"),
    Important("important", "Важная дата"),
    Note("note", "Заметка на дату");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Event
    }
}

enum class Repeat(val code: String, val title: String) {
    None("none", "Не повторять"),
    Daily("daily", "Каждый день"),
    Weekly("weekly", "Каждую неделю"),
    Monthly("monthly", "Каждый месяц"),
    Yearly("yearly", "Каждый год");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: None
    }
}

data class CalendarEvent(
    val id: String = "",
    val title: String,
    val description: String = "",
    val kind: EventKind = EventKind.Event,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val repeat: Repeat = Repeat.None,
    val repeatUntil: LocalDate? = null,
    val remindMinutes: Int? = null,
) {
    val allDay: Boolean get() = startTime == null

    /** Для дня рождения: сколько лет исполнится в эту дату (если год рождения задан разумно). */
    fun ageOn(occurrence: LocalDate): Int? =
        if (kind == EventKind.Birthday && date.year in 1900 until occurrence.year) occurrence.year - date.year else null
}

/** Конкретное появление события в календаре (для повторяющихся — одна из дат). */
data class Occurrence(val event: CalendarEvent, val date: LocalDate) {
    /** Начало: время события, а для событий «на весь день» — 9:00 (удобное время для напоминания). */
    val start: LocalDateTime get() = LocalDateTime(date, event.startTime ?: ALL_DAY_REMINDER_TIME)

    companion object {
        val ALL_DAY_REMINDER_TIME = LocalTime(9, 0)
    }
}

object Recurrence {

    fun occursOn(event: CalendarEvent, day: LocalDate): Boolean {
        val start = event.date
        if (day < start) return false
        if (event.repeatUntil != null && day > event.repeatUntil) return false
        return when (event.repeat) {
            Repeat.None -> day == start
            Repeat.Daily -> true
            Repeat.Weekly -> day.dayOfWeek == start.dayOfWeek
            // 31-е число в коротком месяце переносим на последний день месяца.
            Repeat.Monthly -> day.day == minOf(start.day, day.lengthOfMonth())
            // 29 февраля в невисокосный год отмечаем 28-го.
            Repeat.Yearly -> day.month == start.month && day.day == minOf(start.day, day.lengthOfMonth())
        }
    }

    /** Все даты события в промежутке [from, to] включительно. */
    fun occurrences(event: CalendarEvent, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to < from) return emptyList()
        val result = mutableListOf<LocalDate>()
        var day = maxOf(from, event.date)
        while (day <= to) {
            if (occursOn(event, day)) result += day
            day = day.plus(DatePeriod(days = 1))
        }
        return result
    }

    fun occurrences(events: List<CalendarEvent>, from: LocalDate, to: LocalDate): List<Occurrence> =
        events.flatMap { e -> occurrences(e, from, to).map { Occurrence(e, it) } }
            .sortedWith(compareBy({ it.date }, { it.event.startTime ?: LocalTime(0, 0) }, { it.event.title }))
}

fun isLeapYear(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

fun LocalDate.lengthOfMonth(): Int = when (month) {
    Month.FEBRUARY -> if (isLeapYear(year)) 29 else 28
    Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
    else -> 31
}

/** Понедельник недели, в которую входит дата (неделя в России начинается с понедельника). */
fun LocalDate.startOfWeek(): LocalDate = plus(DatePeriod(days = -(dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal)))

fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month.number, 1)

object RuText {
    private val monthsNominative = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    private val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    val weekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val weekdays = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")

    fun monthTitle(date: LocalDate) = "${monthsNominative[date.month.number - 1]} ${date.year}"

    /** «18 октября» */
    fun dayMonth(date: LocalDate) = "${date.day} ${monthsGenitive[date.month.number - 1]}"

    /** «18 октября, суббота» */
    fun dayMonthWeekday(date: LocalDate) = "${dayMonth(date)}, ${weekdays[date.dayOfWeek.ordinal]}"

    fun time(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    /** «исполнится 30 лет» с правильным окончанием. */
    fun years(n: Int): String {
        val word = when {
            n % 100 in 11..14 -> "лет"
            n % 10 == 1 -> "год"
            n % 10 in 2..4 -> "года"
            else -> "лет"
        }
        return "$n $word"
    }

    fun parseTime(text: String): LocalTime? {
        val parts = text.trim().split(':', '.')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime(h, m) else null
    }
}
