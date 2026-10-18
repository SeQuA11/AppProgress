package com.appprogress.ui.kanban

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.appprogress.kanban.BoardColumn
import com.appprogress.kanban.Card

/**
 * Перетаскивание карточек. Все координаты — относительно корня окна (boundsInRoot),
 * поэтому колонки и карточки можно сравнивать между собой, даже если доска прокручена.
 */
class DragController {
    data class Dragged(val card: Card, val grab: Offset, val pointer: Offset, val widthPx: Float)

    /** Куда упадёт карточка: колонка и номер места среди остальных карточек этой колонки. */
    data class Target(val columnId: String, val index: Int)

    var dragged by mutableStateOf<Dragged?>(null)
        private set

    /** Левый верхний угол области доски в координатах окна — для рисования «летящей» карточки. */
    var origin = Offset.Zero

    /** Актуальные колонки; обновляются при каждой перерисовке доски. */
    var columns: List<BoardColumn> = emptyList()

    val columnBounds = mutableMapOf<String, Rect>()
    val cardBounds = mutableMapOf<String, Rect>()

    fun start(card: Card, grab: Offset) {
        val bounds = cardBounds[card.id] ?: return
        dragged = Dragged(card, grab, bounds.topLeft + grab, bounds.width)
    }

    fun move(delta: Offset) {
        dragged = dragged?.let { it.copy(pointer = it.pointer + delta) }
    }

    fun cancel() {
        dragged = null
    }

    /** Завершает перетаскивание и возвращает место, куда бросили карточку (или null — мимо колонок). */
    fun finish(): Pair<Card, Target>? {
        val d = dragged ?: return null
        val target = target()
        dragged = null
        return target?.let { d.card to it }
    }

    fun target(): Target? {
        val d = dragged ?: return null
        val column = columns.firstOrNull { col ->
            columnBounds[col.id]?.let { d.pointer.x >= it.left && d.pointer.x <= it.right } == true
        } ?: return null
        val index = column.cards
            .filter { it.id != d.card.id }
            .count { c -> cardBounds[c.id]?.let { it.center.y < d.pointer.y } == true }
        return Target(column.id, index)
    }
}
