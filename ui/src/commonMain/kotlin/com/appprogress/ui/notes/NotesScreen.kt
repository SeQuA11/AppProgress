package com.appprogress.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.notes.Note
import com.appprogress.notes.NotesRepository
import com.appprogress.ui.common.formatDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun NotesScreen(repo: NotesRepository, scope: CoroutineScope) {
    val notes by remember(repo) { repo.observeAll() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val visible = remember(notes, query) { notes.filter { it.matches(query) } }
    val selected = notes.firstOrNull { it.id == selectedId }

    // Если выбранной заметки нет (первый запуск или её удалили), выбираем верхнюю.
    LaunchedEffect(notes) {
        if (notes.none { it.id == selectedId }) selectedId = notes.firstOrNull()?.id
    }

    val createNote: () -> Unit = {
        scope.launch {
            query = ""
            selectedId = repo.create()
        }
    }

    Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.N) {
                createNote(); true
            } else false
        },
    ) {
        Column(Modifier.width(320.dp).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Заметки",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = createNote) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("Новая", Modifier.padding(start = 6.dp))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            when {
                notes.isEmpty() -> Hint("Пока нет ни одной заметки")
                visible.isEmpty() -> Hint("Ничего не найдено")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(visible, key = { it.id }) { note ->
                        NoteListItem(note, selected = note.id == selectedId, onClick = { selectedId = note.id })
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (selected != null) {
                // key: при смене заметки старый редактор целиком уходит (и досохраняет текст), новый создаётся с нуля.
                key(selected.id) {
                    NoteEditor(
                        note = selected,
                        onSave = { id, title, content -> scope.launch { repo.updateText(id, title, content) } },
                        onTogglePin = { scope.launch { repo.setPinned(selected.id, !selected.pinned) } },
                        onDelete = { scope.launch { repo.delete(selected.id) } },
                    )
                }
            } else {
                EmptyEditor(onCreate = createNote)
            }
        }
    }
}

@Composable
private fun NoteListItem(note: Note, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primaryContainer else colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.displayTitle,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
            if (note.pinned) Icon(Icons.Filled.Star, "Закреплена", tint = colors.primary, modifier = Modifier.size(16.dp))
        }
        val preview = note.content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .drop(if (note.title.isBlank()) 1 else 0).firstOrNull().orEmpty()
        if (preview.isNotEmpty()) {
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatDateTime(note.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
}

@Composable
private fun EmptyEditor(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Выберите заметку или создайте новую", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Button(onClick = onCreate) { Text("Создать заметку") }
        Text(
            "или нажмите Ctrl+N",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
