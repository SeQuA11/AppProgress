package com.appprogress.kanban

import com.appprogress.core.DatabaseFactory
import com.appprogress.core.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KanbanRepositoryTest {
    private var time = 1_000L
    private val repo = KanbanRepository(DatabaseFactory.inMemory(), Dispatchers.IO) { time++ }

    private suspend fun board(id: String) = repo.observeBoard(id).first()
    private suspend fun titles(boardId: String) = board(boardId).map { col -> col.title to col.cards.map { it.title } }

    @Test
    fun newBoardHasDefaultColumns() = runBlocking {
        val id = repo.createBoard("Дом")
        assertEquals(listOf("Нужно сделать", "В работе", "Готово"), board(id).map { it.title })
        assertEquals(listOf("Дом"), repo.observeBoards().first().map { it.title })
    }

    @Test
    fun cardsKeepInsertionOrder() = runBlocking {
        val b = repo.createBoard("Работа")
        val todo = board(b)[0].id
        listOf("А", "Б", "В").forEach { repo.addCard(b, todo, it) }
        assertEquals(listOf("А", "Б", "В"), board(b)[0].cards.map { it.title })
    }

    @Test
    fun moveCardToAnotherColumnAtIndex() = runBlocking {
        val b = repo.createBoard("Работа")
        val (todo, doing) = board(b).map { it.id }
        val a = repo.addCard(b, todo, "А")
        repo.addCard(b, doing, "X")
        repo.addCard(b, doing, "Y")

        repo.moveCard(a, doing, 1)
        assertEquals(
            listOf("Нужно сделать" to emptyList(), "В работе" to listOf("X", "А", "Y"), "Готово" to emptyList()),
            titles(b),
        )
    }

    @Test
    fun moveCardWithinColumn() = runBlocking {
        val b = repo.createBoard("Работа")
        val todo = board(b)[0].id
        repo.addCard(b, todo, "1")
        repo.addCard(b, todo, "2")
        val third = repo.addCard(b, todo, "3")

        repo.moveCard(third, todo, 0)
        assertEquals(listOf("3", "1", "2"), board(b)[0].cards.map { it.title })
    }

    @Test
    fun manyMovesIntoSameGapStayOrdered() = runBlocking {
        val b = repo.createBoard("Стресс")
        val todo = board(b)[0].id
        repo.addCard(b, todo, "первая")
        repo.addCard(b, todo, "последняя")
        // 80 вставок в одно и то же место: без перенумерации числа бы «слиплись».
        repeat(80) { i -> repo.moveCard(repo.addCard(b, todo, "n$i"), todo, 1) }

        val cards = board(b)[0].cards
        assertEquals("первая", cards.first().title)
        assertEquals("последняя", cards.last().title)
        assertEquals("n79", cards[1].title)
        assertEquals(cards.size, cards.map { it.position }.distinct().size)
    }

    @Test
    fun updateCardRoundTrip() = runBlocking {
        val b = repo.createBoard("Работа")
        val id = repo.addCard(b, board(b)[0].id, "Черновик")
        val due = LocalDate(2026, 10, 25)
        repo.updateCard(id, CardEdit("Отчёт", "Собрать цифры", Priority.High, due, listOf("работа", "срочно")))

        val card = board(b)[0].cards.single()
        assertEquals("Отчёт", card.title)
        assertEquals(Priority.High, card.priority)
        assertEquals(due, card.dueDate)
        assertEquals(listOf("работа", "срочно"), card.labels)
        assertTrue(card.isOverdue(LocalDate(2026, 10, 26)))
    }

    @Test
    fun deleteColumnHidesItsCards() = runBlocking {
        val b = repo.createBoard("Работа")
        val todo = board(b)[0].id
        repo.addCard(b, todo, "А")
        repo.deleteColumn(todo)

        assertEquals(listOf("В работе", "Готово"), board(b).map { it.title })
        assertTrue(repo.allCards().isEmpty())
    }

    @Test
    fun deleteBoard() = runBlocking {
        val b = repo.createBoard("Удалить")
        repo.addCard(b, board(b)[0].id, "А")
        repo.deleteBoard(b)
        assertTrue(repo.observeBoards().first().isEmpty())
        assertTrue(repo.allCards().isEmpty())
    }

    @Test
    fun datesParseAndFormat() {
        assertEquals(LocalDate(2026, 3, 8), Dates.parse("08.03.2026"))
        assertEquals(LocalDate(2026, 3, 8), Dates.parse("8.3.26"))
        assertNull(Dates.parse("31.02.2026"))
        assertNull(Dates.parse("завтра"))
        assertEquals("08.03.2026", Dates.format(LocalDate(2026, 3, 8)))
    }

    @Test
    fun labelsAreTrimmedAndUnique() {
        assertEquals(listOf("дом", "срочно"), parseLabels(" дом, срочно ,, дом"))
    }
}
