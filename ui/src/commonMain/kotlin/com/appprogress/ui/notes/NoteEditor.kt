package com.appprogress.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appprogress.notes.Note
import com.appprogress.ui.common.formatDateTime
import kotlinx.coroutines.delay

private const val AUTOSAVE_DELAY_MS = 400L

/**
 * Редактор с автосохранением. Текст живёт в локальном состоянии редактора:
 * обновления из базы не перезаписывают то, что пользователь печатает, и курсор не прыгает.
 * Вызывать внутри key(note.id), чтобы у каждой заметки был свой экземпляр редактора.
 */
@Composable
fun NoteEditor(
    note: Note,
    onSave: (id: String, title: String, content: String) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var saved by remember { mutableStateOf(note.title to note.content) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Автосохранение: каждое нажатие перезапускает эффект, сохраняем после паузы в печати.
    LaunchedEffect(title, content) {
        if (saved == title to content) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        onSave(note.id, title, content)
        saved = title to content
    }
    // Ушли с заметки раньше, чем истекла пауза, — сохраняем немедленно.
    val latest by rememberUpdatedState(Triple(title, content, saved))
    DisposableEffect(Unit) {
        val id = note.id
        onDispose {
            val (t, c, s) = latest
            if (s != t to c) onSave(id, t, c)
        }
    }

    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (saved == title to content) "Сохранено · ${formatDateTime(note.updatedAt)}" else "Сохранение…",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (note.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (note.pinned) "Открепить" else "Закрепить",
                    tint = if (note.pinned) colors.primary else colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = colors.onSurfaceVariant)
            }
        }

        PlaceholderField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Заголовок",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        )
        PlaceholderField(
            value = content,
            onValueChange = { content = it },
            placeholder = "Начните писать…",
            style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, lineHeight = 26.sp),
            singleLine = false,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заметку?") },
            text = { Text("«${note.displayTitle}» будет удалена.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PlaceholderField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    singleLine: Boolean,
    modifier: Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                inner()
            }
        },
    )
}
