package com.appprogress.calendar

import com.appprogress.core.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Раз в [periodMs] проверяет, не пора ли напомнить о событиях, и вызывает [notify].
 * Работает, пока запущено приложение (на ПК — в том числе свёрнутое в трей).
 */
class ReminderService(
    private val repo: CalendarRepository,
    private val notify: (title: String, text: String) -> Unit,
    private val periodMs: Long = 30_000,
) {
    @OptIn(ExperimentalTime::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            runCatching { repo.takeDueReminders(now) }.getOrDefault(emptyList()).forEach { occ ->
                notify(occ.event.title, describe(occ))
            }
            delay(periodMs)
        }
    }

    companion object {
        fun describe(occ: Occurrence): String {
            val e = occ.event
            val whenText = buildString {
                append(if (occ.date == Dates.today()) "Сегодня" else RuText.dayMonthWeekday(occ.date))
                e.startTime?.let { append(" в ${RuText.time(it)}") }
            }
            val age = e.ageOn(occ.date)?.let { ", исполнится ${RuText.years(it)}" }.orEmpty()
            return "${e.kind.title}: $whenText$age"
        }
    }
}
