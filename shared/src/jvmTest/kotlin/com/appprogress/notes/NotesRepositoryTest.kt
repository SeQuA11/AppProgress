package com.appprogress.notes

import com.appprogress.core.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesRepositoryTest {
    private var time = 1_000L
    private val repo = NotesRepository(DatabaseFactory.inMemory(), Dispatchers.IO) { time++ }

    @Test
    fun createUpdateAndRead() = runBlocking {
        val id = repo.create()
        repo.updateText(id, "Покупки", "Молоко\nХлеб")

        val note = repo.get(id)!!
        assertEquals("Покупки", note.title)
        assertEquals("Молоко\nХлеб", note.content)
        assertTrue(note.updatedAt > note.createdAt)
    }

    @Test
    fun deleteHidesNoteFromList() = runBlocking {
        val keep = repo.create("Оставить")
        val gone = repo.create("Удалить")
        repo.delete(gone)

        val ids = repo.observeAll().first().map { it.id }
        assertEquals(listOf(keep), ids)
    }

    @Test
    fun pinnedNotesGoFirst() = runBlocking {
        val first = repo.create("Старая")
        repo.create("Новая")
        repo.setPinned(first, true)

        assertEquals("Старая", repo.observeAll().first().first().title)
    }

    @Test
    fun searchIgnoresCaseForCyrillic() {
        val note = Note("1", "Заметка", "Текст", false, 0, 0)
        assertTrue(note.matches("заметка"))
        assertTrue(note.matches("ТЕКСТ"))
        assertFalse(note.matches("канбан"))
    }

    @Test
    fun displayTitleFallsBackToFirstLine() {
        assertEquals("Первая строка", Note("1", "", "\nПервая строка\nвторая", false, 0, 0).displayTitle)
        assertEquals("Без названия", Note("1", "", "", false, 0, 0).displayTitle)
    }

    @Test
    fun missingNoteReturnsNull() = runBlocking {
        assertNull(repo.get("нет-такой"))
    }
}
