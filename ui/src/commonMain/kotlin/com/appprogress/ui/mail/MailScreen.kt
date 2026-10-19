package com.appprogress.ui.mail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appprogress.mail.MailAccount
import com.appprogress.mail.MailMessage
import com.appprogress.mail.MailPriority
import com.appprogress.mail.MailRepository
import com.appprogress.mail.MailRule
import com.appprogress.mail.RuleField
import com.appprogress.ui.AppContainer
import com.appprogress.kanban.Priority
import com.appprogress.ui.common.ConfirmDialog
import com.appprogress.ui.notes.ToTaskDialog
import androidx.compose.material3.OutlinedButton
import com.appprogress.ui.common.LocalNavigator
import com.appprogress.ui.common.NavTarget
import com.appprogress.ui.common.formatDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_SYNC_MS = 5 * 60_000L

fun MailPriority.color(): Color = when (this) {
    MailPriority.High -> Color(0xFFE5484D)
    MailPriority.Normal -> Color(0xFF4F46E5)
    MailPriority.Low -> Color(0xFF9A9AAE)
    MailPriority.Suspicious -> Color(0xFFF59E0B)
}

/**
 * [repoFactory] позволяет подменить репозиторий в тестах (без настоящей почты);
 * по умолчанию — IMAP и шифрование паролей текущей платформы.
 */
