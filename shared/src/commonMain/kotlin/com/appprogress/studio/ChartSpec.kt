package com.appprogress.studio

import kotlinx.serialization.Serializable

@Serializable
enum class ChartType(val title: String) { Bar("Столбцы"), Line("Линии"), Pie("Круговая") }

/** Настройки графика: из какой таблицы и каких колонок брать данные. Сами данные живут в таблице. */
@Serializable
data class ChartSpec(
    val tableId: String? = null,
    val type: ChartType = ChartType.Bar,
    val labelColumnId: String? = null,
    val valueColumnIds: List<String> = emptyList(),
)

data class ChartSeries(val name: String, val values: List<Double>)

data class ChartData(val labels: List<String>, val series: List<ChartSeries>) {
    val isEmpty: Boolean get() = labels.isEmpty() || series.isEmpty()
}

/**
 * Достаёт из таблицы подписи и ряды значений. Если колонки не выбраны, берёт первую текстовую
 * колонку как подписи и все числовые как ряды — чтобы график сразу что-то показал.
 */
fun ChartSpec.extract(table: TableData): ChartData {
    val labelIndex = table.columns.indexOfFirst { it.id == labelColumnId }.takeIf { it >= 0 }
        ?: table.columns.indexOfFirst { it.type == ColumnType.Text }.takeIf { it >= 0 }
    val valueIndexes = valueColumnIds.mapNotNull { id -> table.columns.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        .ifEmpty { table.columns.indices.filter { table.columns[it].type == ColumnType.Number } }
    val rowIndexes = table.rows.indices.filter { r ->
        valueIndexes.any { table.number(r, it) != null } || (labelIndex != null && table.cell(r, labelIndex).isNotBlank())
    }
    val labels = rowIndexes.map { r -> labelIndex?.let { table.cell(r, it) }?.ifBlank { null } ?: "${r + 1}" }
    val series = valueIndexes.map { c -> ChartSeries(table.columns[c].name, rowIndexes.map { r -> table.number(r, c) ?: 0.0 }) }
    return ChartData(labels, series)
}
