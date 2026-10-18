package com.appprogress.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appprogress.core.Dates
import com.appprogress.notes.LinkKind
import com.appprogress.notes.Note
import com.appprogress.notes.NoteCrypto
import com.appprogress.notes.NoteFolder
import com.appprogress.notes.NoteMarkup
import com.appprogress.notes.NoteSummary
import com.appprogress.notes.NoteVersion
import com.appprogress.ui.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTOSAVE_DELAY_MS = 400L

private sealed interface EditorDialog {
    data object Versions : EditorDialog
    data object Encrypt : EditorDialog
    data object Date : EditorDialog
    data object Link : EditorDialog
    data class ToTask(val start: Int, val end: Int, val title: String, val selection: Boolean) : EditorDialog
}

/**
 * Редактор заметки. Текст живёт в локальном состоянии: обновления из базы не сбивают курсор.
 * Вызывать внутри key(note.id), чтобы у каждой заметки был свой экземпляр.
 * Для зашифрованной заметки текст расшифровывается в памяти после ввода пароля и шифруется обратно при сохранении.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(
    note: Note,
    container: AppContainer,
    folders: List<NoteFolder>,
    summaries: List<NoteSummary>,
    resolver: LinkResolver,
    onOpenLink: (NoteLink) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val repo = container.notes
    val scope = container.scope
    var password by remember { mutableStateOf<String?>(null) }
    val locked = note.encrypted && password == null
    var title by remember { mutableStateOf(note.title) }
    var value by remember { mutableStateOf(TextFieldValue(if (note.encrypted) "" else note.content)) }
    var saved by remember { mutableStateOf(title to value.text) }
    var preview by remember { mutableStateOf(note.content.isNotBlank() && !note.encrypted) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    fun persist(t: String, text: String) {
        val pw = password
        scope.launch {
            val stored = if (pw != null) withContext(Dispatchers.Default) { NoteCrypto.encrypt(text, pw) } else text
            repo.updateText(note.id, t, stored)
        }
    }

    // Автосохранение после паузы в печати; при уходе с заметки — сразу.
    LaunchedEffect(title, value.text) {
        if (locked || saved == title to value.text) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        persist(title, value.text)
        saved = title to value.text
    }
    val latest by rememberUpdatedState(Triple(title, value.text, saved))
    val lockedNow by rememberUpdatedState(locked)
    DisposableEffect(Unit) {
        onDispose {
            val (t, c, s) = latest
            if (!lockedNow && s != t to c) persist(t, c)
        }
    }

    val backlinks by produceState(emptyList<NoteSummary>(), note.id, summaries) {
        val ids = repo.backlinks(note).toSet()
        this.value = summaries.filter { it.id in ids }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 18.dp)) {
        Header(
            note = note,
            folders = folders,
            status = if (saved == title to value.text) "Сохранено" else "Сохранение…",
            preview = preview,
            locked = locked,
            onTogglePreview = { preview = !preview },
            onFolder = { f -> scope.launch { repo.setFolder(note.id, f) } },
            onDate = { dialog = EditorDialog.Date },
            onPin = { scope.launch { repo.setPinned(note.id, !note.pinned) } },
            onVersions = { dialog = EditorDialog.Versions },
            onEncrypt = { dialog = EditorDialog.Encrypt },
            onDecrypt = {
                scope.launch {
                    repo.setEncryptedContent(note.id, value.text, encrypted = false)
                    password = null
                }
            },
            onTrash = { scope.launch { repo.delete(note.id) } },
        )

        if (locked) {
            LockedView(error = unlockError) { pw ->
                val plain = NoteCrypto.decrypt(note.content, pw)
                if (plain == null) unlockError = "Неверный пароль" else {
                    password = pw
                    value = TextFieldValue(plain)
                    saved = title to plain
                    preview = true
                }
            }
            return@Column
        }

        BasicTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) Text("Заголовок", style = MaterialTheme.typography.headlineMedium.copy(color = colors.onSurfaceVariant))
                    inner()
                }
            },
        )

        if (!preview) {
            Toolbar(
                onWrap = { p, s -> value = TextEditing.wrap(value, p, s) },
                onPrefix = { value = TextEditing.toggleLinePrefix(value, it) },
                onInsert = { value = TextEditing.insert(value, it) },
                onLink = { dialog = EditorDialog.Link },
                onAttach = {
                    val files = pickFilesToAttach()
                    if (files.isNotEmpty()) {
                        val md = files.joinToString("\n") { (name, original) ->
                            if (original.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")) "![$original](attachment:$name)"
                            else "[$original](attachment:$name)"
                        }
                        value = TextEditing.insert(value, md + "\n")
                    }
                },
                onToTask = {
                    val sel = value.selection
                    dialog = if (!sel.collapsed) {
                        EditorDialog.ToTask(sel.min, sel.max, value.text.substring(sel.min, sel.max).trim(), selection = true)
                    } else {
                        val (s, e, line) = TextEditing.currentLine(value)
                        EditorDialog.ToTask(s, e, NoteMarkup.plain(line).trim(), selection = false)
                    }
                },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (preview) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (value.text.isBlank()) {
                        Text("Пустая заметка. Нажмите «Править» или Ctrl+E.", color = colors.onSurfaceVariant)
                    }
                    MarkdownView(
                        text = value.text,
                        resolve = resolver::resolve,
                        onLink = onOpenLink,
                        onToggleCheckbox = { i -> value = value.copy(text = NoteMarkup.toggleCheckbox(value.text, i)) },
                        loadImage = ::loadAttachmentImage,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp),
                    )
                    Backlinks(backlinks, onOpenNote)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = value,
                        onValueChange = { new -> value = TextEditing.continueList(value, new) },
                        textStyle = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        visualTransformation = remember(colors) { MarkdownHighlight(colors) },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.B -> { value = TextEditing.wrap(value, "**"); true }
                                Key.I -> { value = TextEditing.wrap(value, "*"); true }
                                Key.E -> { preview = true; true }
                                Key.V -> clipboardImagePng()?.let { png ->
                                    value = TextEditing.insert(value, "![скриншот](attachment:${saveAttachment(png, "png")})\n")
                                    true
                                } ?: false
                                else -> false
                            }
                        },
                        decorationBox = { inner ->
                            Box {
                                if (value.text.isEmpty()) {
                                    Text(
                                        "Начните писать… Markdown: # заголовок, **жирный**, - [ ] дело, [[ссылка]], #тег",
                                        style = TextStyle(fontSize = 16.sp, color = colors.onSurfaceVariant),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Backlinks(backlinks, onOpenNote)
                }
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        EditorDialog.Versions -> {
            val versions by produceState(emptyList<NoteVersion>()) { this.value = repo.versions(note.id) }
            VersionsDialog(versions, onRestore = { v ->
                dialog = null
                scope.launch { repo.restoreVersion(note.id, v) }
                // Версии зашифрованной заметки тоже зашифрованы.
                val text = password?.let { NoteCrypto.decrypt(v.content, it) } ?: v.content
                title = v.title
                value = TextFieldValue(text)
                saved = title to text
            }, onDismiss = { dialog = null })
        }
        EditorDialog.Encrypt -> PasswordDialog(
            title = "Зашифровать заметку", confirm = true,
            onConfirm = { pw ->
                dialog = null
                password = pw
                val text = value.text
                scope.launch {
                    val secret = withContext(Dispatchers.Default) { NoteCrypto.encrypt(text, pw) }
                    repo.setEncryptedContent(note.id, secret, encrypted = true)
                }
            },
            onDismiss = { dialog = null },
        )
        EditorDialog.Date -> NoteDateDialog(note.date, onSet = { d2 -> dialog = null; scope.launch { repo.setDate(note.id, d2) } }, onDismiss = { dialog = null })
        EditorDialog.Link -> LinkPickerDialog(container, summaries.filter { it.id != note.id }, onPick = { md ->
            dialog = null
            value = TextEditing.insert(value, md)
        }, onDismiss = { dialog = null })
        is EditorDialog.ToTask -> ToTaskDialog(
            container = container,
            initialTitle = d.title.ifBlank { title },
            noteId = note.id,
            noteTitle = title.ifBlank { note.displayTitle },
            onCreated = { cardId, taskTitle ->
                dialog = null
                val link = NoteMarkup.appLink(LinkKind.Card, cardId, taskTitle)
                value = if (d.selection) {
                    TextEditing.replaceRange(value, d.start, d.end, link)
                } else {
                    val line = value.text.substring(d.start, d.end)
                    val indent = line.takeWhile { it == ' ' }
                    TextEditing.replaceRange(value, d.start, d.end, "$indent- [ ] $link")
                }
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun Header(
    note: Note,
    folders: List<NoteFolder>,
    status: String,
    preview: Boolean,
    locked: Boolean,
    onTogglePreview: () -> Unit,
    onFolder: (String?) -> Unit,
    onDate: () -> Unit,
    onPin: () -> Unit,
    onVersions: () -> Unit,
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
    onTrash: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
        var folderMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { folderMenu = true }) {
                Text("Папка: ${folders.firstOrNull { it.id == note.folderId }?.name ?: "нет"}", style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                DropdownMenuItem(text = { Text("Без папки") }, onClick = { folderMenu = false; onFolder(null) })
                folders.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { folderMenu = false; onFolder(f.id) }) }
            }
        }
        TextButton(onClick = onDate) {
            Text(note.date?.let { "Дата: ${Dates.format(it)}" } ?: "Без даты", style = MaterialTheme.typography.labelMedium)
        }
        if (!locked) {
            TextButton(onClick = onTogglePreview) { Text(if (preview) "Править" else "Просмотр", style = MaterialTheme.typography.labelMedium) }
        }
        IconButton(onClick = onPin) {
            Icon(
                if (note.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (note.pinned) "Открепить" else "Закрепить",
                tint = if (note.pinned) colors.primary else colors.onSurfaceVariant,
            )
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("История версий") }, onClick = { menu = false; onVersions() })
                if (!note.encrypted) DropdownMenuItem(text = { Text("Зашифровать паролем") }, onClick = { menu = false; onEncrypt() })
                if (note.encrypted && !locked) DropdownMenuItem(text = { Text("Снять шифрование") }, onClick = { menu = false; onDecrypt() })
                DropdownMenuItem(text = { Text("В корзину", color = colors.error) }, onClick = { menu = false; onTrash() })
            }
        }
    }
}

/** Панель форматирования. Текстовые кнопки понятнее значков и не требуют набора иконок. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(
    onWrap: (String, String) -> Unit,
    onPrefix: (String) -> Unit,
    onInsert: (String) -> Unit,
    onLink: () -> Unit,
    onAttach: () -> Unit,
    onToTask: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(10.dp), color = colors.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 10.dp)) {
        FlowRow(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
            @Composable fun B(label: String, hint: String = "", onClick: () -> Unit) = TextButton(
                onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(34.dp),
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface) }
            B("H1") { onPrefix("# ") }
            B("H2") { onPrefix("## ") }
            B("Ж") { onWrap("**", "**") }
            B("К") { onWrap("*", "*") }
            B("~~") { onWrap("~~", "~~") }
            B("</>") { onWrap("`", "`") }
            B("• Список") { onPrefix("- ") }
            B("☐ Дела") { onPrefix("- [ ] ") }
            B("❝ Цитата") { onPrefix("> ") }
            B("[[ ]]") { onWrap("[[", "]]") }
            B("Связать…", onClick = onLink)
            B("Файл…", onClick = onAttach)
            B("→ В задачу", onClick = onToTask)
            var templates by remember { mutableStateOf(false) }
            Box {
                B("Шаблон") { templates = true }
                DropdownMenu(expanded = templates, onDismissRequest = { templates = false }) {
                    builtInTemplates.forEach { t ->
                        DropdownMenuItem(text = { Text(t.name) }, onClick = { templates = false; onInsert(t.fill().second) })
                    }
                }
            }
            B("Дата") { onInsert(Dates.format(Dates.today())) }
        }
    }
}

@Composable
private fun LockedView(error: String?, onUnlock: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Заметка зашифрована", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        PasswordField(pw, { pw = it }, "Пароль", isError = error != null, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onUnlock(pw) }, enabled = pw.isNotEmpty()) { Text("Открыть") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Backlinks(backlinks: List<NoteSummary>, onOpen: (String) -> Unit) {
    if (backlinks.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        "Ссылаются на эту заметку",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        backlinks.forEach { b -> AssistChip(onClick = { onOpen(b.id) }, label = { Text(b.displayTitle) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) }
    }
}
