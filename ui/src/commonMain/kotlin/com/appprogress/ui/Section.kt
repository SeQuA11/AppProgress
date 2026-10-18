package com.appprogress.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Разделы приложения. [ready] = false — раздел ещё в разработке, показываем, что в нём будет. */
enum class Section(
    val title: String,
    val icon: ImageVector,
    val ready: Boolean,
    val summary: String = "",
    val planned: List<String> = emptyList(),
) {
    Notes("Заметки", Icons.Filled.Edit, ready = true),
    Kanban(
        "Канбан", Icons.AutoMirrored.Filled.List, ready = false,
        summary = "Доски с задачами: перетаскивайте карточки между колонками.",
        planned = listOf(
            "Несколько досок и колонок",
            "Перетаскивание карточек мышью",
            "Сроки, приоритеты, метки и подзадачи",
            "Связь карточки с заметкой",
        ),
    ),
    Calendar(
        "Календарь", Icons.Filled.DateRange, ready = false,
        summary = "События, заметки на даты, дни рождения и напоминания.",
        planned = listOf(
            "Виды: месяц, неделя, день и список",
            "Дни рождения и важные даты с ежегодным повтором",
            "Напоминания за N минут, часов или дней",
            "Задачи со сроком прямо в календаре",
        ),
    ),
    Studio(
        "Студия", Icons.Filled.Build, ready = false,
        summary = "Таблицы, графики и диаграммы в одном месте.",
        planned = listOf(
            "Таблицы с типами колонок и формулами",
            "Графики, которые обновляются вместе с таблицей",
            "Редактор диаграмм: фигуры, стрелки, шаблоны",
            "Экспорт в PNG, SVG и PDF",
        ),
    ),
    Mail(
        "Почта", Icons.Filled.Email, ready = false,
        summary = "Все ящики в одной ленте, важное — сверху.",
        planned = listOf(
            "Несколько ящиков: Gmail, Outlook, Яндекс, Mail.ru и любые IMAP",
            "Приоритеты: важно / обычное / неважное",
            "Ваши правила и обучение на ваших действиях",
            "Письмо → задача или событие одной кнопкой",
        ),
    ),
    Assistant(
        "Ассистент", Icons.Filled.Face, ready = false,
        summary = "ИИ-помощник, который работает прямо на вашем компьютере, без интернета.",
        planned = listOf(
            "Локальная языковая модель — данные не покидают компьютер",
            "Поиск по заметкам, задачам и почте",
            "Создаёт задачи и события — только с вашим подтверждением",
            "Сводка дня: что важно сегодня",
        ),
    ),
    Settings("Настройки", Icons.Filled.Settings, ready = true),
}
