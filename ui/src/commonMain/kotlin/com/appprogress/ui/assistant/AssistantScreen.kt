package com.appprogress.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appprogress.assistant.ActionState
import com.appprogress.assistant.Assistant
import com.appprogress.assistant.ChatItem
import com.appprogress.assistant.EngineStatus
import com.appprogress.assistant.LlmEngine
import com.appprogress.assistant.OllamaEngine
import com.appprogress.assistant.dateTool
import com.appprogress.assistant.notesTools
import com.appprogress.ui.AppContainer
import kotlinx.coroutines.launch

private const val MODEL_KEY = "assistant.model"
private const val URL_KEY = "assistant.url"

/** Модель, которую предлагаем скачать: хорошо понимает русский и умеет вызывать инструменты. */
const val RECOMMENDED_MODEL = "qwen3:8b"

private val suggestions = listOf(
    "Что у меня в заметках про проект?",
    "Сделай короткую сводку моих заметок",
    "Создай заметку со списком дел на завтра",
)

/** [engineFactory] позволяет подменить «мозг» в тестах; по умолчанию — Ollama по адресу из настроек. */
@Composable
fun AssistantScreen(
    container: AppContainer,
    engineFactory: (url: String) -> LlmEngine = { OllamaEngine(it) },
) {
    val scope = container.scope
    var url by remember { mutableStateOf(OllamaEngine.DEFAULT_URL) }
    var status by remember { mutableStateOf<EngineStatus?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var checkCounter by remember { mutableStateOf(0) }
    val engine = remember(url) { engineFactory(url) }
    val assistant = remember(engine) { Assistant(engine, notesTools(container.notes) + dateTool()) }
    val items = remember(assistant) { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.settings.get(URL_KEY)?.let { url = it } }
    LaunchedEffect(engine, checkCounter) {
        status = null
        val s = engine.status()
        status = s
        if (s is EngineStatus.Ready) {
            val saved = container.settings.get(MODEL_KEY)
            model = saved?.takeIf { it in s.models } ?: s.models.firstOrNull { it.startsWith(RECOMMENDED_MODEL.substringBefore(':')) } ?: s.models.first()
        }
    }

    val send: (String) -> Unit = { text ->
        val m = model
        if (text.isNotBlank() && m != null && !thinking) {
            items += ChatItem.User(text.trim())
            input = ""
            thinking = true
            scope.launch {
                try {
                    items += assistant.ask(m, text.trim())
                } catch (e: Exception) {
                    items += ChatItem.Error("Модель не ответила: ${e.message}")
                } finally {
                    thinking = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Header(
            status = status,
            model = model,
            onModel = { m -> model = m; scope.launch { container.settings.put(MODEL_KEY, m) } },
            onNewChat = { assistant.reset(); items.clear() },
            onRecheck = { checkCounter++ },
        )
        Spacer(Modifier.height(12.dp))
        when (val s = status) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is EngineStatus.NotRunning, EngineStatus.NoModels -> SetupGuide(noModels = s == EngineStatus.NoModels, onRecheck = { checkCounter++ })
            is EngineStatus.Ready -> Chat(
                items = items,
                thinking = thinking,
                input = input,
                onInput = { input = it },
                onSend = send,
                onConfirm = { index, item ->
                    scope.launch {
                        val result = assistant.confirm(item.action)
                        items[index] = item.copy(state = ActionState.Done, result = result)
                    }
                },
                onReject = { index, item ->
                    assistant.reject(item.action)
                    items[index] = item.copy(state = ActionState.Rejected)
                },
            )
        }
    }
}

@Composable
private fun Header(status: EngineStatus?, model: String?, onModel: (String) -> Unit, onNewChat: () -> Unit, onRecheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ассистент", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        val (dot, text) = when (status) {
            is EngineStatus.Ready -> Color(0xFF0E9F8E) to "Работает локально, без интернета"
            null -> colors.onSurfaceVariant to "Проверяю…"
            else -> colors.error to "Модель не запущена"
        }
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (status is EngineStatus.Ready && model != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menu = true }) { Text("Модель: $model") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    status.models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { menu = false; onModel(m) }) }
                }
            }
            TextButton(onClick = onNewChat) { Text("Новый разговор") }
        } else {
            TextButton(onClick = onRecheck) { Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp)); Text(" Проверить") }
        }
    }
}

