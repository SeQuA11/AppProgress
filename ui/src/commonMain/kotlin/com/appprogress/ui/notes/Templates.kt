package com.appprogress.ui.notes

import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import com.appprogress.notes.NoteMarkup

/** Шаблон новой заметки: название и текст с подстановками {{дата}}, {{день}}. */
data class NoteTemplate(val name: String, val title: String, val body: String)

val builtInTemplates = listOf(
    NoteTemplate(
        "Встреча", "Встреча {{дата}}",
        "**Участники:** \n\n## Повестка\n- \n\n## Решения\n- \n\n## Задачи\n- [ ] \n",
    ),
    NoteTemplate(
        "План проекта", "План проекта",
        "## Цель\n\n\n## Сроки\n- Начало: {{дата}}\n- Окончание: \n\n## Этапы\n- [ ] \n- [ ] \n\n## Риски\n- \n",
    ),
    NoteTemplate(
        "Итоги недели", "Итоги недели {{дата}}",
        "## Что получилось\n- \n\n## Что не получилось\n- \n\n## Планы на следующую неделю\n- [ ] \n",
    ),
    NoteTemplate("Список дел", "Дела на {{дата}}", "- [ ] \n- [ ] \n- [ ] \n"),
)

fun NoteTemplate.fill(): Pair<String, String> {
    val today = Dates.today()
    val values = mapOf("дата" to Dates.format(today), "день" to RuText.weekday(today), "название" to name)
    return NoteMarkup.fillTemplate(title, values) to NoteMarkup.fillTemplate(body, values)
}
