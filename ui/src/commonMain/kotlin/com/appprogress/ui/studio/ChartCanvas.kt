package com.appprogress.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appprogress.studio.ChartData
import com.appprogress.studio.ChartType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

val ChartPalette = listOf(
    Color(0xFF4F46E5), Color(0xFF0E9F8E), Color(0xFFF59E0B), Color(0xFFE5484D), Color(0xFF3E9BFF), Color(0xFF8B5CF6),
)

/** График, нарисованный на Canvas: столбцы, линии или круговая диаграмма. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartCanvas(data: ChartData, type: ChartType, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val axisStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)

    Column(modifier) {
        // Легенда: для круговой — подписи долей, для остальных — названия рядов.
        val legend = if (type == ChartType.Pie) data.labels else data.series.map { it.name }
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            legend.forEachIndexed { i, name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(ChartPalette[i % ChartPalette.size], RoundedCornerShape(2.dp)))
                    Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            when (type) {
                ChartType.Bar -> drawAxesAndBars(data, measurer, axisStyle, colors.outlineVariant, bars = true)
                ChartType.Line -> drawAxesAndBars(data, measurer, axisStyle, colors.outlineVariant, bars = false)
                ChartType.Pie -> drawPie(data, measurer, colors.surface)
            }
        }
    }
}

/** «Красивый» шаг сетки: 1, 2 или 5 × 10ⁿ. */
private fun niceStep(range: Double, ticks: Int = 5): Double {
    if (range <= 0) return 1.0
    val raw = range / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    return mag * when {
        norm <= 1 -> 1.0
        norm <= 2 -> 2.0
        norm <= 5 -> 5.0
        else -> 10.0
    }
}

private fun shortNumber(v: Double): String = when {
    abs(v) >= 1_000_000 -> formatNumber(v / 1_000_000) + " млн"
    abs(v) >= 10_000 -> formatNumber(v / 1_000) + " тыс"
    else -> formatNumber(v)
}

private fun DrawScope.drawAxesAndBars(
    data: ChartData, measurer: TextMeasurer, style: TextStyle, gridColor: Color, bars: Boolean,
) {
    val all = data.series.flatMap { it.values }
    val step = niceStep(max(all.max(), 0.0) - min(all.min(), 0.0))
    val top = ceil(max(all.max(), 0.0) / step) * step
    val bottom = floor(min(all.min(), 0.0) / step) * step
    val left = 64f
    val bottomPad = 28f
    val plotW = size.width - left
    val plotH = size.height - bottomPad
    fun y(v: Double) = (plotH * (top - v) / (top - bottom).coerceAtLeast(1e-9)).toFloat()

    // Сетка и подписи оси Y.
    var v = bottom
    while (v <= top + step / 2) {
        val yy = y(v)
        drawLine(gridColor, Offset(left, yy), Offset(size.width, yy), strokeWidth = if (v == 0.0) 1.5f else 1f)
        val label = measurer.measure(shortNumber(v), style)
        drawText(label, topLeft = Offset(left - label.size.width - 8f, yy - label.size.height / 2))
        v += step
    }

    val n = data.labels.size
    val slot = plotW / n
    data.labels.forEachIndexed { i, text ->
        val label = measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = slot.toInt().coerceAtLeast(1)))
        drawText(label, topLeft = Offset(left + slot * i + (slot - label.size.width) / 2, plotH + 8f))
    }

    val zero = y(0.0)
    if (bars) {
        val groupW = slot * 0.7f
        val barW = groupW / data.series.size
        data.series.forEachIndexed { s, series ->
            series.values.forEachIndexed { i, value ->
                val x = left + slot * i + (slot - groupW) / 2 + barW * s
                val yv = y(value)
                drawRoundRect(
                    ChartPalette[s % ChartPalette.size],
                    topLeft = Offset(x + 2f, min(yv, zero)),
                    size = Size(barW - 4f, abs(zero - yv)),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
    } else {
        data.series.forEachIndexed { s, series ->
            val color = ChartPalette[s % ChartPalette.size]
            val points = series.values.mapIndexed { i, value -> Offset(left + slot * i + slot / 2, y(value)) }
            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(path, color, style = Stroke(width = 3f))
            points.forEach { drawCircle(color, radius = 5f, center = it); drawCircle(Color.White, radius = 2.5f, center = it) }
        }
    }
}

private fun DrawScope.drawPie(data: ChartData, measurer: TextMeasurer, background: Color) {
    val values = data.series.first().values.map { max(it, 0.0) }
    val total = values.sum()
    if (total <= 0) return
    val d = min(size.width, size.height) * 0.9f
    val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
    var start = -90f
    values.forEachIndexed { i, value ->
        val sweep = (value / total * 360).toFloat()
        drawArc(ChartPalette[i % ChartPalette.size], start, sweep, useCenter = true, topLeft = topLeft, size = Size(d, d))
        drawArc(background, start, sweep, useCenter = true, topLeft = topLeft, size = Size(d, d), style = Stroke(width = 2f))
        // Подпись процента в середине сектора, если сектор не слишком узкий.
        if (sweep > 12) {
            val mid = (start + sweep / 2) * PI / 180
            val r = d / 2 * 0.65f
            val c = Offset(topLeft.x + d / 2 + (r * cos(mid)).toFloat(), topLeft.y + d / 2 + (r * sin(mid)).toFloat())
            val label = measurer.measure("${(value / total * 100).roundToInt()}%", TextStyle(fontSize = 13.sp, color = Color.White))
            drawText(label, topLeft = c - Offset(label.size.width / 2f, label.size.height / 2f))
        }
        start += sweep
    }
}
