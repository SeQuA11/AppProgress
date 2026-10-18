package com.appprogress.studio

import com.appprogress.core.newId
import kotlinx.serialization.Serializable

@Serializable
enum class ShapeKind(val title: String) {
    Rect("Прямоугольник"), Rounded("Скруглённый"), Ellipse("Овал"), Diamond("Ромб"), Note("Стикер")
}

/** Узел диаграммы. Координаты — в «единицах холста» (при масштабе 100% = пиксели). */
@Serializable
data class DiagramNode(
    val id: String,
    val kind: ShapeKind,
    val x: Float,
    val y: Float,
    val w: Float = 160f,
    val h: Float = 72f,
    val text: String = "",
    val color: Long = DEFAULT_COLORS[0],
) {
    val centerX get() = x + w / 2
    val centerY get() = y + h / 2

    companion object {
        /** Пастельные цвета заливки (ARGB). */
        val DEFAULT_COLORS = listOf(0xFFE4E2FFL, 0xFFD6F5EFL, 0xFFFFF1C2L, 0xFFFFDADBL, 0xFFDDEBFFL, 0xFFEDEDF4L)
    }
}

@Serializable
data class DiagramEdge(val id: String, val from: String, val to: String, val label: String = "")

@Serializable
data class DiagramData(val nodes: List<DiagramNode> = emptyList(), val edges: List<DiagramEdge> = emptyList()) {

    fun node(id: String) = nodes.firstOrNull { it.id == id }

    fun addNode(kind: ShapeKind, x: Float, y: Float, text: String = kind.title): Pair<DiagramData, String> {
        val (w, h) = when (kind) {
            ShapeKind.Diamond -> 150f to 96f
            ShapeKind.Ellipse -> 150f to 72f
            ShapeKind.Note -> 150f to 110f
            else -> 160f to 72f
        }
        val color = if (kind == ShapeKind.Note) DiagramNode.DEFAULT_COLORS[2] else DiagramNode.DEFAULT_COLORS[0]
        val node = DiagramNode(newId(), kind, x, y, w, h, text, color)
        return copy(nodes = nodes + node) to node.id
    }

    fun updateNode(id: String, transform: (DiagramNode) -> DiagramNode) =
        copy(nodes = nodes.map { if (it.id == id) transform(it) else it })

    fun moveNode(id: String, dx: Float, dy: Float) = updateNode(id) { it.copy(x = it.x + dx, y = it.y + dy) }

    /** Удаляет узел вместе со всеми его связями. */
    fun removeNode(id: String) = copy(nodes = nodes.filter { it.id != id }, edges = edges.filter { it.from != id && it.to != id })

    /** Соединяет два узла. Если связь уже есть (в любую сторону) — убирает её: повторное соединение = удаление. */
    fun toggleEdge(from: String, to: String): DiagramData {
        if (from == to || node(from) == null || node(to) == null) return this
        val existing = edges.firstOrNull { (it.from == from && it.to == to) || (it.from == to && it.to == from) }
        return if (existing != null) copy(edges = edges - existing) else copy(edges = edges + DiagramEdge(newId(), from, to))
    }

    /** Прямоугольник, в который помещаются все узлы: (minX, minY, maxX, maxY). */
    fun bounds(): FloatArray? {
        if (nodes.isEmpty()) return null
        return floatArrayOf(nodes.minOf { it.x }, nodes.minOf { it.y }, nodes.maxOf { it.x + it.w }, nodes.maxOf { it.y + it.h })
    }

    companion object {
        fun flowchartTemplate(): DiagramData {
            var d = DiagramData()
            val ids = mutableListOf<String>()
            fun add(kind: ShapeKind, x: Float, y: Float, text: String) { val (nd, id) = d.addNode(kind, x, y, text); d = nd; ids += id }
            add(ShapeKind.Ellipse, 205f, 20f, "Начало")
            add(ShapeKind.Rect, 200f, 140f, "Собрать данные")
            add(ShapeKind.Diamond, 205f, 260f, "Всё готово?")
            add(ShapeKind.Rect, 440f, 272f, "Доработать")
            add(ShapeKind.Ellipse, 205f, 420f, "Конец")
            d = d.toggleEdge(ids[0], ids[1]).toggleEdge(ids[1], ids[2]).toggleEdge(ids[2], ids[4]).toggleEdge(ids[2], ids[3])
            return d.copy(edges = d.edges + DiagramEdge(newId(), ids[3], ids[1]))
        }

        fun mindMapTemplate(): DiagramData {
            var d = DiagramData()
            val (d0, center) = d.addNode(ShapeKind.Rounded, 300f, 200f, "Главная идея")
            d = d0.updateNode(center) { it.copy(color = DiagramNode.DEFAULT_COLORS[3]) }
            listOf(60f to 60f, 560f to 60f, 60f to 340f, 560f to 340f).forEachIndexed { i, (x, y) ->
                val (nd, id) = d.addNode(ShapeKind.Rounded, x, y, "Мысль ${i + 1}")
                d = nd.updateNode(id) { it.copy(color = DiagramNode.DEFAULT_COLORS[1 + i % 4]) }.toggleEdge(center, id)
            }
            return d
        }
    }
}
