package com.appprogress.notes

/**
 * Шифрование отдельных заметок паролем пользователя. Пароль нигде не хранится:
 * забыли пароль — заметку не расшифровать, и это свойство, а не ошибка.
 */
expect object NoteCrypto {
    /** Возвращает строку вида «enc1:…» (соль, вектор и шифротекст в Base64). */
    fun encrypt(text: String, password: String): String

    /** null — неверный пароль или повреждённые данные. */
    fun decrypt(secret: String, password: String): String?
}
