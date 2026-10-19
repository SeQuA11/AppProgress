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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.ReminderService
import com.appprogress.core.Backups
import com.appprogress.core.DatabaseFactory
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.TaskReminders
import com.appprogress.ui.App
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.AppCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.awt.Dimension
import javax.swing.SwingUtilities
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val APP_VERSION = "0.1.0"

/** Уведомления из любого места приложения; лоток (tray) появляется позже контейнера, поэтому ссылка подставляется потом. */
private var trayRef: TrayState? = null

fun createContainer(): AppContainer = AppContainer(
    db = DatabaseFactory.open(),
    io = Dispatchers.IO,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    dataLocation = DatabaseFactory.defaultDataDir().absolutePath,
    version = APP_VERSION,
    notify = { title, text -> trayRef?.sendNotification(Notification(title, text, Notification.Type.Info)) },
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

@OptIn(ExperimentalTime::class)
fun main() {
    val container = createContainer()
    val commands = AppCommands()
    application {
        var windowVisible by remember { mutableStateOf(true) }
        var hintShown by remember { mutableStateOf(false) }
        var quickCapture by remember { mutableStateOf(false) }
        val tray = rememberTrayState()
        trayRef = tray
        val notify: (String, String) -> Unit = { title, text -> tray.sendNotification(Notification(title, text, Notification.Type.Info)) }

        LaunchedEffect(Unit) {
            // Напоминания календаря приходят уведомлениями Windows, пока приложение запущено (в том числе в трее).
            ReminderService(CalendarRepository(container.db, container.io), notify = notify).start(this)
            // Сроки задач канбана: в 9:00 — задачи на сегодня и сводка просроченных.
            launch {
                val reminders = TaskReminders(container.db, KanbanRepository(container.db, container.io), container.io)
                while (isActive) {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    runCatching { reminders.takeDue(now) }.getOrDefault(emptyList()).forEach { notify(it.title, it.text) }
                    delay(60_000)
                }
            }
            // Ежедневная резервная копия: при запуске и затем проверка раз в 6 часов.
            launch {
                val backups = Backups()
                while (isActive) {
                    withContext(Dispatchers.IO) { runCatching { backups.dailyIfNeeded() } }
                    delay(6 * 3600_000L)
                }
            }
            // Ctrl+Alt+N в любой программе — окно быстрой записи.
            GlobalHotkey.registerCtrlAltN { SwingUtilities.invokeLater { quickCapture = true } }
        }

        Tray(
            icon = TrayIcon,
            state = tray,
            tooltip = "AppProgress",
            onAction = { windowVisible = true },
            menu = {
                Item("Открыть AppProgress", onClick = { windowVisible = true })
                Item("Быстрая запись (Ctrl+Alt+N)", onClick = { quickCapture = true })
                Item("Фокус 25 минут", onClick = { container.focus.startFocus("Фокус") })
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
                    notify("AppProgress работает в трее", "Напоминания будут приходить. Выход — через значок в трее.")
                }
            },
            visible = windowVisible,
            title = "AppProgress",
            icon = TrayIcon,
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
            // Ctrl+K ловим на уровне окна — так палитра откроется, даже если ни одно поле не в фокусе.
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.K) { commands.paletteOpen = true; true } else false
            },
        ) {
            window.minimumSize = Dimension(960, 600)
            App(container, commands = commands)
        }

        if (quickCapture) QuickCaptureWindow(container, onClose = { quickCapture = false })
    }
}
