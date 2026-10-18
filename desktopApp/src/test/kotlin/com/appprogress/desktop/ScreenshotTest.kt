package com.appprogress.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.appprogress.core.DatabaseFactory
import com.appprogress.notes.NotesRepository
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Рисует экраны без открытия окна и сохраняет PNG в build/screenshots.
 * Так можно проверить внешний вид на сервере сборки, где нет монитора.
 */
class ScreenshotTest {
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun demoContainer(): AppContainer {
        val repo = NotesRepository(DatabaseFactory.inMemory(), Dispatchers.IO)
        runBlocking {
            repo.create("Список покупок", "Молоко\nХлеб\nКофе в зёрнах")
            val plan = repo.create(
                "План проекта AppProgress",
                "Фаза 0 — каркас приложения под Windows.\n\nДальше: канбан, календарь с напоминаниями, " +
                    "студия для таблиц и графиков, почта и локальный ИИ-ассистент.",
            )
            repo.create("", "Идея без заголовка: виджет «сводка дня» на главном экране")
            repo.setPinned(plan, true)
        }
        return AppContainer(repo, CoroutineScope(SupervisorJob() + Dispatchers.Default), "C:\\Users\\demo\\AppData\\Roaming\\AppProgress", APP_VERSION)
    }

    private fun shoot(name: String, section: Section, theme: ThemeMode) {
        val container = demoContainer()
        val scene = ImageComposeScene(1280, 800, Density(1f)) { App(container, section, theme) }
        try {
            // Несколько кадров с паузами: данные из базы приходят асинхронно.
            var time = 0L
            repeat(6) {
                scene.render(time)
                Thread.sleep(150)
                time += 100_000_000L
            }
            val png = scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outDir, "$name.png").writeBytes(png)
            assertTrue(png.size > 10_000, "скриншот $name подозрительно пустой")
        } finally {
            scene.close()
        }
    }

    @Test fun notesLight() = shoot("notes-light", Section.Notes, ThemeMode.Light)
    @Test fun notesDark() = shoot("notes-dark", Section.Notes, ThemeMode.Dark)
    @Test fun calendarComingSoon() = shoot("calendar-soon", Section.Calendar, ThemeMode.Light)
    @Test fun settings() = shoot("settings", Section.Settings, ThemeMode.Light)
}
