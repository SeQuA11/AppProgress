package com.appprogress.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.appprogress.core.DatabaseFactory
import com.appprogress.notes.NotesRepository
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Dimension

const val APP_VERSION = "0.1.0"

fun createContainer(): AppContainer {
    val dataDir = DatabaseFactory.defaultDataDir()
    return AppContainer(
        notes = NotesRepository(DatabaseFactory.open(), Dispatchers.IO),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        dataLocation = dataDir.absolutePath,
        version = APP_VERSION,
    )
}

fun main() {
    val container = createContainer()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AppProgress",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            window.minimumSize = Dimension(960, 600)
            App(container)
        }
    }
}
