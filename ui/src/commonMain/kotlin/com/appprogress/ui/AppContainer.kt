package com.appprogress.ui

import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Всё, что нужно экранам, собранное в одном месте. Создаётся платформой (desktopApp, позже androidApp).
 * [scope] живёт столько же, сколько приложение: сохранение не прервётся при переходе между разделами.
 */
class AppContainer(
    val notes: NotesRepository,
    val scope: CoroutineScope,
    val dataLocation: String,
    val version: String,
)
