package com.appprogress.notes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.appprogress.core.Dates
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Снимок версии делаем не чаще, чем раз в 10 минут правок одной заметки. */
private const val VERSION_INTERVAL_MS = 10 * 60_000L
private const val TRASH_DAYS = 30L

/**
 * Единственная точка доступа к заметкам. UI и ИИ-ассистент работают только через неё,
 * поэтому правила (мягкое удаление, теги, версии, updated_at) живут в одном месте.
 */
class NotesRepository(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = ::nowMillis,
) {
    private val queries get() = db.noteQueries

    /** Полные заметки (с текстом). Для списков лучше [observeSummaries] — он легче. */
    fun observeAll(): Flow<List<Note>> = queries.selectActive(::toNote).asFlow().mapToList(io)

    fun observeSummaries(): Flow<List<NoteSummary>> = queries.selectSummaries(::toSummary).asFlow().mapToList(io)

    fun observeTrash(): Flow<List<NoteSummary>> = queries.selectTrash(::toTrashSummary).asFlow().mapToList(io)

    fun observeFolders(): Flow<List<NoteFolder>> =
        queries.selectFolders { id, name, parent -> NoteFolder(id, name, parent) }.asFlow().mapToList(io)

    suspend fun get(id: String): Note? = withContext(io) { queries.selectById(id, ::toNote).executeAsOneOrNull() }

    suspend fun create(
        title: String = "",
        content: String = "",
        folderId: String? = null,
        date: LocalDate? = null,
        isDaily: Boolean = false,
    ): String = withContext(io) {
        val id = newId()
        val now = clock()
        queries.insertFull(id, title, content, now, now, folderId, date?.toString(), if (isDaily) 1 else 0, tagsOf(content))
        id
    }

    /** Сохраняет текст. Перед изменением кладёт прежний текст в историю (не чаще раза в 10 минут). */
    suspend fun updateText(id: String, title: String, content: String) = withContext(io) {
        db.transaction {
            val current = queries.selectById(id, ::toNote).executeAsOneOrNull() ?: return@transaction
            if (current.title == title && current.content == content) return@transaction
            val now = clock()
            val last = queries.lastVersionAt(id).executeAsOne().MAX
            if (current.content.isNotBlank() && (last == null || now - last >= VERSION_INTERVAL_MS)) {
                queries.insertVersion(newId(), id, current.title, current.content, now)
                queries.trimVersions(id, id)
            }
            queries.updateTextAndTags(title, content, if (current.encrypted) "" else tagsOf(content), now, id)
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(io) { queries.setPinned(if (pinned) 1L else 0L, clock(), id) }

    suspend fun setFolder(id: String, folderId: String?) = withContext(io) { queries.setFolder(folderId, clock(), id) }

    suspend fun setDate(id: String, date: LocalDate?) = withContext(io) { queries.setDate(date?.toString(), clock(), id) }

    /** Шифрование делает UI (пароль знает только пользователь); здесь сохраняем уже зашифрованный или расшифрованный текст. */
    suspend fun setEncryptedContent(id: String, content: String, encrypted: Boolean) = withContext(io) {
        queries.setEncrypted(content, if (encrypted) 1 else 0, if (encrypted) "" else tagsOf(content), clock(), id)
    }

    /** В корзину. */
    suspend fun delete(id: String) = withContext(io) {
        val now = clock()
        queries.softDelete(now, now, id)
    }

    suspend fun restore(id: String) = withContext(io) { queries.restore(clock(), id) }

    suspend fun deleteForever(id: String) = withContext(io) {
        db.transaction {
            queries.deleteVersions(id)
            queries.deleteForever(id)
        }
    }

    /** Заметки, пролежавшие в корзине дольше 30 дней, удаляются окончательно. */
    suspend fun purgeTrash(olderThanDays: Long = TRASH_DAYS) = withContext(io) {
        queries.purgeTrashBefore(clock() - olderThanDays * 24 * 3600_000L)
    }

    /**
     * Обслуживание при запуске: чистим корзину и заполняем теги у заметок, созданных до появления тегов.
     */
    suspend fun maintenance() = withContext(io) {
        queries.purgeTrashBefore(clock() - TRASH_DAYS * 24 * 3600_000L)
        queries.selectUntagged().executeAsList().forEach { queries.setTags(tagsOf(it.content), it.id) }
    }

    /**
     * Полнотекстовый поиск (FTS5): все слова должны встретиться, каждое — как начало слова.
     * «отп кра» найдёт «Отпуск в Краснодаре». Результат — id в порядке релевантности.
     */
    suspend fun search(query: String): List<String> = withContext(io) {
        val words = Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase().replace('ё', 'е')).map { it.value }.toList()
        if (words.isEmpty()) return@withContext emptyList()
        val expr = words.joinToString(" ") { "\"$it\"*" }
        runCatching { queries.search(expr).executeAsList().mapNotNull { it.note_id } }.getOrDefault(emptyList()).distinct()
    }

    suspend fun findByTitle(title: String): String? = withContext(io) {
        val wanted = title.trim().lowercase()
        queries.selectTitles().executeAsList().firstOrNull { it.title.trim().lowercase() == wanted }?.id
    }

    /** Для [[ссылки]]: находит заметку по названию или создаёт новую. */
    suspend fun openOrCreateByTitle(title: String): String = findByTitle(title) ?: create(title.trim())

    /** Заметки, которые ссылаются на эту: через [[Название]] или через app://note/ID. */
    suspend fun backlinks(note: Note): List<String> = withContext(io) {
        val byTitle = if (note.title.isNotBlank()) "[[${note.title}" else "\u0000"
        queries.selectBacklinks(note.id, byTitle, "app://note/${note.id}").executeAsList()
    }

    suspend fun versions(noteId: String): List<NoteVersion> = withContext(io) {
        queries.selectVersions(noteId) { id, title, content, at -> NoteVersion(id, title, content, at) }.executeAsList()
    }

    /** Возвращает старую версию; текущий текст при этом сам попадает в историю. */
    suspend fun restoreVersion(noteId: String, version: NoteVersion) = withContext(io) {
        db.transaction {
            val current = queries.selectById(noteId, ::toNote).executeAsOneOrNull() ?: return@transaction
            val now = clock()
            queries.insertVersion(newId(), noteId, current.title, current.content, now)
            queries.updateTextAndTags(version.title, version.content, if (current.encrypted) "" else tagsOf(version.content), now, noteId)
        }
    }

    // --- Папки ---

    suspend fun createFolder(name: String, parentId: String? = null): String = withContext(io) {
        val id = newId()
        queries.insertFolder(id, name.trim().ifEmpty { "Новая папка" }, parentId, clock())
        id
    }

    suspend fun renameFolder(id: String, name: String) = withContext(io) { queries.renameFolder(name.trim().ifEmpty { "Без названия" }, id) }

    /** Папка удаляется, а её заметки и вложенные папки переезжают на уровень выше. */
    suspend fun deleteFolder(folder: NoteFolder) = withContext(io) {
        db.transaction {
            queries.moveNotesOutOfFolder(folder.parentId, folder.id)
            queries.reparentFolders(folder.parentId, folder.id)
            queries.deleteFolder(folder.id)
        }
    }

    /** Служебная папка («Входящие», «Шаблоны») верхнего уровня: находит или создаёт. */
    suspend fun ensureFolder(name: String): String = withContext(io) {
        queries.selectFolders { id, n, parent -> NoteFolder(id, n, parent) }.executeAsList()
            .firstOrNull { it.parentId == null && it.name.equals(name, ignoreCase = true) }?.id
            ?: newId().also { queries.insertFolder(it, name, null, clock()) }
    }

    // --- Ежедневные заметки ---

    suspend fun findDaily(date: LocalDate): String? = withContext(io) { queries.selectDaily(date.toString()).executeAsOneOrNull() }

    /** Ежедневная заметка на дату: если её нет — создаётся с текстом из [initialContent]. */
    suspend fun openOrCreateDaily(date: LocalDate, initialContent: suspend () -> String): String =
        findDaily(date) ?: create(
            title = "Дневник ${Dates.format(date)}",
            content = initialContent(),
            folderId = ensureFolder(DAILY_FOLDER),
            date = date,
            isDaily = true,
        )

    private fun tagsOf(content: String) = NoteMarkup.tags(content).joinToString(",")

    private fun toNote(
        id: String, title: String, content: String, pinned: Long, createdAt: Long, updatedAt: Long,
        @Suppress("UNUSED_PARAMETER") deletedAt: Long?, folderId: String?, noteDate: String?, isDaily: Long, encrypted: Long,
        @Suppress("UNUSED_PARAMETER") tags: String,
    ) = Note(id, title, content, pinned != 0L, createdAt, updatedAt, folderId, Dates.parseIso(noteDate), isDaily != 0L, encrypted != 0L)

    private fun toSummary(
        id: String, title: String, preview: String?, pinned: Long, updatedAt: Long, folderId: String?, noteDate: String?,
        isDaily: Long, encrypted: Long, tags: String,
    ) = NoteSummary(
        id, title, preview.orEmpty(), pinned != 0L, updatedAt, folderId, Dates.parseIso(noteDate), isDaily != 0L, encrypted != 0L,
        tags.split(',').filter { it.isNotBlank() },
    )

    private fun toTrashSummary(
        id: String, title: String, preview: String?, pinned: Long, updatedAt: Long, folderId: String?, noteDate: String?,
        isDaily: Long, encrypted: Long, tags: String, deletedAt: Long?,
    ) = toSummary(id, title, preview, pinned, updatedAt, folderId, noteDate, isDaily, encrypted, tags).copy(deletedAt = deletedAt)

    companion object {
        const val INBOX_FOLDER = "Входящие"
        const val TEMPLATES_FOLDER = "Шаблоны"
        const val DAILY_FOLDER = "Дневник"
    }
}
