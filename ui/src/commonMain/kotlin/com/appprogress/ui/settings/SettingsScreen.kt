package com.appprogress.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.appprogress.ui.common.ConfirmDialog
import com.appprogress.ui.common.formatDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appprogress.ui.AppContainer
import com.appprogress.ui.theme.ThemeMode

@Composable
fun SettingsScreen(theme: ThemeMode, onThemeChange: (ThemeMode) -> Unit, container: AppContainer) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(32.dp).widthIn(max = 640.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SettingsCard("Оформление") {
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = mode == theme, onClick = { onThemeChange(mode) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == theme, onClick = { onThemeChange(mode) })
                    Text(mode.title)
                }
            }
        }

        SettingsCard("Данные") {
            Text("Все данные хранятся только на этом компьютере:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(container.dataLocation, style = MaterialTheme.typography.bodySmall)
        }

        BackupsCard()

        SettingsCard("Горячие клавиши") {
            listOf(
                "Ctrl+K" to "палитра команд: поиск по всему и быстрые действия",
                "Ctrl+Alt+N" to "быстрая запись во «Входящие» из любой программы (Windows)",
                "Ctrl+N" to "новая заметка",
                "Ctrl+E" to "заметка: правка / просмотр",
                "Ctrl+B, Ctrl+I" to "жирный и курсив в заметке",
                "Ctrl+V" to "вставить скриншот в заметку",
            ).forEach { (keys, what) ->
                Row {
                    Text(keys, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 120.dp))
                    Text(what, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SettingsCard("О приложении") {
            Text("AppProgress ${container.version}")
        }
    }
}

@Composable
private fun BackupsCard() {
    var rows by remember { mutableStateOf(listBackups()) }
    var pending by remember { mutableStateOf(pendingRestoreName()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<BackupRow?>(null) }
    SettingsCard("Резервные копии") {
        Text(
            "Копия базы делается автоматически раз в день и хранится 14 дней. Восстановление — при следующем запуске.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                message = createBackupNow()?.let { "Не удалось: $it" } ?: "Копия создана"
                rows = listBackups()
            }) { Text("Создать копию сейчас") }
            TextButton(onClick = ::openBackupsFolder) { Text("Открыть папку") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        pending?.let { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("При следующем запуске будет восстановлена копия $name", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = { cancelRestore(); pending = null }) { Text("Отменить") }
            }
        }
        rows.take(8).forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(row.createdAt), modifier = Modifier.weight(1f))
                Text("${row.sizeBytes / 1024} КБ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirm = row }) { Text("Восстановить") }
            }
        }
        if (rows.isEmpty()) Text("Копий пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    confirm?.let { row ->
        ConfirmDialog(
            title = "Восстановить копию от ${formatDateTime(row.createdAt)}?",
            text = "При следующем запуске данные заменятся этой копией. Текущая база сохранится отдельной копией, так что отмена возможна.",
            confirmLabel = "Восстановить при запуске",
            onConfirm = { scheduleRestore(row.name); pending = pendingRestoreName() },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
