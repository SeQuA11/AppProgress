package com.appprogress.notes

import kotlinx.datetime.LocalDate

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
    val date: LocalDate? = null,
    val isDaily: Boolean = false,
    val encrypted: Boolean = false,
) {
    /** Заголовок для списка: если пользователь его не задал, берём первую строку текста. */
    val displayTitle: String
        get() = displayTitleOf(title, if (encrypted) "" else content)

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        // Ищем в Kotlin, а не через SQL LIKE: в SQLite LIKE не учитывает регистр только для латиницы,
        // и «Заметка» не нашлась бы по запросу «заметка».
        val q = query.trim().lowercase()
        return title.lowercase().contains(q) || (!encrypted && content.lowercase().contains(q))
    }
}

/** Заметка для списка: без полного текста, только начало (preview). */
data class NoteSummary(
    val id: String,
    val title: String,
    val preview: String,
    val pinned: Boolean,
    val updatedAt: Long,
    val folderId: String?,
    val date: LocalDate?,
    val isDaily: Boolean,
    val encrypted: Boolean,
    val tags: List<String>,
    val deletedAt: Long? = null,
) {
    val displayTitle: String get() = displayTitleOf(title, if (encrypted) "" else preview)

    /** Вторая строка в списке: первая непустая строка текста после заголовка, без разметки. */
    val previewLine: String
        get() = if (encrypted) "Зашифрованная заметка" else preview.lineSequence()
            .map { NoteMarkup.plain(it).trim() }.filter { it.isNotEmpty() }
            .drop(if (title.isBlank()) 1 else 0).firstOrNull().orEmpty()
}

data class NoteFolder(val id: String, val name: String, val parentId: String?)

data class NoteVersion(val id: String, val title: String, val content: String, val createdAt: Long)

internal fun displayTitleOf(title: String, content: String): String =
    title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.let { NoteMarkup.plain(it).trim() }.orEmpty() }
        .ifBlank { "Без названия" }

/** Куда ведёт ссылка внутри заметки. */
enum class LinkKind(val code: String, val title: String) {
    Note("note", "Заметка"), Card("card", "Задача"), Event("event", "Событие"), Mail("mail", "Письмо");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code }
    }
}

/**
 * Разметка заметок — Markdown с несколькими расширениями:
 *  - `[[Название]]` и `[[Название|подпись]]` — ссылка на другую заметку по названию;
 *  - `[Подпись](app://card/ID)` — ссылка на задачу, событие (`event/ID?date=2026-10-20`), письмо или заметку;
 *  - `![подпись](attachment:файл.png)` — картинка-вложение, `[подпись](attachment:файл.pdf)` — файл;
 *  - `#тег` — тег; `- [ ]` / `- [x]` — пункт чек-листа.
 */
object NoteMarkup {
    val WIKI_LINK = Regex("""\[\[([^\[\]|]+)(?:\|([^\[\]]+))?]]""")
    val APP_LINK = Regex("""\[([^\[\]]+)]\(app://(note|card|event|mail)/([^)\s?]+)(?:\?date=([0-9-]+))?\)""")
    val IMAGE = Regex("""!\[([^\[\]]*)]\(attachment:([^)\s]+)\)""")
    val FILE = Regex("""(?<!!)\[([^\[\]]+)]\(attachment:([^)\s]+)\)""")
    val CHECKBOX = Regex("""^(\s*)[-*] \[([ xXхХ])] (.*)$""")
    private val TAG = Regex("""(?<![\p{L}\p{N}_#/&=:.])#([\p{L}\p{N}_-]*\p{L}[\p{L}\p{N}_-]*)""")
    private val CODE_BLOCK = Regex("(?s)```.*?```")
    private val INLINE_CODE = Regex("`[^`\n]+`")

    /** Теги из текста (без «#»), в нижнем регистре, без повторов. Внутри кода теги не ищем. */
    fun tags(text: String): List<String> {
        val clean = text.replace(CODE_BLOCK, " ").replace(INLINE_CODE, " ")
        return TAG.findAll(clean).map { it.groupValues[1].lowercase() }.distinct().toList()
    }

    /** Названия заметок, на которые ссылается текст через [[…]]. */
    fun wikiTargets(text: String): List<String> = WIKI_LINK.findAll(text).map { it.groupValues[1].trim() }.distinct().toList()

    fun appLink(kind: LinkKind, id: String, label: String, date: String? = null): String =
        "[${label.replace("[", "(").replace("]", ")")}](app://${kind.code}/$id${date?.let { "?date=$it" }.orEmpty()})"

    /** Строка без разметки — для превью в списке. */
    fun plain(line: String): String = line
        .replace(IMAGE, "[картинка]")
        .replace(APP_LINK) { it.groupValues[1] }
        .replace(FILE) { it.groupValues[1] }
        .replace(WIKI_LINK) { it.groupValues[2].ifBlank { it.groupValues[1] } }
        .replace(Regex("""^\s*#{1,6}\s+"""), "")
        .replace(Regex("""^\s*[-*] \[[ xXхХ]] """), "")
        .replace(Regex("""^\s*[-*+]\s+"""), "")
        .replace(Regex("""^\s*>\s?"""), "")
        .replace("**", "").replace("__", "").replace("~~", "").replace("`", "")

    /** Переключает галочку в строке [lineIndex], если это пункт чек-листа. */
    fun toggleCheckbox(text: String, lineIndex: Int): String {
        val lines = text.split('\n').toMutableList()
        val line = lines.getOrNull(lineIndex) ?: return text
        val m = CHECKBOX.find(line) ?: return text
        val checked = m.groupValues[2].isNotBlank()
        lines[lineIndex] = "${m.groupValues[1]}- [${if (checked) " " else "x"}] ${m.groupValues[3]}"
        return lines.joinToString("\n")
    }

    /** Подставляет в шаблон {{дата}}, {{день}}, {{время}}, {{название}}. */
    fun fillTemplate(template: String, values: Map<String, String>): String =
        values.entries.fold(template) { acc, (k, v) -> acc.replace("{{$k}}", v) }
}
