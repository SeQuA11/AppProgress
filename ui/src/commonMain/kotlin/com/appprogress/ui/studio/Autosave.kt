package com.appprogress.ui.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Автосохранение документа студии: сохраняет [value] после паузы в правках
 * и досохраняет при уходе с документа. Возвращает строку статуса для показа.
 */
@Composable
fun <T> rememberAutosave(value: T, delayMs: Long = 500, save: (T) -> Unit): String {
    var saved by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value == saved) return@LaunchedEffect
        delay(delayMs)
        save(value)
        saved = value
    }
    val latest by rememberUpdatedState(value to saved)
    DisposableEffect(Unit) {
        onDispose {
            val (v, s) = latest
            if (v != s) save(v)
        }
    }
    return if (value == saved) "Сохранено" else "Сохранение…"
}
