package com.appprogress.notes

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Заголовок для списка: если пользователь его не задал, берём первую строку текста. */
    val displayTitle: String
        get() = title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
            .ifBlank { "Без названия" }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        // Ищем в Kotlin, а не через SQL LIKE: в SQLite LIKE не учитывает регистр только для латиницы,
        // и «Заметка» не нашлась бы по запросу «заметка».
        val q = query.trim().lowercase()
        return title.lowercase().contains(q) || content.lowercase().contains(q)
    }
}
