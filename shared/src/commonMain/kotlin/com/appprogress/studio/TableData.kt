package com.appprogress.studio

import com.appprogress.core.newId
import kotlinx.serialization.Serializable

@Serializable
enum class ColumnType { Text, Number }

@Serializable
data class TableColumn(val id: String, val name: String, val type: ColumnType = ColumnType.Text)

/** Таблица: колонки и строки; ячейки хранятся строками, числа разбираются при расчётах. */
@Serializable
data class TableData(
    val columns: List<TableColumn> = emptyList(),
    val rows: List<List<String>> = emptyList(),
) {
    fun cell(row: Int, col: Int): String = rows.getOrNull(row)?.getOrNull(col).orEmpty()

    fun number(row: Int, col: Int): Double? = parseNumber(cell(row, col))

    /** Сумма и среднее по числовой колонке (пустые и нечисловые ячейки пропускаются). */
    fun stats(col: Int): Stats? {
        val values = rows.indices.mapNotNull { number(it, col) }
        if (values.isEmpty()) return null
        return Stats(values.sum(), values.average(), values.size)
    }

    fun withCell(row: Int, col: Int, value: String): TableData =
        copy(rows = rows.mapIndexed { r, cells ->
            if (r != row) cells else List(columns.size) { c -> if (c == col) value else cells.getOrElse(c) { "" } }
        })

    fun addRow(): TableData = copy(rows = rows + listOf(List(columns.size) { "" }))

    fun removeRow(index: Int): TableData = copy(rows = rows.filterIndexed { i, _ -> i != index })

    fun addColumn(name: String, type: ColumnType = ColumnType.Text): TableData =
        copy(columns = columns + TableColumn(newId(), name, type), rows = rows.map { it + "" })

    fun removeColumn(index: Int): TableData = copy(
        columns = columns.filterIndexed { i, _ -> i != index },
        rows = rows.map { cells -> cells.filterIndexed { i, _ -> i != index } },
    )

    fun updateColumn(index: Int, transform: (TableColumn) -> TableColumn): TableData =
        copy(columns = columns.mapIndexed { i, c -> if (i == index) transform(c) else c })

    fun toCsv(separator: Char = ';'): String = buildString {
        appendLine(columns.joinToString(separator.toString()) { csvEscape(it.name, separator) })
        rows.forEach { row -> appendLine(row.joinToString(separator.toString()) { csvEscape(it, separator) }) }
    }

    data class Stats(val sum: Double, val average: Double, val count: Int)

    companion object {
        fun empty(columns: Int = 3, rows: Int = 5): TableData = TableData(
            columns = List(columns) { TableColumn(newId(), "Колонка ${it + 1}") },
            rows = List(rows) { List(columns) { "" } },
        )

        /** «1 234,5» и «1234.5» — оба числа. */
        fun parseNumber(text: String): Double? =
            text.trim().replace(' ', ' ').replace(" ", "").replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

        /**
         * Разбор CSV: разделитель (; , или табуляция) определяется по первой строке — Excel в русской локали
         * сохраняет через «;». Колонка считается числовой, если все непустые значения в ней — числа.
         */
        fun fromCsv(text: String): TableData {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return empty()
            val header = lines.first()
            val sep = listOf('\t', ';', ',').maxBy { s -> header.count { it == s } }
            val parsed = lines.map { parseCsvLine(it, sep) }
            val width = parsed.maxOf { it.size }
            val names = parsed.first().let { h -> List(width) { h.getOrNull(it)?.ifBlank { null } ?: "Колонка ${it + 1}" } }
            val body = parsed.drop(1).map { r -> List(width) { r.getOrElse(it) { "" } } }
            val columns = names.mapIndexed { i, name ->
                val values = body.map { it[i] }.filter { it.isNotBlank() }
                val numeric = values.isNotEmpty() && values.all { parseNumber(it) != null }
                TableColumn(newId(), name, if (numeric) ColumnType.Number else ColumnType.Text)
            }
            return TableData(columns, body)
        }

        private fun parseCsvLine(line: String, sep: Char): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    quoted && ch == '"' && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                    ch == '"' -> quoted = !quoted
                    ch == sep && !quoted -> { out += cur.toString().trim(); cur.clear() }
                    else -> cur.append(ch)
                }
                i++
            }
            out += cur.toString().trim()
            return out
        }

        private fun csvEscape(value: String, sep: Char): String =
            if (value.any { it == sep || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

        /** Пример для новой таблицы-шаблона. */
        fun budgetSample(): TableData {
            val cols = listOf(
                TableColumn(newId(), "Месяц"),
                TableColumn(newId(), "Доходы", ColumnType.Number),
                TableColumn(newId(), "Расходы", ColumnType.Number),
            )
            val rows = listOf(
                listOf("Июль", "120000", "84000"),
                listOf("Август", "118000", "97000"),
                listOf("Сентябрь", "125000", "79000"),
                listOf("Октябрь", "131000", "88000"),
            )
            return TableData(cols, rows)
        }
    }
}
