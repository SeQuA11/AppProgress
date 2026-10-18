package com.appprogress.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.ReminderService
import com.appprogress.core.DatabaseFactory
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.awt.Dimension

const val APP_VERSION = "0.1.0"

fun createContainer(): AppContainer = AppContainer(
    db = DatabaseFactory.open(),
    io = Dispatchers.IO,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    dataLocation = DatabaseFactory.defaultDataDir().absolutePath,
    version = APP_VERSION,
)

/** Значок в трее: кружок в фирменном цвете с галочкой. Рисуем кодом, чтобы не возиться с файлами иконок. */
private object TrayIcon : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() {
        drawCircle(Color(0xFF4F46E5))
        val w = size.width
        val stroke = Stroke(width = w * 0.11f)
        val path = Path().apply {
            moveTo(w * 0.28f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.68f)
            lineTo(w * 0.73f, w * 0.36f)
        }
        drawPath(path, Color.White, style = stroke)
    }
}

fun main() {
    val container = createContainer()
    application {
        var windowVisible by remember { mutableStateOf(true) }
        var hintShown by remember { mutableStateOf(false) }
        val tray = rememberTrayState()

        // Напоминания календаря приходят уведомлениями Windows, пока приложение запущено (в том числе в трее).
        LaunchedEffect(Unit) {
            ReminderService(CalendarRepository(container.db, container.io), notify = { title, text ->
                tray.sendNotification(Notification(title, text, Notification.Type.Info))
            }).start(this)
        }

        Tray(
            icon = TrayIcon,
            state = tray,
            tooltip = "AppProgress",
            onAction = { windowVisible = true },
            menu = {
                Item("Открыть AppProgress", onClick = { windowVisible = true })
                Separator()
                Item("Выход", onClick = ::exitApplication)
            },
        )

        Window(
            // Закрытие окна сворачивает приложение в трей, чтобы напоминания продолжали приходить.
            onCloseRequest = {
                windowVisible = false
                if (!hintShown) {
                    hintShown = true
                    tray.sendNotification(
                        Notification("AppProgress работает в трее", "Напоминания будут приходить. Выход — через значок в трее."),
                    )
                }
            },
            visible = windowVisible,
            title = "AppProgress",
            icon = TrayIcon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            window.minimumSize = Dimension(960, 600)
            App(container)
        }
    }
}
