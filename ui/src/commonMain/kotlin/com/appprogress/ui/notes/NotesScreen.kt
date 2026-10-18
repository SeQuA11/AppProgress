package com.appprogress.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.calendar.CalendarRepository
import com.appprogress.core.Dates
import com.appprogress.kanban.KanbanRepository
import com.appprogress.notes.DailyNote
import com.appprogress.notes.LinkKind
import com.appprogress.notes.Note
import com.appprogress.notes.NoteFolder
import com.appprogress.notes.NoteSummary
import com.appprogress.notes.NotesRepository
import com.appprogress.ui.AppContainer
import com.appprogress.ui.common.ConfirmDialog
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.NavTarget
import com.appprogress.ui.common.TextInputDialog
import com.appprogress.ui.common.formatDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Что показывает список заметок. */
sealed interface NotesFilter {
    data object All : NotesFilter
    data object NoFolder : NotesFilter
    data class Folder(val id: String) : NotesFilter
    data class Tag(val name: String) : NotesFilter
    data object Trash : NotesFilter
}

private sealed interface FolderDialog {
    data class Create(val parentId: String?) : FolderDialog
    data class Rename(val folder: NoteFolder) : FolderDialog
    data class Delete(val folder: NoteFolder) : FolderDialog
}

@Composable
fun NotesScreen(container: AppContainer) {
    val repo = container.notes
    val scope = container.scope
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val summaries by remember { repo.observeSummaries() }.collectAsState(initial = emptyList())
    val folders by remember { repo.observeFolders() }.collectAsState(initial = emptyList())
    val trash by remember { repo.observeTrash() }.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf<NotesFilter>(NotesFilter.All) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var folderDialog by remember { mutableStateOf<FolderDialog?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { repo.maintenance() }
    // Переход сюда из другого раздела (ссылка в календаре, карточке, палитре команд).
    LaunchedEffect(navigator.pending) {
        navigator.consume<NavTarget.Note>()?.let { filter = NotesFilter.All; query = ""; selectedId = it.id }
    }

    // Полнотекстовый поиск с небольшой задержкой, чтобы не искать на каждую букву.
    val searchIds by produceState<List<String>?>(null, query) {
        if (query.isBlank()) { value = null; return@produceState }
        delay(150)
        value = repo.search(query)
    }
    val base = when (val f = filter) {
        NotesFilter.All -> summaries
        NotesFilter.NoFolder -> summaries.filter { it.folderId == null }
        is NotesFilter.Folder -> summaries.filter { it.folderId == f.id }
        is NotesFilter.Tag -> summaries.filter { f.name in it.tags }
        NotesFilter.Trash -> trash
    }
    val visible = searchIds?.let { ids ->
        val order = ids.withIndex().associate { it.value to it.index }
        base.filter { it.id in order || it.title.contains(query.trim(), ignoreCase = true) }.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    } ?: base

    LaunchedEffect(visible, filter) {
        if (filter != NotesFilter.Trash && visible.none { it.id == selectedId } && summaries.none { it.id == selectedId }) {
            selectedId = visible.firstOrNull()?.id
        }
    }
    val selected by produceState<Note?>(null, selectedId, summaries) { value = selectedId?.let { repo.get(it) } }
    val resolver by produceState(LinkResolver.Empty, selectedId) { value = LinkResolver.load(container, summaries) }

    val currentFolderId = (filter as? NotesFilter.Folder)?.id
    val create: (String, String) -> Unit = { t, c ->
        scope.launch {
            query = ""
            if (filter == NotesFilter.Trash || filter is NotesFilter.Tag) filter = NotesFilter.All
            selectedId = repo.create(t, c, folderId = currentFolderId)
        }
    }
    val openDaily: () -> Unit = {
        scope.launch {
            val today = Dates.today()
            selectedId = repo.openOrCreateDaily(today) {
                DailyNote.build(today, CalendarRepository(container.db, container.io), KanbanRepository(container.db, container.io))
            }
            filter = NotesFilter.All
            query = ""
        }
    }
    val onLink: (NoteLink) -> Unit = { link ->
        when (link) {
            is NoteLink.Wiki -> scope.launch { selectedId = repo.openOrCreateByTitle(link.title); filter = NotesFilter.All }
            is NoteLink.App -> when (link.kind) {
                LinkKind.Note -> selectedId = link.id
                LinkKind.Card -> navigator.open(NavTarget.Card(link.id))
                LinkKind.Mail -> navigator.open(NavTarget.Mail(link.id))
                LinkKind.Event -> scope.launch {
                    val date = Dates.parseIso(link.date) ?: CalendarRepository(container.db, container.io).all().firstOrNull { it.id == link.id }?.date
                    date?.let { navigator.open(NavTarget.Day(it)) }
                }
            }
            is NoteLink.File -> openAttachment(link.name)
            is NoteLink.Url -> runCatching { uriHandler.openUri(link.url) }
        }
    }
    val templates = builtInTemplates + run {
        val tplFolder = folders.firstOrNull { it.name.equals(NotesRepository.TEMPLATES_FOLDER, ignoreCase = true) }
        summaries.filter { tplFolder != null && it.folderId == tplFolder.id }.map { s -> NoteTemplate(s.displayTitle, s.displayTitle, s.id) }
    }

    Row(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.N) { create("", ""); true } else false
        },
    ) {
        NavPanel(
            summaries = summaries,
            folders = folders,
            trashCount = trash.size,
            filter = filter,
            onFilter = { filter = it; query = "" },
            onDaily = openDaily,
            onFolderDialog = { folderDialog = it },
            onImport = { scope.launch { message = importNotesFromDisk(repo) } },
            onExport = { scope.launch { message = exportNotesToFolder(repo) } },
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.width(300.dp).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    filterTitle(filter, folders),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (filter == NotesFilter.Trash) {
                    TextButton(enabled = trash.isNotEmpty(), onClick = { confirmEmptyTrash = true }) { Text("Очистить") }
                } else {
                    NewNoteButton(templates, onEmpty = { create("", "") }, onTemplate = { t ->
                        if (t in builtInTemplates) {
                            val (title, body) = t.fill()
                            create(title, body)
                        } else {
                            // Свой шаблон — заметка из папки «Шаблоны»: копируем её текст.
                            scope.launch { repo.get(t.body)?.let { tpl -> create(tpl.title, tpl.content) } }
                        }
                    })
                }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск по всем словам") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
            )
            message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { message = null })
            }
            when {
                filter == NotesFilter.Trash && trash.isEmpty() -> Hint("Корзина пуста. Удалённые заметки хранятся здесь 30 дней.")
                summaries.isEmpty() && filter == NotesFilter.All -> Hint("Пока нет ни одной заметки")
                visible.isEmpty() -> Hint("Ничего не найдено")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(visible, key = { it.id }) { note ->
                        if (filter == NotesFilter.Trash) {
                            TrashRow(note, onRestore = { scope.launch { repo.restore(note.id) } }, onDelete = { scope.launch { repo.deleteForever(note.id) } })
                        } else {
                            NoteListItem(note, selected = note.id == selectedId, onClick = { selectedId = note.id })
                        }
                    }
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val note = selected
            when {
                filter == NotesFilter.Trash -> Hint("Восстановите заметку, чтобы открыть её", Modifier.align(Alignment.Center))
                note != null -> key(note.id) {
                    NoteEditor(note, container, folders, summaries, resolver, onOpenLink = onLink, onOpenNote = { selectedId = it })
                }
                else -> EmptyEditor(onCreate = { create("", "") }, onDaily = openDaily)
            }
        }
    }

    when (val d = folderDialog) {
        null -> Unit
        is FolderDialog.Create -> TextInputDialog(
            title = if (d.parentId == null) "Новая папка" else "Новая подпапка", confirmLabel = "Создать",
            onConfirm = { name -> folderDialog = null; scope.launch { filter = NotesFilter.Folder(repo.createFolder(name, d.parentId)) } },
            onDismiss = { folderDialog = null },
        )
        is FolderDialog.Rename -> TextInputDialog(
            title = "Переименовать папку", initial = d.folder.name,
            onConfirm = { name -> folderDialog = null; scope.launch { repo.renameFolder(d.folder.id, name) } },
            onDismiss = { folderDialog = null },
        )
        is FolderDialog.Delete -> ConfirmDialog(
            title = "Удалить папку «${d.folder.name}»?",
            text = "Заметки и вложенные папки не удалятся — они переместятся на уровень выше.",
            onConfirm = { scope.launch { repo.deleteFolder(d.folder); filter = NotesFilter.All } },
            onDismiss = { folderDialog = null },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Очистить корзину?", text = "${trash.size} заметок будут удалены навсегда.",
            onConfirm = { scope.launch { trash.forEach { repo.deleteForever(it.id) } } },
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

private fun filterTitle(filter: NotesFilter, folders: List<NoteFolder>) = when (filter) {
    NotesFilter.All -> "Все заметки"
    NotesFilter.NoFolder -> "Без папки"
    is NotesFilter.Folder -> folders.firstOrNull { it.id == filter.id }?.name ?: "Папка"
    is NotesFilter.Tag -> "#${filter.name}"
    NotesFilter.Trash -> "Корзина"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavPanel(
    summaries: List<NoteSummary>,
    folders: List<NoteFolder>,
    trashCount: Int,
    filter: NotesFilter,
    onFilter: (NotesFilter) -> Unit,
    onDaily: () -> Unit,
    onFolderDialog: (FolderDialog) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.width(210.dp).fillMaxHeight().background(colors.surface).verticalScroll(rememberScrollState()).padding(10.dp)) {
        NavItem("Все заметки", summaries.size, filter == NotesFilter.All) { onFilter(NotesFilter.All) }
        NavItem("Без папки", summaries.count { it.folderId == null }, filter == NotesFilter.NoFolder) { onFilter(NotesFilter.NoFolder) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onDaily).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DateRange, null, Modifier.size(16.dp), tint = colors.primary)
            Text("Дневник: сегодня", color = colors.primary, modifier = Modifier.padding(start = 8.dp))
        }

        Row(Modifier.padding(top = 14.dp, start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Папки", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { onFolderDialog(FolderDialog.Create(null)) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Add, "Новая папка", Modifier.size(16.dp))
            }
        }
        // Дерево папок: сначала верхний уровень, вложенные — с отступом.
        fun children(parent: String?): List<NoteFolder> = folders.filter { it.parentId == parent }
        @Composable
        fun tree(parent: String?, depth: Int) {
            children(parent).forEach { f ->
                FolderItem(
                    folder = f, depth = depth,
                    count = summaries.count { it.folderId == f.id },
                    selected = filter == NotesFilter.Folder(f.id),
                    onClick = { onFilter(NotesFilter.Folder(f.id)) },
                    onDialog = onFolderDialog,
                )
                if (depth < 6) tree(f.id, depth + 1)
            }
        }
        tree(null, 0)
        if (folders.isEmpty()) Text("Папок нет", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))

        val tags = summaries.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(30)
        if (tags.isNotEmpty()) {
            Text("Теги", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, start = 10.dp, bottom = 6.dp))
            FlowRow(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { (tag, count) ->
                    val on = filter == NotesFilter.Tag(tag)
                    Text(
                        "#$tag $count",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) colors.onPrimary else colors.secondary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (on) colors.secondary else colors.secondary.copy(alpha = 0.1f))
                            .clickable { onFilter(if (on) NotesFilter.All else NotesFilter.Tag(tag)) }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.outlineVariant)
        NavItem("Корзина", trashCount, filter == NotesFilter.Trash) { onFilter(NotesFilter.Trash) }
        TextButton(onClick = onImport) { Text("Импорт (Obsidian, Notion)…", style = MaterialTheme.typography.labelMedium) }
        TextButton(onClick = onExport) { Text("Экспорт в .md…", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun NavItem(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun FolderItem(folder: NoteFolder, depth: Int, count: Int, selected: Boolean, onClick: () -> Unit, onDialog: (FolderDialog) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(start = (10 + depth * 14).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.MoreVert, "Действия с папкой", Modifier.size(14.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Новая подпапка") }, onClick = { menu = false; onDialog(FolderDialog.Create(folder.id)) })
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onDialog(FolderDialog.Rename(folder)) })
                DropdownMenuItem(text = { Text("Удалить папку", color = colors.error) }, onClick = { menu = false; onDialog(FolderDialog.Delete(folder)) })
            }
        }
    }
}

@Composable
private fun NewNoteButton(templates: List<NoteTemplate>, onEmpty: () -> Unit, onTemplate: (NoteTemplate) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row {
        FilledTonalButton(onClick = onEmpty) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text("Новая", Modifier.padding(start = 4.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, "Из шаблона") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Text("Из шаблона", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                templates.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { menu = false; onTemplate(t) }) }
                Text(
                    "Свои шаблоны — заметки в папке «${NotesRepository.TEMPLATES_FOLDER}»",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteListItem(note: NoteSummary, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primaryContainer else colors.background)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.encrypted) Icon(Icons.Filled.Lock, "Зашифрована", Modifier.size(14.dp).padding(end = 2.dp), tint = colors.onSurfaceVariant)
            Text(
                note.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f), color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
            if (note.pinned) Icon(Icons.Filled.Star, "Закреплена", tint = colors.primary, modifier = Modifier.size(15.dp))
        }
        if (note.previewLine.isNotEmpty()) {
            Text(note.previewLine, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Text(formatDateTime(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            note.date?.let { Text("  · на ${Dates.format(it)}", style = MaterialTheme.typography.labelSmall, color = colors.primary) }
        }
        if (note.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                note.tags.take(4).forEach { Text("#$it", style = MaterialTheme.typography.labelSmall, color = colors.secondary) }
            }
        }
    }
}

@Composable
private fun TrashRow(note: NoteSummary, onRestore: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(note.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        note.deletedAt?.let { Text("Удалена ${formatDateTime(it)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
        Row {
            TextButton(onClick = onRestore) { Text("Восстановить") }
            TextButton(onClick = onDelete) { Text("Удалить навсегда", color = colors.error) }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(8.dp))
}

@Composable
private fun EmptyEditor(onCreate: () -> Unit, onDaily: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Выберите заметку или создайте новую", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate) { Text("Создать заметку") }
            TextButton(onClick = onDaily) { Text("Открыть дневник на сегодня") }
        }
        Text("Ctrl+N — новая заметка, Ctrl+E — правка/просмотр", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
