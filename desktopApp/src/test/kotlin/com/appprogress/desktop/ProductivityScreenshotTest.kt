package com.appprogress.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.appprogress.calendar.CalendarEvent
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.Repeat
import com.appprogress.core.Dates
import com.appprogress.core.nowMillis
import com.appprogress.kanban.CardEdit
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.Priority
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.common.AppCommands
import com.appprogress.ui.studio.StudioScreen
import com.appprogress.ui.theme.AppTheme
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test

class ProductivityScreenshotTest {
    private val seed: suspend (AppContainer) -> Unit = { c ->
        val kanban = KanbanRepository(c.db, c.io)
        val today = Dates.today()
        fun day(n: Int) = today.plus(DatePeriod(days = n))
        val b = kanban.createBoard("Работа")
        val (todo, doing) = kanban.observeBoard(b).first().map { it.id }
        suspend fun card(col: String, title: String, due: Int, p: Priority = Priority.None, repeat: Repeat = Repeat.None) =
            kanban.addCard(b, col, title).also { kanban.updateCard(it, CardEdit(title, "", p, day(due), emptyList(), repeat)) }
        card(todo, "Сдать отчёт за квартал", -2, Priority.High)
        card(doing, "Подготовить презентацию", 0, Priority.Medium)
        card(todo, "Еженедельный созвон с командой", 3, repeat = Repeat.Weekly)
        card(todo, "Обновить договор", 5)

        val cal = CalendarRepository(c.db, c.io)
        cal.save(CalendarEvent(title = "Стоматолог", date = today, startTime = LocalTime(18, 30)))
        cal.save(CalendarEvent(title = "Планёрка", date = day(1), startTime = LocalTime(10, 0)))

        c.notes.create("Идеи", "Пара мыслей #идеи")
        // Немного учёта времени: несколько отрезков фокуса за неделю.
        val now = nowMillis()
        listOf(0 to 50, 0 to 25, 1 to 75, 2 to 25, 3 to 100, 5 to 50).forEach { (daysAgo, minutes) ->
            val end = now - daysAgo * 24 * 3600_000L
            c.time.record(null, if (minutes > 60) "Сдать отчёт за квартал" else "Подготовить презентацию", end - minutes * 60_000L, end)
        }
    }

    @Test fun today() = Screenshots.shoot("productivity-today", Section.Today, seed = seed)
    @Test fun todayDark() = Screenshots.shoot("productivity-today-dark", Section.Today, ThemeMode.Dark, seed = seed)
    @Test fun calendarWithTasks() = Screenshots.shoot("productivity-calendar", Section.Calendar, seed = seed)
    @Test fun settingsWithBackups() = Screenshots.shoot("productivity-settings", Section.Settings, seed = seed)

    @Test
    fun commandPalette() {
        val container = Screenshots.demoContainer(seed)
        val commands = AppCommands().apply { paletteOpen = true }
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(container, Section.Today, ThemeMode.Light, commands) }
        try {
            repeat(8) { scene.render(); Thread.sleep(120) }
            Screenshots.save(scene, "productivity-palette")
        } finally {
            scene.close()
        }
    }

    @Test
    fun timeStats() {
        val container = Screenshots.demoContainer(seed)
        val scene = ImageComposeScene(1280, 800, Density(1f)) {
            AppTheme(ThemeMode.Light) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { StudioScreen(container, initialTime = true) }
            }
        }
        try {
            repeat(8) { scene.render(); Thread.sleep(120) }
            Screenshots.save(scene, "productivity-time")
        } finally {
            scene.close()
        }
    }

    @Test
    fun quickCapture() {
        val scene = ImageComposeScene(560, 190, Density(1f)) {
            AppTheme(ThemeMode.Light) { QuickCaptureContent(onSave = {}, onClose = {}) }
        }
        try {
            repeat(4) { scene.render(); Thread.sleep(80) }
            Screenshots.save(scene, "productivity-quick-capture", minBytes = 3_000)
        } finally {
            scene.close()
        }
    }
}
