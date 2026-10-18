package com.appprogress.mail

import com.appprogress.core.DatabaseFactory
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailTest {
    private lateinit var server: GreenMail
    private val cipher = DesktopSecretCipher(File.createTempFile("key", ".bin").apply { delete(); deleteOnExit() })
    private val repo = MailRepository(DatabaseFactory.inMemory(), Dispatchers.IO, ImapTransport(timeoutMs = 5_000), cipher)
    private val account = MailAccount(
        email = "me@test.local", host = "127.0.0.1", port = ServerSetupTest.IMAP.port, security = Security.None,
    )

    @BeforeTest
    fun start() {
        server = GreenMail(ServerSetupTest.IMAP).apply { start() }
        server.setUser("me@test.local", "me@test.local", "app-password")
    }

    @AfterTest
    fun stop() = server.stop()

    private fun deliver(from: String, subject: String, body: String, headers: Map<String, String> = emptyMap()) {
        val msg = MimeMessage(Session.getInstance(Properties())).apply {
            setFrom(InternetAddress(from))
            setRecipient(Message.RecipientType.TO, InternetAddress("me@test.local"))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
            headers.forEach { (k, v) -> setHeader(k, v) }
            saveChanges()
        }
        server.getUserManager().getUser("me@test.local").deliver(msg)
    }

    private fun facts(subject: String, text: String = "", from: String = "Иван <ivan@mail.ru>", unsubscribe: Boolean = false, auto: Boolean = false) =
        MessageFacts(from.substringBefore("<").trim(), from.substringAfter("<").removeSuffix(">"), subject, text, unsubscribe, auto)

    @Test
    fun classifierPriorities() {
        assertEquals(MailPriority.High, MailClassifier.classify(facts("Счёт на оплату №15"), emptyList()).priority)
        assertEquals(MailPriority.Low, MailClassifier.classify(facts("Скидки недели", unsubscribe = true), emptyList()).priority)
        assertEquals(MailPriority.Low, MailClassifier.classify(facts("Новый вход", from = "noreply@service.com"), emptyList()).priority)
        assertEquals(MailPriority.Suspicious, MailClassifier.classify(facts("Внимание", "Ваш аккаунт заблокирован, перейдите"), emptyList()).priority)
        assertEquals(MailPriority.Normal, MailClassifier.classify(facts("Привет! Как дела?"), emptyList()).priority)
    }

    @Test
    fun userRuleWinsOverHeuristics() {
        val rule = MailRule(field = RuleField.From, pattern = "shop.ru", priority = MailPriority.High)
        val c = MailClassifier.classify(facts("Скидки", from = "Магазин <news@shop.ru>", unsubscribe = true), listOf(rule))
        assertEquals(MailPriority.High, c.priority)
        assertTrue(c.reason.startsWith("Ваше правило"))
    }

    @Test
    fun categoriesFromKeywords() {
        assertEquals(MailCategory.Travel, MailClassifier.classify(facts("Ваш электронный билет"), emptyList()).category)
        assertEquals(MailCategory.Shopping, MailClassifier.classify(facts("Заказ передан в доставку"), emptyList()).category)
    }

    @Test
    fun secretsAreEncryptedAndRoundTrip() {
        val secret = cipher.encrypt("app-password")
        assertTrue(!secret.contains("app-password"))
        assertEquals("app-password", cipher.decrypt(secret))
    }

    @Test
    fun wrongPasswordIsRejectedWithFriendlyMessage() = runBlocking {
        val result = repo.addAccount(account, "wrong")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("пароль"))
        assertTrue(repo.accounts().isEmpty())
    }

    @Test
    fun syncFetchesAndSortsThenOnlyNewOnes() = runBlocking<Unit> {
        deliver("Начальник <boss@work.ru>", "Срочно: отчёт до пятницы", "Нужен отчёт.")
        deliver("Магазин <news@shop.ru>", "Скидки недели", "Распродажа!", mapOf("List-Unsubscribe" to "<mailto:unsub@shop.ru>"))
        deliver("Служба <noreply@bank.ru>", "Вход в интернет-банк", "Выполнен вход", mapOf("Auto-Submitted" to "auto-generated"))

        val id = repo.addAccount(account, "app-password").getOrThrow()
        val first = repo.sync(repo.accounts().single())
        assertNull(first.error)
        assertEquals(3, first.newMessages)

        val byPriority = repo.observeMessages().first().associate { it.subject to it.priority }
        assertEquals(MailPriority.High, byPriority["Срочно: отчёт до пятницы"])
        assertEquals(MailPriority.Low, byPriority["Скидки недели"])
        assertEquals(MailPriority.Low, byPriority["Вход в интернет-банк"])

        deliver("Иван <ivan@mail.ru>", "Привет", "Как дела?")
        assertEquals(1, repo.sync(repo.accounts().single()).newMessages, "второй раз забираем только новое")
        assertEquals("Как дела?", repo.body(repo.observeMessages().first().first { it.subject == "Привет" }.id).trim())
        assertNotNull(repo.accounts().single { it.id == id }.lastSyncAt)
    }

    @Test
    fun ruleReclassifiesButKeepsManualChoice() = runBlocking {
        val accountId = repo.addAccount(account, "app-password").getOrThrow()
        val now = System.currentTimeMillis()
        fun fetched(uid: Long, subject: String) =
            FetchedMessage("INBOX", uid, "", "news@shop.ru", subject, "текст", now + uid, false, hasUnsubscribe = true, isAuto = false)
        repo.saveFetched(accountId, listOf(fetched(1, "Скидки"), fetched(2, "Ещё скидки")))
        assertTrue(repo.recentMessages().all { it.priority == MailPriority.Low })

        val manual = repo.recentMessages().first { it.subject == "Скидки" }
        repo.setPriority(manual.id, MailPriority.Low)
        repo.addRule(MailRule(field = RuleField.From, pattern = "shop.ru", priority = MailPriority.High))

        val after = repo.recentMessages().associate { it.subject to it.priority }
        assertEquals(MailPriority.Low, after["Скидки"], "ручной выбор правило не трогает")
        assertEquals(MailPriority.High, after["Ещё скидки"])
    }

    @Test
    fun htmlIsConvertedToText() {
        val text = ImapTransport.htmlToText("<style>p{}</style><p>Привет,&nbsp;<b>мир</b></p><br>Пока")
        assertTrue(text.startsWith("Привет, мир"))
        assertTrue(text.endsWith("Пока"))
        assertTrue('<' !in text && "p{}" !in text)
    }
}
