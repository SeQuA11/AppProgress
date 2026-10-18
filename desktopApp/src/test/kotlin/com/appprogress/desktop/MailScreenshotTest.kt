package com.appprogress.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.appprogress.mail.FetchedMessage
import com.appprogress.mail.MailAccount
import com.appprogress.mail.MailRepository
import com.appprogress.mail.MailTransport
import com.appprogress.mail.SecretCipher
import com.appprogress.ui.AppContainer
import com.appprogress.ui.Section
import com.appprogress.ui.mail.MailScreen
import com.appprogress.ui.theme.AppTheme
import com.appprogress.ui.theme.ThemeMode
import com.appprogress.mail.MailPriority
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** Почтовый сервер для скриншотов: вход всегда успешен, новых писем нет (демо-письма кладём сами). */
private object FakeTransport : MailTransport {
    override suspend fun test(account: MailAccount, password: String): String? = null
    override suspend fun fetchInbox(account: MailAccount, password: String, afterUid: Long?, limit: Int) = emptyList<FetchedMessage>()
}

private object PlainCipher : SecretCipher {
    override fun encrypt(plain: String) = plain
    override fun decrypt(secret: String) = secret
}

class MailScreenshotTest {
    private fun repo(c: AppContainer) = MailRepository(c.db, c.io, FakeTransport, PlainCipher)

    private val seed: suspend (AppContainer) -> Unit = { c ->
        val repo = repo(c)
        val work = repo.addAccount(MailAccount(email = "ivan@work.ru", host = "imap.work.ru", port = 993), "x").getOrThrow()
        val home = repo.addAccount(MailAccount(email = "ivan.home@yandex.ru", host = "imap.yandex.ru", port = 993), "x").getOrThrow()
        val now = System.currentTimeMillis()
        var uid = 0L
        fun msg(from: String, address: String, subject: String, body: String, minutesAgo: Int, unsubscribe: Boolean = false, auto: Boolean = false, read: Boolean = false) =
            FetchedMessage("INBOX", ++uid, from, address, subject, body, now - minutesAgo * 60_000L, read, unsubscribe, auto)
        repo.saveFetched(work, listOf(
            msg("Анна Смирнова", "anna@work.ru", "Срочно: согласовать договор с подрядчиком", "Иван, нужно до конца дня посмотреть договор и дать комментарии. Файл во вложении.", 12),
            msg("Бухгалтерия", "buh@work.ru", "Счёт на оплату №1542", "Добрый день! Направляем счёт на оплату за октябрь. Оплатить до 25.10.", 95),
            msg("Jira", "noreply@jira.work.ru", "[PRJ-102] Задача назначена на вас", "Вам назначена задача PRJ-102.", 180, auto = true, read = true),
        ))
        repo.saveFetched(home, listOf(
            msg("Мама", "mama@mail.ru", "Про выходные", "Приезжайте в субботу к обеду, будет пирог!", 40),
            msg("OZON", "news@ozon.ru", "Скидки до 70% только сегодня", "Распродажа недели...", 60, unsubscribe = true),
            msg("Служба безопасности", "security@bank-verify.ru", "Ваш аккаунт заблокирован", "Подтвердите пароль по ссылке, иначе ваш аккаунт заблокирован навсегда.", 300),
            msg("Аэрофлот", "info@aeroflot.ru", "Ваш электронный билет", "Рейс SU 1234 Москва — Казань, 2 ноября.", 600, read = true),
        ))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun shootMail(name: String, theme: ThemeMode, clickAt: Offset? = null) {
        val container = Screenshots.demoContainer(seed)
        val scene = ImageComposeScene(1280, 800, Density(1f)) {
            AppTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MailScreen(container) { repo(container) }
                }
            }
        }
        try {
            repeat(8) { scene.render(); Thread.sleep(120) }
            clickAt?.let {
                scene.sendPointerEvent(PointerEventType.Press, it)
                scene.sendPointerEvent(PointerEventType.Release, it)
                repeat(6) { scene.render(); Thread.sleep(120) }
            }
            Screenshots.save(scene, name)
        } finally {
            scene.close()
        }
    }

    @Test fun mailLight() = shootMail("mail-light", ThemeMode.Light)
    @Test fun mailDark() = shootMail("mail-dark", ThemeMode.Dark)
    @Test fun mailReading() = shootMail("mail-reading", ThemeMode.Light, clickAt = Offset(440f, 120f))
    @Test fun mailNoAccounts() = Screenshots.shoot("mail-empty", Section.Mail)

    @Test
    fun demoMailIsSortedAsExpected() = runBlocking<Unit> {
        val c = Screenshots.demoContainer(seed)
        val byPriority = repo(c).recentMessages().groupBy({ it.priority }, { it.subject })
        assertEquals(
            setOf("Срочно: согласовать договор с подрядчиком", "Счёт на оплату №1542"),
            byPriority[MailPriority.High].orEmpty().toSet(),
        )
        assertEquals(setOf("Про выходные", "Ваш электронный билет"), byPriority[MailPriority.Normal].orEmpty().toSet())
        assertEquals(setOf("[PRJ-102] Задача назначена на вас", "Скидки до 70% только сегодня"), byPriority[MailPriority.Low].orEmpty().toSet())
        assertEquals(listOf("Ваш аккаунт заблокирован"), byPriority[MailPriority.Suspicious])
    }
}
