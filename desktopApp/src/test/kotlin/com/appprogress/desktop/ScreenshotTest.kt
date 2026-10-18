package com.appprogress.desktop

import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.theme.ThemeMode
import kotlin.test.Test

class ScreenshotTest {
    private val seedNotes: suspend (AppContainer) -> Unit = { c ->
        val repo = c.notes
        repo.create("Список покупок", "Молоко\nХлеб\nКофе в зёрнах")
        val plan = repo.create(
            "План проекта AppProgress",
            "Фаза 0 — каркас приложения под Windows.\n\nДальше: канбан, календарь с напоминаниями, " +
                "студия для таблиц и графиков, почта и локальный ИИ-ассистент.",
        )
        repo.create("", "Идея без заголовка: виджет «сводка дня» на главном экране")
        repo.setPinned(plan, true)
    }

    @Test fun notesLight() = Screenshots.shoot("notes-light", Section.Notes, seed = seedNotes)
    @Test fun notesDark() = Screenshots.shoot("notes-dark", Section.Notes, ThemeMode.Dark, seed = seedNotes)
    @Test fun calendarComingSoon() = Screenshots.shoot("coming-soon", Section.Calendar)
    @Test fun settings() = Screenshots.shoot("settings", Section.Settings)
}
