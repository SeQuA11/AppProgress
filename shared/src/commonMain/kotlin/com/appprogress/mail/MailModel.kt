package com.appprogress.mail

enum class MailPriority(val code: String, val title: String) {
    High("high", "Важно"),
    Normal("normal", "Обычное"),
    Low("low", "Неважное"),
    Suspicious("suspicious", "Подозрительное");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Normal
    }
}

enum class MailCategory(val code: String, val title: String) {
    Personal("personal", "Личное"),
    Work("work", "Работа"),
    Finance("finance", "Финансы"),
    Shopping("shopping", "Покупки"),
    Travel("travel", "Поездки"),
    Social("social", "Соцсети"),
    Newsletter("newsletter", "Рассылки"),
    Notification("notification", "Уведомления");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Personal
    }
}

enum class Security(val code: String, val title: String) {
    Ssl("ssl", "SSL/TLS"), StartTls("starttls", "STARTTLS"), None("none", "Без шифрования");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Ssl
    }
}

/** Настройки ящика. Пароль здесь не хранится — он лежит в базе зашифрованным и расшифровывается только для подключения. */
data class MailAccount(
    val id: String = "",
    val email: String,
    val displayName: String = email,
    val host: String,
    val port: Int,
    val security: Security = Security.Ssl,
    val username: String = email,
    val color: Long = 0xFF4F46E5,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

/** Готовые настройки популярных почтовых сервисов. */
enum class MailProvider(val title: String, val host: String, val port: Int, val hint: String) {
    Gmail(
        "Gmail", "imap.gmail.com", 993,
        "Нужен «пароль приложения»: аккаунт Google → Безопасность → Двухэтапная аутентификация → Пароли приложений.",
    ),
    Yandex(
        "Яндекс", "imap.yandex.ru", 993,
        "Нужен пароль приложения: Яндекс ID → Безопасность → Пароли приложений → Почта. В настройках почты включите доступ по IMAP.",
    ),
    MailRu(
        "Mail.ru", "imap.mail.ru", 993,
        "Нужен пароль для внешнего приложения: Настройки → Безопасность → Пароли для внешних приложений.",
    ),
    Other("Другой", "", 993, "Адрес IMAP-сервера и порт есть в справке вашей почты (обычно порт 993, SSL)."),
}

/** Письмо в списке (без полного текста). */
data class MailMessage(
    val id: String,
    val accountId: String,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val snippet: String,
    val receivedAt: Long,
    val isRead: Boolean,
    val priority: MailPriority,
    val category: MailCategory,
    val reason: String,
    val manual: Boolean,
) {
    val sender: String get() = fromName.ifBlank { fromAddress }
}

/** Письмо, полученное с сервера, до сохранения. */
data class FetchedMessage(
    val folder: String,
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val body: String,
    val receivedAt: Long,
    val isRead: Boolean,
    val hasUnsubscribe: Boolean,
    val isAuto: Boolean,
)

enum class RuleField(val code: String, val title: String) {
    From("from", "Отправитель содержит"), Subject("subject", "Тема содержит"), Any("any", "Письмо содержит");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Any
    }
}

data class MailRule(val id: String = "", val field: RuleField, val pattern: String, val priority: MailPriority)

/** Подключение к почтовому серверу. На ПК — IMAP через Jakarta Mail (см. jvmMain). */
interface MailTransport {
    /** Проверяет, что сервер принимает логин и пароль. Возвращает текст ошибки или null. */
    suspend fun test(account: MailAccount, password: String): String?

    /** Новые письма из «Входящих» с UID больше [afterUid]; при первом обращении (null) — последние [limit]. */
    suspend fun fetchInbox(account: MailAccount, password: String, afterUid: Long?, limit: Int = 100): List<FetchedMessage>
}

/** Шифрование паролей ящиков перед сохранением в базу. */
interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(secret: String): String
}

expect fun platformMailTransport(): MailTransport

expect fun platformSecretCipher(): SecretCipher
