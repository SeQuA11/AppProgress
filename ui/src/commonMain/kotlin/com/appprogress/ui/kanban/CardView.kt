package com.appprogress.ui.kanban

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.core.Dates
import com.appprogress.kanban.Card
import com.appprogress.kanban.Priority

fun Priority.color(): Color = when (this) {
    Priority.High -> Color(0xFFE5484D)
    Priority.Medium -> Color(0xFFF5A524)
    Priority.Low -> Color(0xFF3E9BFF)
    Priority.None -> Color.Transparent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardView(card: Card, modifier: Modifier = Modifier, elevated: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = if (elevated) 8.dp else 0.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.title, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (card.description.isNotBlank()) {
                Text(
                    card.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val hasMeta = card.priority != Priority.None || card.dueDate != null || card.labels.isNotEmpty()
            if (hasMeta) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (card.priority != Priority.None) PriorityTag(card.priority)
                    card.dueDate?.let { due ->
                        val overdue = card.isOverdue(Dates.today())
                        Tag(
                            "до ${Dates.format(due)}",
                            background = if (overdue) colors.error.copy(alpha = 0.14f) else colors.surfaceVariant,
                            content = if (overdue) colors.error else colors.onSurfaceVariant,
                        )
                    }
                    card.labels.forEach { Tag(it, colors.primaryContainer, colors.onPrimaryContainer) }
                }
            }
        }
    }
}

@Composable
private fun PriorityTag(priority: Priority) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.background(colors.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(priority.color(), CircleShape))
        Text(priority.title, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun Tag(text: String, background: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier.background(background, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
