package com.appprogress.ui.notes

import com.appprogress.calendar.CalendarRepository
import com.appprogress.calendar.RuText
import com.appprogress.core.Dates
import com.appprogress.kanban.KanbanRepository
import com.appprogress.kanban.cardRefs
import com.appprogress.kanban.isDone
import com.appprogress.mail.MailRepository
import com.appprogress.notes.LinkKind
import com.appprogress.notes.NoteSummary
import com.appprogress.ui.AppContainer

/**
 * «Живые» ссылки в заметках: по id задачи, события или письма показываем актуальное состояние —
 * колонку и срок задачи, дату события, отправителя письма. Собирается заново при открытии заметки.
 */
class LinkResolver(private val infos: Map<Pair<LinkKind, String>, LinkInfo>) {
    fun resolve(kind: LinkKind, id: String): LinkInfo? =
        infos[kind to id] ?: LinkInfo("не найдено", missing = true)

    companion object {
        val Empty = LinkResolver(emptyMap())

        suspend fun load(container: AppContainer, notes: List<NoteSummary>): LinkResolver {
            val map = mutableMapOf<Pair<LinkKind, String>, LinkInfo>()
            runCatching {
                KanbanRepository(container.db, container.io).cardRefs().forEach { r ->
                    val due = r.card.dueDate?.let { " · до ${Dates.format(it)}" }.orEmpty()
                    map[LinkKind.Card to r.card.id] = LinkInfo("${r.column.title}$due", done = r.column.isDone())
                }
            }
            runCatching {
                CalendarRepository(container.db, container.io).all().forEach { e ->
                    val time = e.startTime?.let { " ${RuText.time(it)}" }.orEmpty()
                    map[LinkKind.Event to e.id] = LinkInfo(Dates.format(e.date) + time)
                }
            }
            runCatching {
                MailRepository(container.db, container.io).recentMessages(2000).forEach { m ->
                    map[LinkKind.Mail to m.id] = LinkInfo("от ${m.sender}")
                }
            }
            notes.forEach { map[LinkKind.Note to it.id] = LinkInfo("") }
            return LinkResolver(map)
        }
    }
}