@Composable
fun MailScreen(
    container: AppContainer,
    repoFactory: () -> MailRepository = { MailRepository(container.db, container.io) },
) {
    val repo = remember { repoFactory() }
    val scope = container.scope
    val accounts by remember { repo.observeAccounts() }.collectAsState(initial = null)
    val messages by remember { repo.observeMessages() }.collectAsState(initial = emptyList())
    val rules by remember { repo.observeRules() }.collectAsState(initial = emptyList())

    var priorityFilter by remember { mutableStateOf<MailPriority?>(MailPriority.High) }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var addingAccount by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<MailAccount?>(null) }
    var taskFrom by remember { mutableStateOf<MailMessage?>(null) }
    val navigator = LocalNavigator.current
    LaunchedEffect(navigator.pending) {
        navigator.consume<NavTarget.Mail>()?.let { priorityFilter = null; accountFilter = null; query = ""; selectedId = it.id }
    }

    val sync: () -> Unit = {
        if (!syncing) scope.launch { syncing = true; try { repo.syncAll() } finally { syncing = false } }
    }
    // Пока раздел открыт, проверяем почту сразу и затем каждые 5 минут.
    LaunchedEffect(accounts?.size) {
        if (accounts.isNullOrEmpty()) return@LaunchedEffect
        while (true) {
            sync()
            delay(AUTO_SYNC_MS)
        }
    }

    val accountById = accounts.orEmpty().associateBy { it.id }
    val visible = messages.filter { m ->
        (priorityFilter == null || m.priority == priorityFilter) &&
            (accountFilter == null || m.accountId == accountFilter) &&
            (query.isBlank() || listOf(m.subject, m.sender, m.fromAddress, m.snippet).any { it.contains(query.trim(), ignoreCase = true) })
    }
    val selected = messages.firstOrNull { it.id == selectedId }

    if (accounts?.isEmpty() == true) {
        NoAccounts(onAdd = { addingAccount = true })
    } else {
        Row(Modifier.fillMaxSize()) {
            Filters(
                messages = messages,
                accounts = accounts.orEmpty(),
                priorityFilter = priorityFilter,
                accountFilter = accountFilter,
                syncing = syncing,
                onPriority = { priorityFilter = it },
                onAccount = { accountFilter = if (accountFilter == it) null else it },
                onSync = sync,
                onAddAccount = { addingAccount = true },
                onRemoveAccount = { removing = it },
                onRules = { showRules = true },
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.width(400.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Поиск по письмам") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                if (visible.isEmpty()) {
                    Text(
                        if (messages.isEmpty()) "Писем пока нет" else "Здесь пусто — попробуйте другой фильтр",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visible, key = { it.id }) { m ->
                        MessageRow(m, accountById[m.accountId], selected = m.id == selectedId) {
                            selectedId = m.id
                            if (!m.isRead) scope.launch { repo.markRead(m.id) }
                        }
                    }
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                if (selected != null) {
                    MessageView(
                        message = selected,
                        account = accountById[selected.accountId],
                        repo = repo,
                        onPriority = { p -> scope.launch { repo.setPriority(selected.id, p) } },
                        onCreateRule = { rule -> scope.launch { repo.addRule(rule) } },
                        onToTask = { taskFrom = selected },
                    )
                } else {
                    Text(
                        "Выберите письмо",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }

    if (addingAccount) {
        AddAccountDialog(
            onAdd = { account, password -> repo.addAccount(account, password).also { r -> if (r.isSuccess) sync() } },
            onDismiss = { addingAccount = false },
        )
    }
    if (showRules) {
        RulesDialog(
            rules = rules,
            onAdd = { scope.launch { repo.addRule(it) } },
            onDelete = { scope.launch { repo.deleteRule(it.id) } },
            onDismiss = { showRules = false },
        )
    }
    taskFrom?.let { m ->
        ToTaskDialog(
            container = container,
            initialTitle = m.subject.ifBlank { "Письмо от ${m.sender}" },
            description = "Из письма от ${m.sender} <${m.fromAddress}>, тема «${m.subject}»\napp://mail/${m.id}\n\n${m.snippet}",
            labels = listOf("почта"),
            priority = if (m.priority == MailPriority.High) Priority.High else Priority.None,
            onCreated = { _, _ -> taskFrom = null },
            onDismiss = { taskFrom = null },
        )
    }
    removing?.let { acc ->
        ConfirmDialog(
            title = "Отключить ящик?",
            text = "${acc.email} будет отключён, его письма удалятся из приложения. На сервере почты ничего не изменится.",
            confirmLabel = "Отключить",
            onConfirm = { scope.launch { repo.removeAccount(acc.id) } },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun NoAccounts(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Подключите почту", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Письма из всех ящиков соберутся в одну ленту и разложатся по важности. " +
                "Приложение только читает почту и ничего не меняет на сервере.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(520.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Icon(Icons.Filled.Add, null); Text("Добавить ящик", Modifier.padding(start = 6.dp)) }
    }
}

@Composable
private fun Filters(
    messages: List<MailMessage>,
    accounts: List<MailAccount>,
    priorityFilter: MailPriority?,
    accountFilter: String?,
    syncing: Boolean,
    onPriority: (MailPriority?) -> Unit,
    onAccount: (String) -> Unit,
    onSync: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (MailAccount) -> Unit,
    onRules: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Почта", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onSync) { Icon(Icons.Filled.Refresh, "Проверить почту") }
        }
        Spacer(Modifier.height(8.dp))
        val counts = messages.groupingBy { it.priority }.eachCount()
        val unread = messages.filter { !it.isRead }.groupingBy { it.priority }.eachCount()
        MailPriority.entries.forEach { p ->
            FilterRow(p.title, counts[p] ?: 0, unread[p] ?: 0, p.color(), selected = priorityFilter == p) { onPriority(p) }
        }
        FilterRow("Все письма", messages.size, messages.count { !it.isRead }, colors.onSurfaceVariant, selected = priorityFilter == null) { onPriority(null) }

        Spacer(Modifier.height(16.dp))
        Text("Ящики", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        accounts.forEach { acc ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (accountFilter == acc.id) colors.primaryContainer else Color.Transparent)
                    .clickable { onAccount(acc.id) }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(Color(acc.color), CircleShape))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(acc.email, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    acc.lastError?.let { Text("Ошибка: $it", style = MaterialTheme.typography.labelSmall, color = colors.error, maxLines = 2) }
                }
                Text(
                    "×", color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { onRemoveAccount(acc) }.padding(horizontal = 4.dp),
                )
            }
        }
        TextButton(onClick = onAddAccount) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text(" Добавить ящик") }
        TextButton(onClick = onRules) { Icon(Icons.Filled.Settings, null, Modifier.size(16.dp)); Text(" Правила сортировки") }
    }
}

@Composable
private fun FilterRow(title: String, count: Int, unread: Int, dot: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(title, modifier = Modifier.padding(start = 10.dp).weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (unread > 0) Text("$unread · ", style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun MessageRow(m: MailMessage, account: MailAccount?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else colors.background)
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Box(Modifier.width(3.dp).height(48.dp).background(Color(account?.color ?: 0xFF9A9AAE), RoundedCornerShape(2.dp)))
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(m.priority.color(), CircleShape))
                Text(
                    m.sender, modifier = Modifier.padding(start = 6.dp).weight(1f),
                    fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(formatDateTime(m.receivedAt), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Text(m.subject.ifBlank { "(без темы)" }, fontWeight = if (m.isRead) FontWeight.Normal else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.snippet, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageView(
    message: MailMessage,
    account: MailAccount?,
    repo: MailRepository,
    onPriority: (MailPriority) -> Unit,
    onCreateRule: (MailRule) -> Unit,
    onToTask: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val body by produceState("", message.id) { value = repo.body(message.id) }
    // После ручной смены приоритета предлагаем сделать из этого правило для отправителя.
    var suggestion by remember(message.id) { mutableStateOf<MailPriority?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(message.subject.ifBlank { "(без темы)" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("${message.sender} <${message.fromAddress}>", fontWeight = FontWeight.Medium)
        Text(
            "${formatDateTime(message.receivedAt)} · ${account?.email.orEmpty()} · ${message.category.title}",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
        )
        Surface(shape = RoundedCornerShape(10.dp), color = message.priority.color().copy(alpha = 0.1f), border = BorderStroke(1.dp, message.priority.color().copy(alpha = 0.4f))) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${message.priority.title}: ${message.reason}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MailPriority.High, MailPriority.Normal, MailPriority.Low).forEach { p ->
                        FilterChip(
                            selected = message.priority == p,
                            onClick = { onPriority(p); suggestion = p },
                            label = { Text(p.title) },
                        )
                    }
                }
                suggestion?.let { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Всегда так для писем от ${message.fromAddress}?", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onCreateRule(MailRule(field = RuleField.From, pattern = message.fromAddress, priority = p))
                            suggestion = null
                        }) { Text("Создать правило") }
                        TextButton(onClick = { suggestion = null }) { Text("Нет") }
                    }
                }
            }
        }
        OutlinedButton(onClick = onToTask) { Text("→ В задачу") }
        Spacer(Modifier.height(8.dp))
        SelectionContainer { Text(body.ifBlank { message.snippet }, style = MaterialTheme.typography.bodyLarge) }
    }
}
