package com.appprogress.kanban

import kotlinx.datetime.LocalDate

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
