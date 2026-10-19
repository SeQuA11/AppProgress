package com.appprogress.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import com.appprogress.focus.TimeEntry
import com.appprogress.studio.ChartData
import com.appprogress.studio.ChartSeries
import com.appprogress.studio.ChartType
import com.appprogress.ui.AppContainer
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Учёт времени: минуты фокуса по дням за неделю и на какие задачи они ушли. */
@OptIn(ExperimentalTime::class)
@Composable
fun TimeStats(container: AppContainer) {
    val tz = TimeZone.currentSystemDefault()
    val today = Dates.today()
    val first = today.plus(DatePeriod(days = -6))
    val from = first.atStartOfDayIn(tz).toEpochMilliseconds()
    val entries by remember { container.time.observeBetween(from, Long.MAX_VALUE) }.collectAsState(initial = emptyList())
    fun dayOf(e: TimeEntry): LocalDate = Instant.fromEpochMilliseconds(e.startedAt).toLocalDateTime(tz).date

    val days = (0..6).map { first.plus(DatePeriod(days = it)) }
    val perDay = days.map { d -> entries.filter { dayOf(it) == d }.sumOf { it.minutes } }
    val byTask = entries.groupBy { it.title }.map { (title, v) -> title to v.sumOf { it.minutes } }.sortedByDescending { it.second }
    // Шесть самых затратных задач, остальное — одним сектором «Другое».
    val top = byTask.take(6) + byTask.drop(6).takeIf { it.isNotEmpty() }?.let { rest -> listOf("Другое" to rest.sumOf { it.second }) }.orEmpty()
    val week = perDay.sum()

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Учёт времени", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Сегодня: ${minutesText(perDay.last())} · за 7 дней: ${minutesText(week)}. Время набирается таймером фокуса (Pomodoro) на экране «Сегодня» и в карточках канбана.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("Пока нет ни одного отрезка фокуса за неделю", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartCard("По дням, минут", Modifier.weight(1.4f)) {
                ChartCanvas(
                    ChartData(days.map { "${RuText.weekdaysShort[it.dayOfWeek.ordinal]} ${it.day}" }, listOf(ChartSeries("Минуты фокуса", perDay))),
                    ChartType.Bar,
                    Modifier.fillMaxSize(),
                )
            }
            ChartCard("На что ушло время", Modifier.weight(1f)) {
                ChartCanvas(ChartData(top.map { it.first }, listOf(ChartSeries("Минуты", top.map { it.second }))), ChartType.Pie, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

private fun minutesText(minutes: Double): String {
    val m = minutes.toInt()
    return if (m < 60) "$m мин" else "${m / 60} ч ${m % 60} мин"
}
