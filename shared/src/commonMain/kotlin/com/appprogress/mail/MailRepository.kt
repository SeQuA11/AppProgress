package com.appprogress.mail

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Цвета ящиков: у каждого свой, чтобы в общей ленте было видно, откуда письмо. */
private val ACCOUNT_COLORS = listOf(0xFF4F46E5, 0xFF0E9F8E, 0xFFE5484D, 0xFFF59E0B, 0xFF3E9BFF, 0xFF8B5CF6)

data class SyncResult(val accountId: String, val newMessages: Int, val error: String?)

class MailRepository(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
    private val transport: MailTransport = platformMailTransport(),
    private val cipher: SecretCipher = platformSecretCipher(),
    private val clock: () -> Long = ::nowMillis,
) {
    private val q get() = db.mailQueries

    fun observeAccounts(): Flow<List<MailAccount>> = q.selectAccounts(::toAccount).asFlow().mapToList(io)

    fun observeMessages(limit: Long = 1000): Flow<List<MailMessage>> = q.selectRecent(limit, ::toMessage).asFlow().mapToList(io)

    fun observeRules(): Flow<List<MailRule>> = q.selectRules(::toRule).asFlow().mapToList(io)

    suspend fun accounts(): List<MailAccount> = withContext(io) { q.selectAccounts(::toAccount).executeAsList() }

    suspend fun recentMessages(limit: Long = 200): List<MailMessage> = withContext(io) { q.selectRecent(limit, ::toMessage).executeAsList() }

    suspend fun body(messageId: String): String = withContext(io) { q.selectBody(messageId).executeAsOneOrNull().orEmpty() }

    /** Проверяет подключение и, если всё хорошо, сохраняет ящик (пароль — в зашифрованном виде). */
    suspend fun addAccount(account: MailAccount, password: String): Result<String> {
        transport.test(account, password)?.let { return Result.failure(IllegalStateException(it)) }
        return withContext(io) {
            val id = newId()
            val used = q.selectAccounts(::toAccount).executeAsList().size
            q.insertAccount(
                id, account.email.trim(), account.displayName.trim().ifEmpty { account.email.trim() }, account.host.trim(),
                account.port.toLong(), account.security.code, account.username.trim().ifEmpty { account.email.trim() },
                cipher.encrypt(password), ACCOUNT_COLORS[used % ACCOUNT_COLORS.size], clock(),
            )
            Result.success(id)
        }
    }

    suspend fun removeAccount(id: String) = withContext(io) {
        db.transaction {
            q.softDeleteAccount(clock(), id)
            q.deleteMessagesOfAccount(id)
        }
    }

    /** Забирает новые письма из всех ящиков. Ошибка одного ящика не мешает остальным. */
    suspend fun syncAll(): List<SyncResult> = accounts().map { sync(it) }

    suspend fun sync(account: MailAccount): SyncResult {
        val result = runCatching {
            val secret = withContext(io) { q.selectAccounts(::secretOf).executeAsList().first { it.first == account.id }.second }
            val afterUid = withContext(io) { q.maxUid(account.id, "INBOX").executeAsOne().MAX }
            val fetched = transport.fetchInbox(account, cipher.decrypt(secret), afterUid)
            saveFetched(account.id, fetched)
        }
        val error = result.exceptionOrNull()?.let { it.message ?: it::class.simpleName ?: "Ошибка" }
        withContext(io) { q.setSyncResult(clock(), error, account.id) }
        return SyncResult(account.id, result.getOrDefault(0), error)
    }

    /** Сохраняет письма, сразу раскладывая их по приоритетам. Возвращает, сколько добавлено. */
    suspend fun saveFetched(accountId: String, messages: List<FetchedMessage>): Int = withContext(io) {
        val rules = q.selectRules(::toRule).executeAsList()
        var added = 0
        db.transaction {
            messages.forEach { m ->
                val facts = MessageFacts(m.fromName, m.fromAddress, m.subject, m.body.take(4000), m.hasUnsubscribe, m.isAuto)
                val c = MailClassifier.classify(facts, rules)
                val before = q.selectBody("$accountId:${m.folder}:${m.uid}").executeAsOneOrNull()
                q.insertMessage(
                    "$accountId:${m.folder}:${m.uid}", accountId, m.folder, m.uid, m.fromName, m.fromAddress, m.subject,
                    snippetOf(m.body), m.body.take(200_000), m.receivedAt, if (m.isRead) 1 else 0,
                    if (m.hasUnsubscribe) 1 else 0, if (m.isAuto) 1 else 0, c.priority.code, c.category.code, c.reason,
                )
                if (before == null) added++
            }
        }
        added
    }

    suspend fun markRead(id: String) = withContext(io) { q.markRead(id) }

    /** Пользователь сам поменял приоритет — дальше автоматическая сортировка это письмо не трогает. */
    suspend fun setPriority(id: String, priority: MailPriority) = withContext(io) {
        q.setManualPriority(priority.code, "Вы отметили вручную", id)
    }

    /** Новое правило сразу применяется ко всем письмам, которые пользователь не сортировал вручную. */
    suspend fun addRule(rule: MailRule) = withContext(io) {
        q.insertRule(newId(), rule.field.code, rule.pattern.trim(), rule.priority.code, clock())
        reclassify()
    }

    suspend fun deleteRule(id: String) = withContext(io) {
        q.deleteRule(id)
        reclassify()
    }

    private fun reclassify() {
        val rules = q.selectRules(::toRule).executeAsList()
        db.transaction {
            q.selectForReclassify().executeAsList().forEach { r ->
                val c = MailClassifier.classify(
                    MessageFacts(r.from_name, r.from_address, r.subject, r.snippet, r.has_unsubscribe == 1L, r.is_auto == 1L), rules,
                )
                q.setClassification(c.priority.code, c.category.code, c.reason, r.id)
            }
        }
    }

    private fun toAccount(
        id: String, email: String, displayName: String, host: String, port: Long, security: String, username: String,
        @Suppress("UNUSED_PARAMETER") secret: String, color: Long, lastSyncAt: Long?, lastError: String?,
    ) = MailAccount(id, email, displayName, host, port.toInt(), Security.of(security), username, color, lastSyncAt, lastError)

    @Suppress("UNUSED_PARAMETER")
    private fun secretOf(
        id: String, email: String, displayName: String, host: String, port: Long, security: String, username: String,
        secret: String, color: Long, lastSyncAt: Long?, lastError: String?,
    ) = id to secret

    private fun toMessage(
        id: String, accountId: String, fromName: String, fromAddress: String, subject: String, snippet: String,
        receivedAt: Long, isRead: Long, @Suppress("UNUSED_PARAMETER") hasUnsubscribe: Long,
        @Suppress("UNUSED_PARAMETER") isAuto: Long, priority: String, category: String, reason: String, manual: Long,
    ) = MailMessage(
        id, accountId, fromName, fromAddress, subject, snippet, receivedAt, isRead == 1L,
        MailPriority.of(priority), MailCategory.of(category), reason, manual == 1L,
    )

    private fun toRule(id: String, field: String, pattern: String, priority: String) =
        MailRule(id, RuleField.of(field), pattern, MailPriority.of(priority))

    companion object {
        fun snippetOf(body: String): String =
            body.replace(Regex("\\s+"), " ").trim().take(240)
    }
}
