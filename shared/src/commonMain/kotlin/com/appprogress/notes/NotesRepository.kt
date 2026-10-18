package com.appprogress.notes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Единственная точка доступа к заметкам. UI, а позже и ИИ-ассистент, работают только через неё,
 * поэтому правила (мягкое удаление, обновление updated_at) живут в одном месте.
 */
class NotesRepository(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = ::nowMillis,
) {
    private val queries get() = db.noteQueries

    fun observeAll(): Flow<List<Note>> =
        queries.selectActive(::toNote).asFlow().mapToList(io)

    suspend fun get(id: String): Note? = withContext(io) {
        queries.selectById(id, ::toNote).executeAsOneOrNull()
    }

    suspend fun create(title: String = "", content: String = ""): String = withContext(io) {
        val id = newId()
        val now = clock()
        queries.insert(id, title, content, now, now)
        id
    }

    suspend fun updateText(id: String, title: String, content: String) = withContext(io) {
        queries.updateText(title, content, clock(), id)
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(io) {
        queries.setPinned(if (pinned) 1L else 0L, clock(), id)
    }

    suspend fun delete(id: String) = withContext(io) {
        val now = clock()
        queries.softDelete(now, now, id)
    }

    private fun toNote(
        id: String, title: String, content: String, pinned: Long,
        createdAt: Long, updatedAt: Long, @Suppress("UNUSED_PARAMETER") deletedAt: Long?,
    ) = Note(id, title, content, pinned != 0L, createdAt, updatedAt)
}
