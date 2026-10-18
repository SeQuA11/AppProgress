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
    Notes("Заметки", Icons.Filled.Edit, content = { NotesScreen(it.notes, it.scope) }),

    Kanban(
        "Канбан", Icons.AutoMirrored.Filled.List,
        summary = "Доски с задачами: перетаскивайте карточки между колонками.",
        planned = listOf(
            "Несколько досок и колонок",
            "Перетаскивание карточек мышью",
            "Сроки, приоритеты, метки и подзадачи",
            "Связь карточки с заметкой",
        ),
    ),

    Calendar("Календарь", Icons.Filled.DateRange, content = { com.appprogress.ui.calendar.CalendarScreen(it) }),

    Studio(
        "Студия", Icons.Filled.Build,
        summary = "Таблицы, графики и диаграммы в одном месте.",
        planned = listOf(
            "Таблицы с типами колонок и формулами",
            "Графики, которые обновляются вместе с таблицей",
            "Редактор диаграмм: фигуры, стрелки, шаблоны",
            "Экспорт в PNG, SVG и PDF",
        ),
    ),

    Mail(
        "Почта", Icons.Filled.Email,
        summary = "Все ящики в одной ленте, важное — сверху.",
        planned = listOf(
            "Несколько ящиков: Gmail, Outlook, Яндекс, Mail.ru и любые IMAP",
            "Приоритеты: важно / обычное / неважное",
            "Ваши правила и обучение на ваших действиях",
            "Письмо → задача или событие одной кнопкой",
        ),
    ),

    Assistant(
        "Ассистент", Icons.Filled.Face,
        summary = "ИИ-помощник, который работает прямо на вашем компьютере, без интернета.",
        planned = listOf(
            "Локальная языковая модель — данные не покидают компьютер",
            "Поиск по заметкам, задачам и почте",
            "Создаёт задачи и события — только с вашим подтверждением",
            "Сводка дня: что важно сегодня",
        ),
    ),

    // Экран настроек рисует сам App: ему нужна текущая тема.
    Settings("Настройки", Icons.Filled.Settings);

    val ready: Boolean get() = content != null || this == Settings
}
