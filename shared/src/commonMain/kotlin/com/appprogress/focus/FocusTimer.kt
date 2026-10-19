package com.appprogress.focus

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.appprogress.core.newId
import com.appprogress.core.nowMillis
import com.appprogress.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TimeEntry(val id: String, val cardId: String?, val title: String, val startedAt: Long, val endedAt: Long) {
    val minutes: Double get() = (endedAt - startedAt) / 60_000.0
}

class TimeRepository(private val db: AppDatabase, private val io: CoroutineDispatcher) {
    fun observeBetween(from: Long, to: Long): Flow<List<TimeEntry>> =
        db.timeTrackingQueries.selectBetween(from, to, ::TimeEntry).asFlow().mapToList(io)

    suspend fun between(from: Long, to: Long): List<TimeEntry> = withContext(io) {
        db.timeTrackingQueries.selectBetween(from, to, ::TimeEntry).executeAsList()
    }

    suspend fun record(cardId: String?, title: String, startedAt: Long, endedAt: Long) = withContext(io) {
        db.timeTrackingQueries.insert(newId(), cardId, title, startedAt, endedAt)
    }
}

/**
 * Таймер Pomodoro: 25 минут фокуса → 5 минут перерыва. Завершённый (или остановленный, если прошло больше минуты)
 * отрезок фокуса записывается в учёт времени — из этого строится график «куда уходит время».
 */
class FocusTimer(
    private val time: TimeRepository,
    private val scope: CoroutineScope,
    private val notify: (title: String, text: String) -> Unit,
    private val clock: () -> Long = ::nowMillis,
) {
    enum class Phase { Focus, Break }

    sealed interface State {
        data object Idle : State
        data class Running(val phase: Phase, val title: String, val cardId: String?, val startedAt: Long, val endsAt: Long) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun startFocus(title: String, cardId: String? = null, durationMs: Long = FOCUS_MS, breakMs: Long = BREAK_MS) {
        stop()
        val now = clock()
        val running = State.Running(Phase.Focus, title.ifBlank { "Без задачи" }, cardId, now, now + durationMs)
        _state.value = running
        job = scope.launch {
            delay(durationMs)
            time.record(running.cardId, running.title, running.startedAt, clock())
            notify("Фокус окончен: ${running.title}", "Отличная работа! Перерыв ${breakMs / 60_000} минут.")
            val br = clock()
            _state.value = State.Running(Phase.Break, running.title, running.cardId, br, br + breakMs)
            delay(breakMs)
            notify("Перерыв окончен", "Можно начинать следующий фокус.")
            _state.value = State.Idle
        }
    }

    /** Остановить досрочно: отрезок фокуса длиннее минуты всё равно попадает в учёт. */
    fun stop() {
        job?.cancel()
        job = null
        val s = _state.value
        if (s is State.Running && s.phase == Phase.Focus && clock() - s.startedAt >= 60_000) {
            val end = clock()
            scope.launch { time.record(s.cardId, s.title, s.startedAt, end) }
        }
        _state.value = State.Idle
    }

    companion object {
        const val FOCUS_MS = 25 * 60_000L
        const val BREAK_MS = 5 * 60_000L
    }
}
