package com.appprogress.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.appprogress.mail.MailAccount
import com.appprogress.mail.MailPriority
import com.appprogress.mail.MailProvider
import com.appprogress.mail.MailRule
import com.appprogress.mail.RuleField
import com.appprogress.mail.Security
import kotlinx.coroutines.launch

/** Подключение ящика: выбор сервиса подставляет сервер и порт; перед сохранением проверяем вход. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAccountDialog(onAdd: suspend (MailAccount, String) -> Result<String>, onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf(MailProvider.Gmail) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(provider.host) }
    var port by remember { mutableStateOf(provider.port.toString()) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = !checking && email.contains('@') && password.isNotBlank() && host.isNotBlank() && port.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text("Добавить ящик") },
        text = {
            Column(Modifier.width(480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MailProvider.entries.forEach { p ->
                        FilterChip(
                            selected = p == provider,
                            onClick = { provider = p; host = p.host; port = p.port.toString() },
                            label = { Text(p.title) },
                        )
                    }
                }
                Text(provider.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Адрес почты") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Пароль приложения") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(host, { host = it.trim() }, label = { Text("IMAP-сервер") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Порт") }, singleLine = true, modifier = Modifier.width(100.dp))
                }
                Text(
                    "Пароль хранится на этом компьютере в зашифрованном виде. Приложение только читает письма.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = {
                checking = true
                error = null
                scope.launch {
                    val p = port.toInt()
                    val account = MailAccount(
                        email = email, host = host, port = p,
                        security = if (p == 143) Security.StartTls else Security.Ssl,
                    )
                    val result = onAdd(account, password)
                    checking = false
                    if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message
                }
            }) {
                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Проверяем…")
                    }
                } else Text("Проверить и добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesDialog(rules: List<MailRule>, onAdd: (MailRule) -> Unit, onDelete: (MailRule) -> Unit, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf(RuleField.From) }
    var pattern by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(MailPriority.High) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Правила сортировки") },
        text = {
            Column(Modifier.width(520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Правила проверяются первыми и сразу применяются ко всем письмам, кроме отсортированных вручную.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rules.isEmpty()) Text("Правил пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                rules.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.field.title} «${r.pattern}» → ${r.priority.title}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDelete(r) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    }
                }
                Text("Новое правило", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleField.entries.forEach { f -> FilterChip(selected = f == field, onClick = { field = f }, label = { Text(f.title) }) }
                }
                OutlinedTextField(
                    pattern, { pattern = it },
                    label = { Text(if (field == RuleField.From) "Адрес или домен, например boss@work.ru или @bank.ru" else "Слово или фраза") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MailPriority.entries.forEach { p -> FilterChip(selected = p == priority, onClick = { priority = p }, label = { Text(p.title) }) }
                }
                TextButton(enabled = pattern.isNotBlank(), onClick = {
                    onAdd(MailRule(field = field, pattern = pattern, priority = priority))
                    pattern = ""
                }) { Text("Добавить правило") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
