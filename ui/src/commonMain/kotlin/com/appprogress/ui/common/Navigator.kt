package com.appprogress.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.appprogress.ui.Section
import kotlinx.datetime.LocalDate

/** Что открыть в другом разделе: заметку, задачу, день календаря или письмо. */
sealed interface NavTarget {
    data class Note(val id: String) : NavTarget
    data class Card(val id: String) : NavTarget
    data class Day(val date: LocalDate) : NavTarget
    data class Mail(val id: String) : NavTarget
}

/**
 * Переходы между разделами. Раздел, которому адресован [pending], забирает его через [consume]
 * при появлении на экране — так ссылка из заметки открывает нужную карточку канбана.
 */
class Navigator(private val switchTo: (Section) -> Unit) {
    var pending by mutableStateOf<NavTarget?>(null)

    fun go(section: Section) = switchTo(section)

    fun open(target: NavTarget) {
        pending = target
        switchTo(
            when (target) {
                is NavTarget.Note -> Section.Notes
                is NavTarget.Card -> Section.Kanban
                is NavTarget.Day -> Section.Calendar
                is NavTarget.Mail -> Section.Mail
            },
        )
    }

    inline fun <reified T : NavTarget> consume(): T? = (pending as? T)?.also { pending = null }
}

val LocalNavigator = staticCompositionLocalOf { Navigator {} }

/** Ссылки внутри приложения в тексте карточек: app://note/ID, app://mail/ID. */
val APP_URI = Regex("""app://(note|card|event|mail)/([A-Za-z0-9-]+)""")
