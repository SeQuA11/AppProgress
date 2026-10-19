package com.appprogress.kanban

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.appprogress.calendar.Repeat
import com.appprogress.core.Dates
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class KanbanRepository(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = ::nowMillis,
) {
    private val q get() = db.kanbanQueries

    fun observeBoards(): Flow<List<Board>> =
        q.selectBoards { id, title, _ -> Board(id, title) }.asFlow().mapToList(io)

    /** Колонки доски вместе с карточками — одним потоком, обновляется при любом изменении. */
    fun observeBoard(boardId: String): Flow<List<BoardColumn>> {
        val columns = q.selectColumns(boardId) { id, bId, title, _ -> Triple(id, bId, title) }.asFlow().mapToList(io)
        val cards = q.selectCards(boardId, ::toCard).asFlow().mapToList(io)
        return combine(columns, cards) { cols, allCards ->
            val byColumn = allCards.groupBy { it.columnId }
            cols.map { (id, bId, title) -> BoardColumn(id, bId, title, byColumn[id].orEmpty()) }
        }
    }

    suspend fun allCards(): List<Card> = withContext(io) { q.selectAllCards(::toCard).executeAsList() }

    /** Новая доска сразу с тремя привычными колонками. */
    suspend fun createBoard(
        title: String,
        columns: List<String> = listOf("Нужно сделать", "В работе", "Готово"),
    ): String = withContext(io) {
        val id = newId()
        val now = clock()
        db.transaction {
            val last = q.selectBoards { _, _, position -> position }.executeAsList().maxOrNull()
            q.insertBoard(id, title.trim().ifEmpty { "Новая доска" }, Positions.between(last, null), now, now)
            columns.forEachIndexed { i, name -> q.insertColumn(newId(), id, name, i + 1.0, now, now) }
        }
        id
    }

    suspend fun renameBoard(id: String, title: String) = withContext(io) {
        q.renameBoard(title.trim().ifEmpty { "Без названия" }, clock(), id)
    }

    suspend fun deleteBoard(id: String) = withContext(io) {
        val now = clock()
        db.transaction {
            q.softDeleteCardsOfBoard(now, now, id)
            q.softDeleteColumnsOfBoard(now, now, id)
            q.softDeleteBoard(now, now, id)
        }
    }

    suspend fun addColumn(boardId: String, title: String): String = withContext(io) {
        val id = newId()
        val now = clock()
        val last = q.selectColumns(boardId) { _, _, _, position -> position }.executeAsList().maxOrNull()
        q.insertColumn(id, boardId, title.trim().ifEmpty { "Новая колонка" }, Positions.between(last, null), now, now)
        id
    }

    suspend fun renameColumn(id: String, title: String) = withContext(io) {
        q.renameColumn(title.trim().ifEmpty { "Без названия" }, clock(), id)
    }

    suspend fun deleteColumn(id: String) = withContext(io) {
        val now = clock()
        db.transaction {
            q.softDeleteCardsOfColumn(now, now, id)
            q.softDeleteColumn(now, now, id)
        }
    }

    /** Карточка добавляется в конец колонки. */
    suspend fun addCard(boardId: String, columnId: String, title: String): String = withContext(io) {
        val id = newId()
        val now = clock()
        val last = q.selectCardsInColumn(columnId, ::toCard).executeAsList().lastOrNull()?.position
        q.insertCard(id, boardId, columnId, title.trim(), Positions.between(last, null), now, now)
        id
    }

    suspend fun updateCard(id: String, edit: CardEdit) = withContext(io) {
        q.updateCard(
            title = edit.title.trim().ifEmpty { "Без названия" },
            description = edit.description,
            priority = edit.priority.code,
            due_date = edit.dueDate?.toString(),
            labels = edit.labels.joinToString(","),
            repeat_rule = edit.repeat.code,
            updated_at = clock(),
            id = id,
        )
    }

    /**
     * Переносит карточку в колонку [toColumnId] так, чтобы она оказалась на месте [toIndex]
     * среди остальных карточек этой колонки (сама карточка не считается).
     */
    suspend fun moveCard(cardId: String, toColumnId: String, toIndex: Int) = withContext(io) {
        db.transaction {
            var others = q.selectCardsInColumn(toColumnId, ::toCard).executeAsList().filter { it.id != cardId }
            val index = toIndex.coerceIn(0, others.size)
            var before = others.getOrNull(index - 1)?.position
            var after = others.getOrNull(index)?.position
            if (Positions.needsRebalance(before, after)) {
                val now = clock()
                others.forEachIndexed { i, c -> q.moveCard(toColumnId, i + 1.0, now, c.id) }
                others = others.mapIndexed { i, c -> c.copy(position = i + 1.0) }
                before = others.getOrNull(index - 1)?.position
                after = others.getOrNull(index)?.position
            }
            q.moveCard(toColumnId, Positions.between(before, after), clock(), cardId)
            spawnNextIfRepeating(cardId, toColumnId)
        }
    }

    /**
     * Повторяющаяся задача попала в «Готово» — создаём следующую в первой колонке со сдвинутым сроком.
     * Вызывается внутри транзакции moveCard.
     */
    private fun spawnNextIfRepeating(cardId: String, toColumnId: String) {
        val card = q.selectCardById(cardId, ::toCard).executeAsOneOrNull() ?: return
        if (card.repeat == Repeat.None) return
        val columns = q.selectColumns(card.boardId) { id, bId, title, _ -> BoardColumn(id, bId, title, emptyList()) }.executeAsList()
        val target = columns.firstOrNull { it.id == toColumnId } ?: return
        if (!target.isDone()) return
        val first = columns.firstOrNull { !it.isDone() } ?: return
        val now = clock()
        val id = newId()
        val last = q.selectCardsInColumn(first.id, ::toCard).executeAsList().lastOrNull()?.position
        q.insertCard(id, card.boardId, first.id, card.title, Positions.between(last, null), now, now)
        val due = nextDue(card.dueDate ?: Dates.today(), card.repeat)
        q.updateCard(card.title, card.description, card.priority.code, due.toString(), card.labels.joinToString(","), card.repeat.code, now, id)
    }

    suspend fun deleteCard(id: String) = withContext(io) {
        val now = clock()
        q.softDeleteCard(now, now, id)
    }

    private fun toCard(
        id: String, boardId: String, columnId: String, title: String, description: String,
        priority: Long, dueDate: String?, labels: String, position: Double, repeat: String,
    ) = Card(id, boardId, columnId, title, description, Priority.of(priority), Dates.parseIso(dueDate), parseLabels(labels), position, Repeat.of(repeat))
}

/** Все карточки всех досок с указанием доски и колонки. */
suspend fun KanbanRepository.cardRefs(): List<CardRef> =
    observeBoards().first().flatMap { b -> observeBoard(b.id).first().flatMap { col -> col.cards.map { CardRef(b, col, it) } } }
