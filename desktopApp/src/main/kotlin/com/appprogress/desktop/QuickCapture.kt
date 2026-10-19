package com.appprogress.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.appprogress.notes.NotesRepository
import com.appprogress.ui.AppContainer
import com.appprogress.ui.theme.AppTheme
import com.appprogress.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Маленькое окно поверх всех программ: записать мысль и вернуться к работе.
 * Enter — сохранить во «Входящие», Ctrl+Enter — новая строка, Esc — закрыть.
 */
@Composable
fun QuickCaptureWindow(container: AppContainer, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = "Быстрая запись",
        undecorated = true,
        alwaysOnTop = true,
        resizable = false,
        state = rememberWindowState(width = 560.dp, height = 190.dp, position = WindowPosition(Alignment.Center)),
    ) {
        AppTheme(ThemeMode.System) {
            QuickCaptureContent(onSave = { text ->
                container.scope.launch {
                    val inbox = container.notes.ensureFolder(NotesRepository.INBOX_FOLDER)
                    container.notes.create(text.lineSequence().first().take(80), text, folderId = inbox)
                }
                onClose()
            }, onClose = onClose)
        }
    }
}

@Composable
fun QuickCaptureContent(onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Surface(
        Modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Быстрая запись во «Входящие»", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Esc") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Мысль, задача, ссылка… Enter — сохранить") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.Escape -> { onClose(); true }
                        (e.key == Key.Enter || e.key == Key.NumPadEnter) && e.isCtrlPressed -> { text += "\n"; true }
                        e.key == Key.Enter || e.key == Key.NumPadEnter -> { if (text.isNotBlank()) onSave(text.trim()); true }
                        else -> false
                    }
                },
            )
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
