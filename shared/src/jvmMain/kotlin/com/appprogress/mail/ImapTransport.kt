package com.appprogress.mail

import jakarta.mail.FetchProfile
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/** Чтение «Входящих» по IMAP через Jakarta Mail. Только чтение: письма на сервере не меняются. */
class ImapTransport(private val timeoutMs: Int = 20_000) : MailTransport {

    override suspend fun test(account: MailAccount, password: String): String? = withContext(Dispatchers.IO) {
        runCatching { connect(account, password).close() }.exceptionOrNull()?.let(::humanError)
    }

    override suspend fun fetchInbox(account: MailAccount, password: String, afterUid: Long?, limit: Int): List<FetchedMessage> =
        withContext(Dispatchers.IO) {
            connect(account, password).use { store ->
                val folder = store.getFolder("INBOX")
                folder.open(Folder.READ_ONLY)
                try {
                    val uidFolder = folder as UIDFolder
                    val messages: Array<Message> = when {
                        afterUid != null -> uidFolder.getMessagesByUID(afterUid + 1, UIDFolder.LASTUID)
                            .filter { uidFolder.getUID(it) > afterUid }.toTypedArray()
                        folder.messageCount == 0 -> emptyArray()
                        else -> folder.getMessages(maxOf(1, folder.messageCount - limit + 1), folder.messageCount)
                    }
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(UIDFolder.FetchProfileItem.UID)
                        add("List-Unsubscribe")
                        add("Auto-Submitted")
                        add("Precedence")
                    }
                    folder.fetch(messages, profile)
                    messages.takeLast(limit).map { m -> toFetched(m, uidFolder.getUID(m)) }
                } finally {
                    folder.close(false)
                }
            }
        }

    private fun connect(account: MailAccount, password: String): Store {
        val protocol = if (account.security == Security.Ssl) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.connectiontimeout", timeoutMs.toString())
            put("mail.$protocol.timeout", timeoutMs.toString())
            if (account.security == Security.StartTls) put("mail.imap.starttls.required", "true")
        }
        val store = Session.getInstance(props).getStore(protocol)
        store.connect(account.host, account.port, account.username, password)
        return store
    }

    private fun toFetched(m: Message, uid: Long): FetchedMessage {
        val from = m.from?.firstOrNull() as? InternetAddress
        val auto = m.getHeader("Auto-Submitted")?.any { !it.equals("no", ignoreCase = true) } == true ||
            m.getHeader("Precedence")?.any { it.lowercase() in setOf("bulk", "list", "junk") } == true
        return FetchedMessage(
            folder = "INBOX",
            uid = uid,
            fromName = from?.personal?.let { MimeUtility.decodeText(it) }.orEmpty(),
            fromAddress = from?.address.orEmpty(),
            subject = m.subject.orEmpty(),
            body = runCatching { textOf(m) }.getOrDefault(""),
            receivedAt = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
            isRead = m.isSet(Flags.Flag.SEEN),
            hasUnsubscribe = m.getHeader("List-Unsubscribe")?.isNotEmpty() == true,
            isAuto = auto,
        )
    }

    /** Текст письма: text/plain, а если его нет — HTML без тегов. */
    private fun textOf(part: Part): String {
        if (part.isMimeType("text/plain")) return part.content.toString()
        if (part.isMimeType("text/html")) return htmlToText(part.content.toString())
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as Multipart
            val parts = (0 until mp.count).map { mp.getBodyPart(it) }
            parts.firstOrNull { it.isMimeType("text/plain") }?.let { return textOf(it) }
            return parts.map { textOf(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
        }
        return ""
    }

    private fun humanError(e: Throwable): String {
        val text = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
        return when {
            "authentication" in text || "auth" in text || "login" in text || "invalid credentials" in text ->
                "Сервер не принял логин или пароль. Для Gmail, Яндекса и Mail.ru нужен «пароль приложения», а не обычный пароль."
            "unknownhost" in text || "unknown host" in text || "nodename" in text -> "Сервер не найден — проверьте адрес IMAP-сервера."
            "timed out" in text || "timeout" in text -> "Сервер не отвечает — проверьте адрес, порт и подключение к интернету."
            "connection refused" in text -> "Сервер отказал в подключении — проверьте порт и тип шифрования."
            else -> "Не удалось подключиться: ${e.message ?: e::class.simpleName}"
        }
    }

    companion object {
        fun htmlToText(html: String): String = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>|</h[1-6]>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()
    }
}

actual fun platformMailTransport(): MailTransport = ImapTransport()
