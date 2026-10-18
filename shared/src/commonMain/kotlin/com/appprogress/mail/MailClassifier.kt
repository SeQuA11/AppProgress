package com.appprogress.mail

/** Что известно о письме для сортировки. */
data class MessageFacts(
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val text: String,
    val hasUnsubscribe: Boolean,
    val isAuto: Boolean,
)

data class Classification(val priority: MailPriority, val category: MailCategory, val reason: String)

/**
 * Сортировка писем «от дешёвого к дорогому»: сначала правила пользователя, затем признаки в самом письме.
 * Работает мгновенно и без интернета. ИИ-ассистент может уточнять только те письма, где правила не уверены.
 */
object MailClassifier {

    private val phishing = listOf(
        "подтвердите пароль", "ваш аккаунт заблокирован", "учётная запись будет удалена", "учетная запись будет удалена",
        "вы выиграли", "выигрыш", "verify your account", "your account has been suspended", "lottery", "unusual sign-in activity, click",
    )
    private val urgent = listOf(
        "срочно", "urgent", "asap", "дедлайн", "deadline", "до конца дня", "оплатить до", "просрочен",
        "договор", "contract", "собеседование", "interview", "счёт на оплату", "счет на оплату", "invoice",
    )
    private val categoryWords: List<Pair<MailCategory, List<String>>> = listOf(
        MailCategory.Finance to listOf("счёт", "счет", "оплат", "платёж", "платеж", "банк", "выписка", "invoice", "payment", "налог"),
        MailCategory.Travel to listOf("билет", "бронирован", "рейс", "отель", "гостиниц", "booking", "flight", "посадочный"),
        MailCategory.Shopping to listOf("заказ", "доставк", "скидк", "распродаж", "промокод", "order", "delivery", "sale", "корзин"),
        MailCategory.Social to listOf("vk.com", "telegram", "facebook", "instagram", "linkedin", "одноклассники", "ok.ru"),
    )
    private val robotSenders = listOf("noreply", "no-reply", "do-not-reply", "donotreply", "notifications@", "notification@", "mailer-daemon")

    fun classify(m: MessageFacts, rules: List<MailRule>): Classification {
        val haystack = "${m.subject}\n${m.text}".lowercase()
        val from = "${m.fromName} ${m.fromAddress}".lowercase()
        val category = guessCategory(from, haystack, m)

        // 1. Правила пользователя важнее всего.
        rules.firstOrNull { it.matches(from, m.subject.lowercase(), haystack) }?.let { rule ->
            return Classification(rule.priority, category, "Ваше правило: ${rule.field.title.lowercase()} «${rule.pattern}»")
        }

        // 2. Похоже на фишинг.
        phishing.firstOrNull { it in haystack }?.let { word ->
            return Classification(MailPriority.Suspicious, category, "Похоже на мошенничество: «$word»")
        }

        // 3. Срочное и деловое — даже если пришло от робота (например, счёт на оплату).
        urgent.firstOrNull { it in haystack }?.let { word ->
            return Classification(MailPriority.High, category, "Ключевое слово «$word»")
        }

        // 4. Рассылки и автоматические уведомления.
        if (m.hasUnsubscribe) {
            val cat = if (category == MailCategory.Personal) MailCategory.Newsletter else category
            return Classification(MailPriority.Low, cat, "Рассылка: в письме есть ссылка «отписаться»")
        }
        if (m.isAuto || robotSenders.any { it in m.fromAddress.lowercase() }) {
            val cat = if (category == MailCategory.Personal) MailCategory.Notification else category
            return Classification(MailPriority.Low, cat, "Автоматическое письмо от ${m.fromAddress}")
        }

        return Classification(MailPriority.Normal, category, "Обычное письмо от человека")
    }

    private fun guessCategory(from: String, haystack: String, m: MessageFacts): MailCategory {
        categoryWords.firstOrNull { (_, words) -> words.any { it in from || it in haystack } }?.let { return it.first }
        if (m.hasUnsubscribe) return MailCategory.Newsletter
        return MailCategory.Personal
    }

    private fun MailRule.matches(from: String, subject: String, haystack: String): Boolean {
        val p = pattern.trim().lowercase()
        if (p.isEmpty()) return false
        return when (field) {
            RuleField.From -> p in from
            RuleField.Subject -> p in subject
            RuleField.Any -> p in from || p in haystack
        }
    }
}
