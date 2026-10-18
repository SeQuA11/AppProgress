package com.appprogress.assistant

import com.appprogress.core.Dates
import com.appprogress.notes.NotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/** Инструменты ассистента для заметок. Другие модули добавляют свои инструменты так же. */
fun notesTools(notes: NotesRepository): List<AssistantTool> = listOf(
    object : AssistantTool {
        override val spec = ToolSpec(
            "search_notes",
            "Ищет заметки пользователя по словам. Пустой запрос — список последних заметок.",
            stringParams("query" to "Слова для поиска", required = emptyList()),
        )

        override fun activity(args: JsonObject) =
            args.str("query").let { if (it.isBlank()) "Смотрю последние заметки" else "Ищу в заметках: «$it»" }

        override suspend fun run(args: JsonObject): String {
            val query = args.str("query")
            val words = query.lowercase().split(' ', ',').filter { it.length > 2 }
            val found = notes.observeAll().first().filter { n ->
                words.isEmpty() || words.any { w -> n.matches(w) }
            }.take(10)
            if (found.isEmpty()) return "Заметок по запросу «$query» не найдено."
            return found.joinToString("\n") { n -> "- id=${n.id} «${n.displayTitle}»: ${n.content.replace('\n', ' ').take(160)}" }
        }
    },
    object : AssistantTool {
        override val spec = ToolSpec(
            "read_note",
            "Возвращает полный текст заметки по её id (id берётся из search_notes).",
            stringParams("id" to "id заметки"),
        )

        override fun activity(args: JsonObject) = "Читаю заметку"

        override suspend fun run(args: JsonObject): String {
            val note = notes.get(args.str("id")) ?: return "Заметка не найдена."
            return "«${note.displayTitle}»\n${note.content}"
        }
    },
    object : AssistantTool {
        override val spec = ToolSpec(
            "create_note",
            "Создаёт новую заметку. Приложение покажет пользователю карточку для подтверждения.",
            stringParams("title" to "Заголовок", "content" to "Текст заметки"),
        )
        override val writes = true

        override suspend fun describe(args: JsonObject) = "Создать заметку «${args.str("title").ifBlank { "Без названия" }}»"

        override suspend fun run(args: JsonObject): String {
            notes.create(args.str("title"), args.str("content"))
            return "Заметка «${args.str("title")}» создана."
        }
    },
)

/** Текущая дата — без неё модель не знает, какое сегодня число. */
fun dateTool(): AssistantTool = object : AssistantTool {
    override val spec = ToolSpec("today", "Возвращает сегодняшнюю дату.", stringParams(required = emptyList()))
    override fun activity(args: JsonObject) = "Смотрю дату"
    override suspend fun run(args: JsonObject) = "Сегодня ${Dates.format(Dates.today())}"
}