/** Пошаговая инструкция, если модель ещё не установлена. */
@Composable
private fun SetupGuide(noModels: Boolean, onRecheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.outlineVariant), color = colors.surface) {
            Column(Modifier.widthIn(max = 620.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Подключим локальную модель", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Ассистент думает с помощью открытой языковой модели, которая работает прямо на вашем компьютере. " +
                        "Ваши заметки и письма никуда не отправляются. Нужно один раз поставить бесплатную программу Ollama и скачать модель.",
                    color = colors.onSurfaceVariant,
                )
                if (!noModels) Step(1, "Скачайте и установите Ollama с сайта ollama.com (Windows, Linux).")
                Step(if (noModels) 1 else 2, "Откройте терминал и скачайте модель (≈ 5 ГБ, один раз):")
                Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceVariant) {
                    SelectionContainer {
                        Text("ollama pull $RECOMMENDED_MODEL", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
                Text(
                    "Если компьютер слабый, возьмите модель поменьше: ollama pull qwen3:4b",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
                Step(if (noModels) 2 else 3, "Вернитесь сюда и нажмите «Проверить».")
                Button(onClick = onRecheck) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Text("  Проверить") }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(text, modifier = Modifier.padding(start = 10.dp, top = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chat(
    items: List<ChatItem>,
    thinking: Boolean,
    input: String,
    onInput: (String) -> Unit,
    onSend: (String) -> Unit,
    onConfirm: (Int, ChatItem.Action) -> Unit,
    onReject: (Int, ChatItem.Action) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size, thinking) { if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1 + if (thinking) 1 else 0) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Спросите что-нибудь о своих заметках или попросите что-то создать", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { s -> AssistChip(onClick = { onSend(s) }, label = { Text(s) }) }
                        }
                    }
                }
            }
            itemsIndexed(items) { index, item ->
                when (item) {
                    is ChatItem.User -> Bubble(item.text, mine = true)
                    is ChatItem.Answer -> Bubble(item.text, mine = false)
                    is ChatItem.ToolUse -> Text("· ${item.text}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is ChatItem.Error -> Text(item.text, color = MaterialTheme.colorScheme.error)
                    is ChatItem.Action -> ActionCard(item, onConfirm = { onConfirm(index, item) }, onReject = { onReject(index, item) })
                }
            }
            if (thinking) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Думаю…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("Напишите сообщение… (Enter — отправить, Shift+Enter — новая строка)") },
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { onSend(input); true } else false
                },
            )
            FilledIconButton(onClick = { onSend(input) }, enabled = input.isNotBlank() && !thinking, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) colors.primary else colors.surface,
            border = if (mine) null else BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            SelectionContainer {
                Text(text, color = if (mine) colors.onPrimary else colors.onSurface, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

/** Карточка подтверждения: ассистент ничего не меняет без явного «Да». */
@Composable
private fun ActionCard(item: ChatItem.Action, onConfirm: () -> Unit, onReject: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.primaryContainer.copy(alpha = 0.4f), border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))) {
        Column(Modifier.widthIn(max = 640.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ассистент предлагает действие", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(item.action.description, fontWeight = FontWeight.SemiBold)
            item.action.preview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (item.state) {
                ActionState.Waiting -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm) { Text("Выполнить") }
                    OutlinedButton(onClick = onReject) { Text("Отклонить") }
                }
                ActionState.Done -> Text("✓ ${item.result}", color = Color(0xFF0E9F8E))
                ActionState.Rejected -> Text("Отклонено", color = colors.onSurfaceVariant)
            }
        }
    }
}
