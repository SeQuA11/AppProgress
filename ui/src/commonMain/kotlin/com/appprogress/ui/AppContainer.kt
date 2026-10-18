package com.appprogress.ui

import com.appprogress.core.SettingsRepository
import com.appprogress.db.AppDatabase
import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Всё, что нужно экранам, собранное в одном месте. Создаётся платформой (desktopApp, позже androidApp).
 * [scope] живёт столько же, сколько приложение: сохранение не прервётся при переходе между разделами.
 * Модули создают свои репозитории сами из [db] и [io] — так ветки модулей не правят этот файл.
 */
class AppContainer(
    val db: AppDatabase,
    val io: CoroutineDispatcher,
    val scope: CoroutineScope,
    val dataLocation: String,
    val version: String,
) {
    val notes = NotesRepository(db, io)
    val settings = SettingsRepository(db, io)
}
