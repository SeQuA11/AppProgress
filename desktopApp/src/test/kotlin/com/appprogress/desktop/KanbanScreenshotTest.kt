package com.appprogress.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.appprogress.core.Dates
import com.appprogress.kanban.CardEdit
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.Priority
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class KanbanScreenshotTest {
    private val seed: suspend (AppContainer) -> Unit = { c ->
        val repo = KanbanRepository(c.db, c.io)
        val b = repo.createBoard("Проект AppProgress")
        repo.createBoard("Дом")
        val (todo, doing, done) = repo.observeBoard(b).first().map { it.id }
        val today = Dates.today()
        val calendar = repo.addCard(b, todo, "Календарь с напоминаниями")
        repo.updateCard(calendar, CardEdit("Календарь с напоминаниями", "Дни рождения, повторы, уведомления в трее", Priority.High, today.plus(DatePeriod(days = 5)), listOf("модуль")))
        val mail = repo.addCard(b, todo, "Почта: сортировка по приоритетам")
        repo.updateCard(mail, CardEdit("Почта: сортировка по приоритетам", "", Priority.Medium, null, listOf("модуль", "ИИ")))
        repo.addCard(b, todo, "Студия: графики")
        val kanban = repo.addCard(b, doing, "Канбан-доски")
        repo.updateCard(kanban, CardEdit("Канбан-доски", "Перетаскивание карточек мышью", Priority.High, today, listOf("модуль")))
        val skeleton = repo.addCard(b, done, "Каркас приложения")
        repo.updateCard(skeleton, CardEdit("Каркас приложения", "", Priority.Low, today.plus(DatePeriod(days = -2)), emptyList()))
    }

    @Test fun kanbanLight() = Screenshots.shoot("kanban-light", Section.Kanban, seed = seed)
    @Test fun kanbanDark() = Screenshots.shoot("kanban-dark", Section.Kanban, ThemeMode.Dark, seed = seed)
    @Test fun kanbanEmpty() = Screenshots.shoot("kanban-empty", Section.Kanban)

    /** Тянем мышью первую карточку из «Нужно сделать» в «В работе» и проверяем, что она туда переехала. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun dragCardToAnotherColumn() {
        val container = Screenshots.demoContainer(seed)
        val repo = KanbanRepository(container.db, container.io)
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(container, Section.Kanban, ThemeMode.Light) }
        fun frames(n: Int = 4) = repeat(n) { scene.render(); Thread.sleep(100) }
        try {
            frames(8)
            // Сайдбар 220 px + отступ 16; первая колонка ~ x 237..525, вторая ~ 537..825. Первая карточка ~ y 140.
            val start = Offset(380f, 140f)
            scene.sendPointerEvent(PointerEventType.Press, start)
            var p = start
            repeat(20) {
                p += Offset(15f, 2f)
                scene.sendPointerEvent(PointerEventType.Move, p)
                scene.render()
            }
            frames(2)
            Screenshots.save(scene, "kanban-dragging")
            scene.sendPointerEvent(PointerEventType.Release, p)
            frames(6)

            val columns = runBlocking {
                val boardId = repo.observeBoards().first().first().id
                repo.observeBoard(boardId).first()
            }
            assertEquals(listOf("Почта: сортировка по приоритетам", "Студия: графики"), columns[0].cards.map { it.title })
            assertEquals("Календарь с напоминаниями", columns[1].cards.first().title)
        } finally {
            scene.close()
        }
    }
}
