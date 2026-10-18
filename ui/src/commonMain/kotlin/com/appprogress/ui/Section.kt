package com.appprogress.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.appprogress.ui.notes.NotesScreen

/**
 * Разделы приложения. [content] — экран раздела; null значит «в разработке», и тогда показываем [planned].
 * Каждый модуль подключается правкой только своей записи. Пустые строки между записями нужны:
 * без них правки соседних записей из разных веток Git считает конфликтом.
 */
enum class Section(
    val title: String,
    val icon: ImageVector,
    val summary: String = "",
    val planned: List<String> = emptyList(),
    val content: (@Composable (AppContainer) -> Unit)? = null,
) {
    Notes("Заметки", Icons.Filled.Edit, content = { NotesScreen(it) }),

    Kanban("Канбан", Icons.AutoMirrored.Filled.List, content = { com.appprogress.ui.kanban.KanbanScreen(it) }),

    Calendar("Календарь", Icons.Filled.DateRange, content = { com.appprogress.ui.calendar.CalendarScreen(it) }),

    Studio("Студия", Icons.Filled.Build, content = { com.appprogress.ui.studio.StudioScreen(it) }),

    Mail("Почта", Icons.Filled.Email, content = { com.appprogress.ui.mail.MailScreen(it) }),

    Assistant("Ассистент", Icons.Filled.Face, content = { com.appprogress.ui.assistant.AssistantScreen(it) }),

    // Экран настроек рисует сам App: ему нужна текущая тема.
    Settings("Настройки", Icons.Filled.Settings);

    val ready: Boolean get() = content != null || this == Settings
}
