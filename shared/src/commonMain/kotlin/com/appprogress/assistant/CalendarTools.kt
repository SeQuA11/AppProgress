package com.appprogress.assistant

import com.appprogress.calendar.CalendarEvent
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.EventKind
import com.appprogress.calendar.Occurrence
import com.appprogress.calendar.Recurrence
import com.appprogress.calendar.Repeat
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonObject

fun Occurrence.line(): String = buildString {
    append("- ${Dates.format(date)} (${RuText.weekday(date)})")
    event.startTime?.let { append(" ${RuText.time(it)}") }
    event.endTime?.let { append("–${RuText.time(it)}") }
    append(" «${event.title}»")
    if (event.kind != EventKind.Event) append(" [${event.kind.title.lowercase()}]")
    event.ageOn(date)?.let { append(", исполнится ${RuText.years(it)}") }
    if (event.description.isNotBlank()) append(": ${event.description.replace('\n', ' ').take(120)}")
}

/** «30» → 30 минут; принимаем и слова: «за час», «за день». */
fun parseRemind(text: String): Int? {
    val t = text.trim().lowercase()
    if (t.isEmpty() || t == "нет" || t == "none") return null
    t.toIntOrNull()?.let { return it.coerceAtLeast(0) }
    return when {
        "недел" in t -> 7 * 24 * 60
        "день" in t || "дня" in t || "сутк" in t -> 24 * 60
        "час" in t -> 60
        "15" in t -> 15
        "начал" in t || "момент" in t -> 0
        else -> null
    }
}

private fun parseKind(text: String) = when (text.trim().lowercase()) {
    "birthday", "день рождения", "др" -> EventKind.Birthday
    "important", "важная дата", "важное" -> EventKind.Important
    "note", "заметка" -> EventKind.Note
    else -> EventKind.Event
}

private fun parseRepeat(text: String) = when (text.trim().lowercase()) {
    "daily", "каждый день" -> Repeat.Daily
    "weekly", "каждую неделю" -> Repeat.Weekly
    "monthly", "каждый месяц" -> Repeat.Monthly
    "yearly", "каждый год" -> Repeat.Yearly
    else -> Repeat.None
}

fun calendarTools(repo: CalendarRepository): List<AssistantTool> = listOf(
    object : AssistantTool {
        override val spec = ToolSpec(
            "list_events",
            "События календаря (включая дни рождения и повторяющиеся) в промежутке дат. По умолчанию — сегодня и 7 дней вперёд.",
            stringParams("from" to "С даты дд.мм.гггг", "to" to "По дату дд.мм.гггг", required = emptyList()),
        )

        override fun activity(args: JsonObject) = "Смотрю календарь"

        override suspend fun run(args: JsonObject): String {
            val from = Dates.parse(args.str("from")) ?: Dates.today()
            val to = (Dates.parse(args.str("to")) ?: from.plus(DatePeriod(days = 7))).let { if (it < from) from else it }
            // Ограничиваем промежуток годом, чтобы случайный «01.01.1900» не перебирал сотни лет.
            val limitedTo = minOf(to, from.plus(DatePeriod(years = 1)))
            val items = Recurrence.occurrences(repo.all(), from, limitedTo)
            if (items.isEmpty()) return "С ${Dates.format(from)} по ${Dates.format(limitedTo)} событий нет."
            return items.take(40).joinToString("\n") { it.line() }
        }
    },
    object : AssistantTool {
        override val spec = ToolSpec(
            "create_event",
            "Добавляет событие в календарь. kind: event | birthday | important | note. " +
                "time — ЧЧ:ММ, пусто = на весь день. remind_minutes — за сколько минут напомнить (60 = за час, 1440 = за день). " +
                "repeat: none | daily | weekly | monthly | yearly (день рождения всегда yearly).",
            stringParams(
                "title" to "Название",
                "date" to "Дата дд.мм.гггг (для дня рождения можно с годом рождения)",
                "time" to "Время ЧЧ:ММ",
                "kind" to "event | birthday | important | note",
                "remind_minutes" to "За сколько минут напомнить",
                "repeat" to "none | daily | weekly | monthly | yearly",
                "description" to "Заметка к событию",
                required = listOf("title", "date"),
            ),
        )
        override val writes = true

        override suspend fun describe(args: JsonObject): String = buildString {
            val kind = parseKind(args.str("kind"))
            append(if (kind == EventKind.Birthday) "Добавить день рождения" else "Добавить в календарь")
            append(" «${args.str("title")}»")
            Dates.parse(args.str("date"))?.let { append(", ${Dates.format(it)}") } ?: append(", дата не распознана: «${args.str("date")}»")
            RuText.parseTime(args.str("time"))?.let { append(" в ${RuText.time(it)}") }
            parseRemind(args.str("remind_minutes"))?.let { append(", напомнить ${remindText(it)}") }
            parseRepeat(args.str("repeat")).takeIf { it != Repeat.None && kind != EventKind.Birthday }?.let { append(", ${it.title.lowercase()}") }
        }

        override suspend fun run(args: JsonObject): String {
            val date = Dates.parse(args.str("date")) ?: return "Не понял дату «${args.str("date")}». Нужен формат дд.мм.гггг."
            val event = CalendarEvent(
                title = args.str("title"),
                description = args.str("description"),
                kind = parseKind(args.str("kind")),
                date = date,
                startTime = RuText.parseTime(args.str("time")),
                repeat = parseRepeat(args.str("repeat")),
                remindMinutes = parseRemind(args.str("remind_minutes")),
            )
            repo.save(event)
            return "Событие «${event.title}» добавлено на ${Dates.format(date)}."
        }
    },
)

fun remindText(minutes: Int): String = when {
    minutes == 0 -> "в момент начала"
    minutes % (7 * 24 * 60) == 0 -> "за ${minutes / (7 * 24 * 60)} нед."
    minutes % (24 * 60) == 0 -> "за ${minutes / (24 * 60)} дн."
    minutes % 60 == 0 -> "за ${minutes / 60} ч"
    else -> "за $minutes мин"
}
