package com.appprogress.assistant

import com.appprogress.core.Dates
import com.appprogress.kanban.Board
import com.appprogress.kanban.BoardColumn
import com.appprogress.kanban.Card
import com.appprogress.kanban.CardEdit
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.Priority
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject

/** Колонка считается «завершённой», если называется «Готово», «Сделано», «Done». */
fun BoardColumn.isDone(): Boolean = title.lowercase().let { "готов" in it || "сделан" in it || "done" in it }

/** Задача вместе с доской и колонкой, где она лежит. */
data class TaskRef(val board: Board, val column: BoardColumn, val card: Card)

suspend fun KanbanRepository.allTasks(): List<TaskRef> =
    observeBoards().first().flatMap { b -> observeBoard(b.id).first().flatMap { col -> col.cards.map { TaskRef(b, col, it) } } }

fun parsePriority(text: String): Priority = when (text.trim().lowercase()) {
    "high", "высокий", "срочно", "3" -> Priority.High
    "medium", "средний", "2" -> Priority.Medium
    "low", "низкий", "1" -> Priority.Low
    else -> Priority.None
}

fun TaskRef.line(): String = buildString {
    append("- id=${card.id} «${card.title}» [${board.title} / ${column.title}]")
    card.dueDate?.let { due ->
        append(", срок ${Dates.format(due)}")
        if (!column.isDone() && card.isOverdue(Dates.today())) append(" (просрочено)")
    }
    if (card.priority != Priority.None) append(", приоритет ${card.priority.title.lowercase()}")
    if (card.labels.isNotEmpty()) append(", метки: ${card.labels.joinToString()}")
}

private fun findColumn(columns: List<BoardColumn>, name: String): BoardColumn? =
    columns.firstOrNull { it.title.equals(name.trim(), ignoreCase = true) }
        ?: columns.firstOrNull { name.isNotBlank() && it.title.contains(name.trim(), ignoreCase = true) }

fun kanbanTools(repo: KanbanRepository): List<AssistantTool> {

    /** Доска по названию (или первая); если досок нет — создаём «Мои задачи». */
    suspend fun boardFor(name: String): Pair<Board, List<BoardColumn>> {
        val boards = repo.observeBoards().first()
        val board = boards.firstOrNull { it.title.equals(name.trim(), ignoreCase = true) }
            ?: boards.firstOrNull { name.isNotBlank() && it.title.contains(name.trim(), ignoreCase = true) }
            ?: boards.firstOrNull()
            ?: repo.createBoard("Мои задачи").let { id -> repo.observeBoards().first().first { it.id == id } }
        return board to repo.observeBoard(board.id).first()
    }

    return listOf(
        object : AssistantTool {
            override val spec = ToolSpec(
                "list_tasks",
                "Задачи с канбан-досок. filter: overdue — просроченные, today — со сроком сегодня, week — на 7 дней вперёд, " +
                    "open — все незавершённые, all — все. query — слова для поиска в названии и описании.",
                stringParams("filter" to "overdue | today | week | open | all", "query" to "Слова для поиска", required = emptyList()),
            )

            override fun activity(args: JsonObject) = when (args.str("filter")) {
                "overdue" -> "Смотрю просроченные задачи"
                "today" -> "Смотрю задачи на сегодня"
                "week" -> "Смотрю задачи на неделю"
                else -> args.str("query").let { if (it.isBlank()) "Смотрю задачи на досках" else "Ищу задачи: «$it»" }
            }

            override suspend fun run(args: JsonObject): String {
                val today = Dates.today()
                val query = args.str("query").trim().lowercase()
                val tasks = repo.allTasks().filter { t ->
                    val due = t.card.dueDate
                    val matchesFilter = when (args.str("filter")) {
                        "overdue" -> !t.column.isDone() && due != null && due < today
                        "today" -> !t.column.isDone() && due == today
                        "week" -> !t.column.isDone() && due != null && due >= today && due.toEpochDays() - today.toEpochDays() <= 7
                        "all" -> true
                        else -> !t.column.isDone()
                    }
                    matchesFilter && (query.isEmpty() || query in t.card.title.lowercase() || query in t.card.description.lowercase())
                }.sortedWith(compareBy({ it.card.dueDate == null }, { it.card.dueDate }, { -it.card.priority.code }))
                if (tasks.isEmpty()) return "Задач не найдено."
                return tasks.take(25).joinToString("\n") { it.line() } +
                    if (tasks.size > 25) "\n… и ещё ${tasks.size - 25}" else ""
            }
        },
        object : AssistantTool {
            override val spec = ToolSpec(
                "create_task",
                "Создаёт задачу (карточку) на канбан-доске. Если доска или колонка не указаны — первая доска и первая колонка.",
                stringParams(
                    "title" to "Название задачи",
                    "description" to "Описание",
                    "board" to "Название доски",
                    "column" to "Название колонки",
                    "due" to "Срок дд.мм.гггг",
                    "priority" to "high | medium | low",
                    required = listOf("title"),
                ),
            )
            override val writes = true

            override suspend fun describe(args: JsonObject): String {
                val (board, columns) = boardFor(args.str("board"))
                val column = findColumn(columns, args.str("column")) ?: columns.firstOrNull()
                return buildString {
                    append("Создать задачу «${args.str("title")}» на доске «${board.title}»")
                    column?.let { append(" → «${it.title}»") }
                    Dates.parse(args.str("due"))?.let { append(", срок ${Dates.format(it)}") }
                    parsePriority(args.str("priority")).takeIf { it != Priority.None }?.let { append(", приоритет ${it.title.lowercase()}") }
                }
            }

            override suspend fun run(args: JsonObject): String {
                val (board, columns) = boardFor(args.str("board"))
                val column = findColumn(columns, args.str("column")) ?: columns.firstOrNull()
                    ?: return "На доске «${board.title}» нет колонок."
                val id = repo.addCard(board.id, column.id, args.str("title"))
                repo.updateCard(
                    id,
                    CardEdit(args.str("title"), args.str("description"), parsePriority(args.str("priority")), Dates.parse(args.str("due")), emptyList()),
                )
                return "Задача «${args.str("title")}» создана на доске «${board.title}» в колонке «${column.title}»."
            }
        },
        object : AssistantTool {
            override val spec = ToolSpec(
                "move_task",
                "Переносит задачу в другую колонку той же доски, например в «Готово». id берётся из list_tasks.",
                stringParams("id" to "id задачи", "column" to "Название колонки"),
            )
            override val writes = true

            override suspend fun describe(args: JsonObject): String {
                val task = repo.allTasks().firstOrNull { it.card.id == args.str("id") } ?: return "Перенести задачу (не найдена)"
                return "Перенести «${task.card.title}» в колонку «${args.str("column")}»"
            }

            override suspend fun run(args: JsonObject): String {
                val task = repo.allTasks().firstOrNull { it.card.id == args.str("id") } ?: return "Задача не найдена."
                val columns = repo.observeBoard(task.board.id).first()
                val target = findColumn(columns, args.str("column"))
                    ?: return "На доске «${task.board.title}» нет колонки «${args.str("column")}». Есть: ${columns.joinToString { it.title }}."
                repo.moveCard(task.card.id, target.id, Int.MAX_VALUE)
                return "Задача «${task.card.title}» перенесена в «${target.title}»."
            }
        },
    )
}
