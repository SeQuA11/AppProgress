package com.appprogress.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.appprogress.calendar.CalendarEvent
import com.appprogress.calendar.CalendarRepository
import com.appprogress.core.Dates
import com.appprogress.kanban.KanbanRepository
import com.appprogress.notes.LinkKind
import com.appprogress.notes.NoteCrypto
import com.appprogress.notes.NoteMarkup
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class Notes2ScreenshotTest {
    private val seed: suspend (AppContainer) -> Unit = { c ->
        val notes = c.notes
        val kanban = KanbanRepository(c.db, c.io)
        val calendar = CalendarRepository(c.db, c.io)
        val board = kanban.createBoard("Проект")
        val (todo, doing) = kanban.observeBoard(board).first().map { it.id }
        val card = kanban.addCard(board, doing, "Сверстать отчёт")
        kanban.addCard(board, todo, "Позвонить подрядчику")
        val today = Dates.today()
        val event = calendar.save(CalendarEvent(title = "Созвон с заказчиком", date = today.plus(DatePeriod(days = 2)), startTime = LocalTime(11, 0)))

        val work = notes.createFolder("Работа")
        notes.createFolder("Проект X", work)
        notes.createFolder("Шаблоны")
        notes.create("Идеи", "Список идей #идеи")
        notes.create("Покупки", "- [ ] Молоко\n- [x] Хлеб #дом")
        val secret = notes.create("Пароли от Wi-Fi", "")
        notes.setEncryptedContent(secret, NoteCrypto.encrypt("сеть: 12345", "1"), true)
        val old = notes.create("Старый черновик", "удалю")
        notes.delete(old)
        val main = notes.create(
            "Запуск проекта",
            """
            ## Цель
            Выпустить **первую версию** до конца месяца. Подробности — в [[Идеи]].

            ## Задачи
            - [x] Собрать требования
            - [ ] ${NoteMarkup.appLink(LinkKind.Card, card, "Сверстать отчёт")}
            - [ ] Обсудить на встрече ${NoteMarkup.appLink(LinkKind.Event, event, "Созвон с заказчиком")}

            > Главное — не распыляться.

            Код для проверки: `./gradlew test` #работа #запуск
            """.trimIndent(),
            folderId = work,
        )
        notes.setPinned(main, true)
        notes.setDate(main, today)
    }

    @Test fun notesPreview() = Screenshots.shoot("notes2-preview", Section.Notes, seed = seed)
    @Test fun notesPreviewDark() = Screenshots.shoot("notes2-preview-dark", Section.Notes, ThemeMode.Dark, seed = seed)
    @Test fun calendarWithDatedNote() = Screenshots.shoot("notes2-calendar", Section.Calendar, seed = seed)

    /** Щелчки по интерфейсу: «Править» — режим правки с подсветкой и панелью, «Корзина» — список удалённых. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun shootAfterClick(name: String, click: Offset) {
        val container = Screenshots.demoContainer(seed)
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(container, Section.Notes, ThemeMode.Light) }
        try {
            repeat(8) { scene.render(); Thread.sleep(120) }
            scene.sendPointerEvent(PointerEventType.Press, click)
            scene.sendPointerEvent(PointerEventType.Release, click)
            repeat(8) { scene.render(); Thread.sleep(120) }
            Screenshots.save(scene, name)
        } finally {
            scene.close()
        }
    }

    @Test fun notesEdit() = shootAfterClick("notes2-edit", EDIT_BUTTON)

    /** Галочка в режиме просмотра меняет текст заметки, и автосохранение записывает его в базу. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun checkboxClickIsSaved() {
        val container = Screenshots.demoContainer(seed)
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(container, Section.Notes, ThemeMode.Light) }
        try {
            repeat(8) { scene.render(); Thread.sleep(120) }
            scene.sendPointerEvent(PointerEventType.Press, SECOND_CHECKBOX)
            scene.sendPointerEvent(PointerEventType.Release, SECOND_CHECKBOX)
            repeat(10) { scene.render(); Thread.sleep(120) } // 400 мс паузы автосохранения + запись
            val text = runBlocking { container.notes.observeAll().first().single { it.title == "Запуск проекта" }.content }
            assertTrue("- [x] [Сверстать отчёт]" in text, text)
        } finally {
            scene.close()
        }
    }
    @Test fun notesTrash() = shootAfterClick("notes2-trash", TRASH_ITEM)

    companion object {
        // Координаты кнопок на экране 1280×800 (уточнены по скриншоту notes2-preview).
        val EDIT_BUTTON = Offset(1108f, 42f)
        val TRASH_ITEM = Offset(300f, 392f)
        val SECOND_CHECKBOX = Offset(785f, 319f)
    }
}
