package com.appprogress.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Даты в привычном виде «18.10.2026». В базе храним ISO «2026-10-18» (LocalDate.toString). */
object Dates {
    fun format(date: LocalDate): String =
        "${date.day.pad()}.${date.month.number.pad()}.${date.year}"

    /** Принимает «18.10.2026» и «18.10.26». Возвращает null, если дата неверная (например, 31.02). */
    fun parse(text: String): LocalDate? {
        val parts = text.trim().split('.', '/', '-').map { it.trim() }
        if (parts.size != 3) return null
        val (d, m, y) = parts.map { it.toIntOrNull() ?: return null }
        val year = if (y < 100) 2000 + y else y
        return runCatching { LocalDate(year, m, d) }.getOrNull()
    }

    fun parseIso(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    @OptIn(ExperimentalTime::class)
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun Int.pad() = toString().padStart(2, '0')
}
