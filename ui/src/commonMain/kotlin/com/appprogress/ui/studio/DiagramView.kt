package com.appprogress.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appprogress.studio.DiagramData
import com.appprogress.studio.DiagramNode
import com.appprogress.studio.ShapeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val DiamondShape: Shape = GenericShape { size, _ ->
    moveTo(size.width / 2, 0f)
    lineTo(size.width, size.height / 2)
    lineTo(size.width / 2, size.height)
    lineTo(0f, size.height / 2)
    close()
}

fun ShapeKind.shape(scale: Float): Shape = when (this) {
    ShapeKind.Rect -> RoundedCornerShape(4.dp)
    ShapeKind.Rounded -> RoundedCornerShape((20 * scale).dp)
    ShapeKind.Ellipse -> RoundedCornerShape(percent = 50)
    ShapeKind.Diamond -> DiamondShape
    ShapeKind.Note -> RoundedCornerShape(2.dp)
}

/**
 * Рисует диаграмму: связи на Canvas, фигуры — отдельными элементами (так у фигур есть текст и обработка мыши).
 * Экран = холст × [scale] + [pan]. [nodeModifier] добавляет к фигурам обработку мыши в редакторе.
 */
@Composable
fun DiagramView(
    diagram: DiagramData,
    scale: Float,
    pan: Offset,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    highlightId: String? = null,
    showGrid: Boolean = true,
    nodeModifier: (DiagramNode) -> Modifier = { Modifier },
) {
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.onSurfaceVariant
    val gridColor = colors.outlineVariant
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            if (showGrid) {
                val step = 24f * scale
                if (step >= 8f) {
                    var x = pan.x % step
                    while (x < size.width) {
                        var y = pan.y % step
                        while (y < size.height) {
                            drawCircle(gridColor, radius = 1.2f, center = Offset(x, y))
                            y += step
                        }
                        x += step
                    }
                }
            }
            diagram.edges.forEach { edge ->
                val a = diagram.node(edge.from) ?: return@forEach
                val b = diagram.node(edge.to) ?: return@forEach
                val start = borderPoint(a, b.centerX, b.centerY) * scale + pan
                val end = borderPoint(b, a.centerX, a.centerY) * scale + pan
                drawLine(lineColor, start, end, strokeWidth = 2f * scale.coerceAtLeast(0.6f))
                // Наконечник стрелки.
                val angle = atan2(end.y - start.y, end.x - start.x)
                val len = 12f * scale.coerceAtLeast(0.6f)
                for (side in listOf(-0.45f, 0.45f)) {
                    val a2 = angle + PI.toFloat() + side
                    drawLine(lineColor, end, Offset(end.x + len * cos(a2), end.y + len * sin(a2)), strokeWidth = 2f * scale.coerceAtLeast(0.6f))
                }
            }
        }
        diagram.nodes.forEach { node ->
            NodeBox(node, scale, pan, selected = node.id == selectedId, highlighted = node.id == highlightId, modifier = nodeModifier(node))
        }
    }
}

@Composable
private fun NodeBox(node: DiagramNode, scale: Float, pan: Offset, selected: Boolean, highlighted: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val shape = node.kind.shape(scale)
    val borderColor = when {
        selected -> colors.primary
        highlighted -> colors.secondary
        else -> Color(0x33000000)
    }
    Box(
        Modifier
            .offset { IntOffset((node.x * scale + pan.x).roundToInt(), (node.y * scale + pan.y).roundToInt()) }
            .size(with(density) { (node.w * scale).toDp() }, with(density) { (node.h * scale).toDp() })
            .then(if (node.kind == ShapeKind.Note) Modifier.shadow(3.dp, shape) else Modifier)
            .background(Color(node.color), shape)
            .border(if (selected || highlighted) 2.dp else 1.dp, borderColor, shape)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            node.text,
            fontSize = (14 * scale).sp,
            color = Color(0xFF1B1B21),
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = (if (node.kind == ShapeKind.Diamond) 26 else 10).dp * scale),
        )
    }
}

/** Точка на границе фигуры в сторону (tx, ty) — туда упирается стрелка. В координатах холста. */
fun borderPoint(node: DiagramNode, tx: Float, ty: Float): Offset {
    val cx = node.centerX
    val cy = node.centerY
    val dx = tx - cx
    val dy = ty - cy
    if (abs(dx) < 1e-3f && abs(dy) < 1e-3f) return Offset(cx, cy)
    val hw = node.w / 2
    val hh = node.h / 2
    val t = when (node.kind) {
        ShapeKind.Ellipse -> 1f / sqrt((dx / hw) * (dx / hw) + (dy / hh) * (dy / hh))
        ShapeKind.Diamond -> 1f / (abs(dx) / hw + abs(dy) / hh)
        else -> minOf(if (dx != 0f) hw / abs(dx) else Float.MAX_VALUE, if (dy != 0f) hh / abs(dy) else Float.MAX_VALUE)
    }
    return Offset(cx + dx * t, cy + dy * t)
}
