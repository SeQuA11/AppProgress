package com.appprogress.ui.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Правки текста для кнопок панели форматирования. Все функции возвращают новое значение поля. */
object TextEditing {

    /** Обрамляет выделение: **жирный**, *курсив*. Без выделения ставит пару знаков и курсор между ними. */
    fun wrap(v: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
        val s = v.selection.min
        val e = v.selection.max
        val selected = v.text.substring(s, e)
        val text = v.text.substring(0, s) + prefix + selected + suffix + v.text.substring(e)
        val sel = if (selected.isEmpty()) TextRange(s + prefix.length) else TextRange(s + prefix.length, e + prefix.length)
        return TextFieldValue(text, sel)
    }

    /** Диапазон строк, затронутых выделением: (начало первой строки, конец последней). */
    private fun lineBounds(text: String, sel: TextRange): Pair<Int, Int> {
        val start = text.lastIndexOf('\n', (sel.min - 1).coerceAtLeast(0)).let { if (sel.min == 0) 0 else it + 1 }
        val endIdx = text.indexOf('\n', sel.max)
        return start to if (endIdx < 0) text.length else endIdx
    }

    private val linePrefixes = Regex("""^(#{1,6} |[-*] \[[ xXхХ]] |[-*+] |> |\d+[.)] )""")

    /**
     * Ставит или снимает префикс у выделенных строк: «# », «- », «- [ ] », «> ».
     * Если у строки другой префикс (например, «- » вместо «- [ ] »), он заменяется.
     */
    fun toggleLinePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
        val (start, end) = lineBounds(v.text, v.selection)
        val lines = v.text.substring(start, end).split('\n')
        val allHave = lines.all { it.startsWith(prefix) }
        val changed = lines.joinToString("\n") { line ->
            val bare = line.replaceFirst(linePrefixes, "")
            if (allHave) bare else prefix + bare
        }
        val text = v.text.substring(0, start) + changed + v.text.substring(end)
        return TextFieldValue(text, TextRange(start + changed.length))
    }

    fun insert(v: TextFieldValue, snippet: String): TextFieldValue {
        val text = v.text.substring(0, v.selection.min) + snippet + v.text.substring(v.selection.max)
        return TextFieldValue(text, TextRange(v.selection.min + snippet.length))
    }

    /** Текст текущей строки (где курсор) и её границы. */
    fun currentLine(v: TextFieldValue): Triple<Int, Int, String> {
        val (start, end) = lineBounds(v.text, TextRange(v.selection.start))
        return Triple(start, end, v.text.substring(start, end))
    }

    fun replaceRange(v: TextFieldValue, start: Int, end: Int, replacement: String): TextFieldValue {
        val text = v.text.substring(0, start) + replacement + v.text.substring(end)
        return TextFieldValue(text, TextRange(start + replacement.length))
    }

    private val listItem = Regex("""^(\s*)([-*] \[[ xXхХ]] |[-*+] |(\d+)([.)]) |> )(.*)$""")

    /**
     * Продолжение списков по Enter: после «- [ ] дело» новая строка начинается с «- [ ] »,
     * после «1. пункт» — с «2. ». Enter на пустом пункте завершает список.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val cursor = new.selection.start
        val isSingleNewline = new.selection.collapsed && new.text.length == old.text.length + 1 &&
            cursor > 0 && new.text[cursor - 1] == '\n' && old.selection.collapsed && old.selection.start == cursor - 1
        if (!isSingleNewline) return new
        val prevStart = new.text.lastIndexOf('\n', cursor - 2).let { if (cursor - 2 < 0) 0 else it + 1 }
        val prevLine = new.text.substring(prevStart, cursor - 1)
        val m = listItem.find(prevLine) ?: return new
        val (indent, marker, number, delimiter, rest) = m.destructured
        if (rest.isBlank()) {
            // Пустой пункт: убираем его маркер и не продолжаем список.
            val text = new.text.substring(0, prevStart) + new.text.substring(cursor)
            return TextFieldValue(text, TextRange(prevStart))
        }
        val nextMarker = when {
            marker.contains('[') -> "- [ ] "
            number.isNotEmpty() -> "${number.toInt() + 1}$delimiter "
            else -> marker
        }
        val insertion = indent + nextMarker
        val text = new.text.substring(0, cursor) + insertion + new.text.substring(cursor)
        return TextFieldValue(text, TextRange(cursor + insertion.length))
    }
}
