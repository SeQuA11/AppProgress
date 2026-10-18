package com.appprogress.studio

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class DocKind(val code: String, val title: String, val newTitle: String) {
    Table("table", "Таблицы", "Новая таблица"),
    Chart("chart", "Графики", "Новый график"),
    Diagram("diagram", "Диаграммы", "Новая диаграмма");

    companion object {
        fun of(code: String) = entries.first { it.code == code }
    }
}

data class StudioDoc(val id: String, val kind: DocKind, val title: String, val data: String, val updatedAt: Long)

object StudioJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun table(doc: StudioDoc?): TableData = doc?.let { runCatching { json.decodeFromString<TableData>(it.data) }.getOrNull() } ?: TableData()
    fun chart(doc: StudioDoc?): ChartSpec = doc?.let { runCatching { json.decodeFromString<ChartSpec>(it.data) }.getOrNull() } ?: ChartSpec()
    fun diagram(doc: StudioDoc?): DiagramData = doc?.let { runCatching { json.decodeFromString<DiagramData>(it.data) }.getOrNull() } ?: DiagramData()

    fun encode(table: TableData) = json.encodeToString(TableData.serializer(), table)
    fun encode(chart: ChartSpec) = json.encodeToString(ChartSpec.serializer(), chart)
    fun encode(diagram: DiagramData) = json.encodeToString(DiagramData.serializer(), diagram)
}

class StudioRepository(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = ::nowMillis,
) {
    private val q get() = db.studioQueries

    fun observe(kind: DocKind): Flow<List<StudioDoc>> = q.selectByKind(kind.code, ::toDoc).asFlow().mapToList(io)

    fun observeDoc(id: String): Flow<StudioDoc?> = q.selectById(id, ::toDoc).asFlow().mapToOneOrNull(io)

    suspend fun get(id: String): StudioDoc? = withContext(io) { q.selectById(id, ::toDoc).executeAsOneOrNull() }

    suspend fun create(kind: DocKind, title: String, data: String): String = withContext(io) {
        val id = newId()
        val now = clock()
        q.insert(id, kind.code, title.trim().ifEmpty { kind.newTitle }, data, now, now)
        id
    }

    suspend fun createTable(title: String, table: TableData = TableData.empty()) = create(DocKind.Table, title, StudioJson.encode(table))
    suspend fun createChart(title: String, spec: ChartSpec = ChartSpec()) = create(DocKind.Chart, title, StudioJson.encode(spec))
    suspend fun createDiagram(title: String, diagram: DiagramData = DiagramData()) = create(DocKind.Diagram, title, StudioJson.encode(diagram))

    suspend fun saveData(id: String, data: String) = withContext(io) { q.updateData(data, clock(), id) }

    suspend fun rename(id: String, title: String) = withContext(io) { q.rename(title.trim().ifEmpty { "Без названия" }, clock(), id) }

    suspend fun delete(id: String) = withContext(io) {
        val now = clock()
        q.softDelete(now, now, id)
    }

    private fun toDoc(id: String, kind: String, title: String, data: String, updatedAt: Long) =
        StudioDoc(id, DocKind.of(kind), title, data, updatedAt)
}
