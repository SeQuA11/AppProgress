package com.appprogress.studio

import com.appprogress.core.DatabaseFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioTest {
    private val repo = StudioRepository(DatabaseFactory.inMemory(), Dispatchers.IO)

    @Test
    fun numbersAcceptRussianFormat() {
        assertEquals(1234.5, TableData.parseNumber("1 234,5"))
        assertEquals(-7.0, TableData.parseNumber(" -7 "))
        assertNull(TableData.parseNumber("abc"))
        assertNull(TableData.parseNumber(""))
    }

    @Test
    fun statsSkipEmptyCells() {
        val t = TableData.budgetSample().withCell(1, 1, "")
        val stats = t.stats(1)!!
        assertEquals(376000.0, stats.sum)
        assertEquals(3, stats.count)
        assertNull(t.stats(0))
    }

    @Test
    fun csvRoundTripDetectsSeparatorAndNumbers() {
        val csv = "Город;Население\n\"Москва; столица\";13 000 000\nКазань;1300000\n"
        val t = TableData.fromCsv(csv)
        assertEquals(listOf("Город", "Население"), t.columns.map { it.name })
        assertEquals(listOf(ColumnType.Text, ColumnType.Number), t.columns.map { it.type })
        assertEquals("Москва; столица", t.cell(0, 0))
        assertEquals(t.rows, TableData.fromCsv(t.toCsv()).rows)
    }

    @Test
    fun addAndRemoveColumnsKeepRowsAligned() {
        val t = TableData.empty(2, 2).withCell(0, 1, "x").addColumn("Новая", ColumnType.Number).removeColumn(0)
        assertEquals(listOf("Колонка 2", "Новая"), t.columns.map { it.name })
        assertEquals(listOf("x", ""), t.rows[0])
    }

    @Test
    fun chartDefaultsToTextLabelsAndNumberSeries() {
        val data = ChartSpec().extract(TableData.budgetSample())
        assertEquals(listOf("Июль", "Август", "Сентябрь", "Октябрь"), data.labels)
        assertEquals(listOf("Доходы", "Расходы"), data.series.map { it.name })
        assertEquals(97000.0, data.series[1].values[1])
    }

    @Test
    fun chartUsesChosenColumns() {
        val table = TableData.budgetSample()
        val spec = ChartSpec(labelColumnId = table.columns[0].id, valueColumnIds = listOf(table.columns[2].id))
        assertEquals(listOf("Расходы"), spec.extract(table).series.map { it.name })
    }

    @Test
    fun toggleEdgeAddsThenRemoves() {
        val (d1, a) = DiagramData().addNode(ShapeKind.Rect, 0f, 0f)
        val (d2, b) = d1.addNode(ShapeKind.Ellipse, 200f, 0f)
        val linked = d2.toggleEdge(a, b)
        assertEquals(1, linked.edges.size)
        assertEquals(0, linked.toggleEdge(b, a).edges.size, "повторное соединение убирает связь")
        assertEquals(linked, linked.toggleEdge(a, a), "петлю не создаём")
    }

    @Test
    fun removeNodeDropsItsEdges() {
        val d = DiagramData.flowchartTemplate()
        val decision = d.nodes.first { it.kind == ShapeKind.Diamond }.id
        val after = d.removeNode(decision)
        assertTrue(after.edges.none { it.from == decision || it.to == decision })
        assertEquals(d.nodes.size - 1, after.nodes.size)
    }

    @Test
    fun documentsPersistAsJson() = runBlocking {
        val id = repo.createTable("Бюджет", TableData.budgetSample())
        val chart = repo.createChart("Бюджет по месяцам", ChartSpec(tableId = id, type = ChartType.Line))
        val diagram = repo.createDiagram("Процесс", DiagramData.mindMapTemplate())

        assertEquals("Октябрь", StudioJson.table(repo.get(id)).cell(3, 0))
        assertEquals(ChartType.Line, StudioJson.chart(repo.get(chart)).type)
        assertEquals(5, StudioJson.diagram(repo.get(diagram)).nodes.size)
        assertEquals(listOf("Бюджет"), repo.observe(DocKind.Table).first().map { it.title })

        repo.delete(id)
        assertNull(repo.get(id))
    }

    @Test
    fun brokenJsonFallsBackToEmpty() {
        val broken = StudioDoc("1", DocKind.Table, "x", "{не json", 0)
        assertEquals(TableData(), StudioJson.table(broken))
    }
}
