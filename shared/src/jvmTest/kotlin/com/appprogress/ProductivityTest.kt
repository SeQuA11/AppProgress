package com.appprogress

import com.appprogress.calendar.Repeat
import com.appprogress.core.Backups
import com.appprogress.core.DatabaseFactory
import com.appprogress.focus.FocusTimer
import com.appprogress.focus.TimeRepository
import com.appprogress.kanban.CardEdit
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.Priority
import com.appprogress.kanban.TaskReminders
import com.appprogress.kanban.cardRefs
import com.appprogress.kanban.nextDue
import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductivityTest {
    private val db = DatabaseFactory.inMemory()
    private val kanban = KanbanRepository(db, Dispatchers.IO)
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun nextDueClampsMonthEndsAndLeapDays() {
        assertEquals(d(2026, 11, 30), nextDue(d(2026, 10, 31), Repeat.Monthly))
        assertEquals(d(2027, 2, 28), nextDue(d(2027, 1, 31), Repeat.Monthly))
        assertEquals(d(2025, 2, 28), nextDue(d(2024, 2, 29), Repeat.Yearly))
        assertEquals(d(2026, 10, 25), nextDue(d(2026, 10, 18), Repeat.Weekly))
    }

    @Test
    fun repeatingTaskSpawnsNextCopyWhenDone() = runBlocking {
        val b = kanban.createBoard("Дом")
        val (todo, _, done) = kanban.observeBoard(b).first().map { it.id }
        val weekly = kanban.addCard(b, todo, "Вынести мусор")
        kanban.updateCard(weekly, CardEdit("Вынести мусор", "", Priority.Low, d(2026, 10, 18), listOf("дом"), Repeat.Weekly))
        val once = kanban.addCard(b, todo, "Разовая")

        kanban.moveCard(weekly, done, 0)
        kanban.moveCard(once, done, 0)

        val todoCards = kanban.observeBoard(b).first()[0].cards
        val next = todoCards.single()
        assertEquals("Вынести мусор", next.title)
        assertEquals(d(2026, 10, 25), next.dueDate)
        assertEquals(Repeat.Weekly, next.repeat)
        assertEquals(listOf("дом"), next.labels)
        assertEquals(2, kanban.observeBoard(b).first()[2].cards.size)
    }

    @Test
    fun taskRemindersAtNineOncePerDay() = runBlocking {
        val b = kanban.createBoard("Работа")
        val (todo, _, done) = kanban.observeBoard(b).first().map { it.id }
        suspend fun card(title: String, due: LocalDate, col: String = todo) =
            kanban.addCard(b, col, title).also { kanban.updateCard(it, CardEdit(title, "", Priority.None, due, emptyList())) }
        card("Отчёт", d(2026, 10, 20))
        card("Счёт", d(2026, 10, 15))
        card("Старое сделанное", d(2026, 10, 1), done)
        val reminders = TaskReminders(db, kanban, Dispatchers.IO)

        assertTrue(reminders.takeDue(LocalDateTime(2026, 10, 20, 8, 59)).isEmpty())
        val at9 = reminders.takeDue(LocalDateTime(2026, 10, 20, 9, 0))
        assertEquals(listOf("Срок сегодня: Отчёт", "Просрочено задач: 1"), at9.map { it.title })
        assertEquals("«Счёт»", at9[1].text)
        assertTrue(reminders.takeDue(LocalDateTime(2026, 10, 20, 15, 0)).isEmpty(), "второй раз за день не напоминаем")
        assertEquals(1, reminders.takeDue(LocalDateTime(2026, 10, 21, 9, 30)).size, "на следующий день — снова о просроченных")
    }

    @Test
    fun focusTimerRecordsTimeAndNotifies() = runBlocking {
        val time = TimeRepository(db, Dispatchers.IO)
        val notes = CopyOnWriteArrayList<String>()
        val timer = FocusTimer(time, CoroutineScope(SupervisorJob() + Dispatchers.Default), { t, _ -> notes += t })
        timer.startFocus("Отчёт", "card-1", durationMs = 80, breakMs = 80)
        assertTrue(timer.state.value is FocusTimer.State.Running)
        Thread.sleep(500)
        assertEquals(FocusTimer.State.Idle, timer.state.value)
        assertEquals(listOf("Фокус окончен: Отчёт", "Перерыв окончен"), notes.toList())
        val entries = time.between(0, Long.MAX_VALUE)
        assertEquals(listOf("card-1"), entries.map { it.cardId })
    }

    @Test
    fun stoppingFocusEarlyKeepsOnlyMeaningfulTime() = runBlocking {
        var now = 0L
        val time = TimeRepository(db, Dispatchers.IO)
        val timer = FocusTimer(time, CoroutineScope(SupervisorJob() + Dispatchers.Default), { _, _ -> }, clock = { now })
        timer.startFocus("Короткий")
        now += 30_000
        timer.stop()
        timer.startFocus("Длинный")
        now += 10 * 60_000
        timer.stop()
        Thread.sleep(200)
        assertEquals(listOf("Длинный"), time.between(0, Long.MAX_VALUE).map { it.title })
    }

    @Test
    fun backupAndRestoreOnNextStart() = runBlocking {
        val dir = Files.createTempDirectory("backup").toFile().apply { deleteOnExit() }
        val dbFile = File(dir, "appprogress.db")
        val notes = NotesRepository(DatabaseFactory.open(dbFile), Dispatchers.IO)
        notes.create("До копии", "")
        val backups = Backups(dir, dbFile)
        val copy = backups.create()
        notes.create("После копии", "")

        assertNull(backups.dailyIfNeeded(), "копия свежая — ежедневная не нужна")
        backups.scheduleRestore(copy)
        val restored = NotesRepository(DatabaseFactory.open(dbFile), Dispatchers.IO)
        assertEquals(listOf("До копии"), restored.observeAll().first().map { it.title })
        assertTrue(backups.list().any { it.name.startsWith("before-restore-") }, "текущая база сохранена перед откатом")
        assertNull(backups.pendingRestore())
    }

    @Test
    fun pruneKeepsNewest() {
        val dir = Files.createTempDirectory("prune").toFile().apply { deleteOnExit() }
        val backups = Backups(dir, File(dir, "appprogress.db"))
        backups.dir.mkdirs()
        repeat(20) { i -> File(backups.dir, "appprogress-$i.db").apply { writeText("x"); setLastModified(1_000_000L * (i + 1)) } }
        backups.prune(keep = 5)
        assertEquals((15..19).map { "appprogress-$it.db" }.reversed(), backups.list().map { it.name })
    }

    /** У пользователя уже есть таблица card без repeat_rule — при запуске колонка добавляется, данные целы. */
    @Test
    fun upgradesKanbanTableWithoutRepeatColumn() = runBlocking<Unit> {
        val file = File.createTempFile("oldkanban", ".db").apply { delete(); deleteOnExit() }
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.execute("CREATE TABLE board (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, position REAL NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)")
                st.execute("CREATE TABLE board_column (id TEXT NOT NULL PRIMARY KEY, board_id TEXT NOT NULL, title TEXT NOT NULL, position REAL NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)")
                st.execute("CREATE TABLE card (id TEXT NOT NULL PRIMARY KEY, board_id TEXT NOT NULL, column_id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', priority INTEGER NOT NULL DEFAULT 0, due_date TEXT, labels TEXT NOT NULL DEFAULT '', position REAL NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)")
                st.execute("INSERT INTO board VALUES ('b', 'Старая доска', 1, 1, 1, NULL)")
                st.execute("INSERT INTO board_column VALUES ('c', 'b', 'Нужно сделать', 1, 1, 1, NULL)")
                st.execute("INSERT INTO card VALUES ('k', 'b', 'c', 'Старая задача', '', 0, NULL, '', 1, 1, 1, NULL)")
            }
        }
        val repo = KanbanRepository(DatabaseFactory.open(file), Dispatchers.IO)
        val card = repo.cardRefs().single().card
        assertEquals("Старая задача", card.title)
        assertEquals(Repeat.None, card.repeat)
        assertNotNull(repo.observeBoards().first().single())
    }
}
