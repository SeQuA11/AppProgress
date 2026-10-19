package com.appprogress.ui

import com.appprogress.core.SettingsRepository
import com.appprogress.db.AppDatabase
import com.appprogress.focus.FocusTimer
import com.appprogress.focus.TimeRepository
import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Всё, что нужно экранам, собранное в одном месте. Создаётся платформой (desktopApp, позже androidApp).
 * [scope] живёт столько же, сколько приложение: сохранение не прервётся при переходе между разделами.
 * Модули создают свои репозитории сами из [db] и [io] — так ветки модулей не правят этот файл.
 * [notify] показывает системное уведомление (на ПК — из трея); в тестах ничего не делает.
 */
class AppContainer(
    val db: AppDatabase,
    val io: CoroutineDispatcher,
    val scope: CoroutineScope,
    val dataLocation: String,
    val version: String,
    val notify: (title: String, text: String) -> Unit = { _, _ -> },
) {
    val notes = NotesRepository(db, io)
    val settings = SettingsRepository(db, io)
    val time = TimeRepository(db, io)

    /** Один таймер на всё приложение: можно запустить фокус в канбане и видеть отсчёт на экране «Сегодня». */
    val focus = FocusTimer(time, scope, notify = { t, text -> notify(t, text) })
}
