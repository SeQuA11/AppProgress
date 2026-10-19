package com.appprogress.kanban

import com.appprogress.calendar.Repeat
import com.appprogress.calendar.lengthOfMonth
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus


data class Board(val id: String, val title: String)

data class BoardColumn(val id: String, val boardId: String, val title: String, val cards: List<Card>)

data class Card(
    val id: String,
    val boardId: String,
    val columnId: String,
    val title: String,
    val description: String = "",
    val priority: Priority = Priority.None,
    val dueDate: LocalDate? = null,
    val labels: List<String> = emptyList(),
    val position: Double = 0.0,
    val repeat: Repeat = Repeat.None,
) {
    fun isOverdue(today: LocalDate): Boolean = dueDate != null && dueDate < today
}

enum class Priority(val code: Long, val title: String) {
    None(0, "Без приоритета"),
    Low(1, "Низкий"),
    Medium(2, "Средний"),
    High(3, "Высокий");

    companion object {
        fun of(code: Long) = entries.firstOrNull { it.code == code } ?: None
    }
}

/** Изменяемые пользователем поля карточки (для формы редактирования). */
data class CardEdit(
    val title: String,
    val description: String,
    val priority: Priority,
    val dueDate: LocalDate?,
    val labels: List<String>,
    val repeat: Repeat = Repeat.None,
)

object Positions {
    /** Позиция для вставки между соседями. null — соседа с этой стороны нет. */
    fun between(before: Double?, after: Double?): Double = when {
        before == null && after == null -> 1.0
        before == null -> after!! - 1.0
        after == null -> before + 1.0
        else -> (before + after) / 2.0
    }

    /** После многих вставок в одно место числа становятся слишком близкими — тогда перенумеровываем колонку. */
    fun needsRebalance(before: Double?, after: Double?): Boolean =
        before != null && after != null && after - before < 1e-9
}

fun parseLabels(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Колонка считается «завершённой», если называется «Готово», «Сделано» или «Done». */
fun BoardColumn.isDone(): Boolean = title.lowercase().let { "готов" in it || "сделан" in it || "done" in it }

/** Карточка вместе с доской и колонкой, где она лежит. */
data class CardRef(val board: Board, val column: BoardColumn, val card: Card)

/** Следующий срок повторяющейся задачи: 31-е в коротком месяце — последний день, 29.02 — 28.02. */
fun nextDue(from: LocalDate, repeat: Repeat): LocalDate = when (repeat) {
    Repeat.None -> from
    Repeat.Daily -> from.plus(DatePeriod(days = 1))
    Repeat.Weekly -> from.plus(DatePeriod(days = 7))
    Repeat.Monthly -> {
        val firstNext = LocalDate(from.year, from.month.number, 1).plus(DatePeriod(months = 1))
        LocalDate(firstNext.year, firstNext.month.number, minOf(from.day, firstNext.lengthOfMonth()))
    }
    Repeat.Yearly -> {
        val firstNext = LocalDate(from.year + 1, from.month.number, 1)
        LocalDate(firstNext.year, firstNext.month.number, minOf(from.day, firstNext.lengthOfMonth()))
    }
}
