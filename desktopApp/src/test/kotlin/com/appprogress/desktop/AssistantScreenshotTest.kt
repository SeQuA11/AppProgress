package com.appprogress.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.appprogress.assistant.EngineStatus
import com.appprogress.assistant.LlmEngine
import com.appprogress.assistant.LlmMessage
import com.appprogress.assistant.LlmReply
import com.appprogress.assistant.ToolCall
import com.appprogress.assistant.ToolSpec
import com.appprogress.ui.Section
import com.appprogress.ui.assistant.AssistantScreen
import com.appprogress.ui.theme.AppTheme
import com.appprogress.ui.theme.ThemeMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test

/** «Модель» для скриншота: на вопрос про проект ищет заметки, затем предлагает создать заметку. */
private class ScriptedEngine : LlmEngine {
    override suspend fun status() = EngineStatus.Ready(listOf("qwen3:8b", "qwen3:4b"))

    override suspend fun chat(model: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply {
        val last = messages.last()
        return when {
            last.role == "user" && "проект" in last.content ->
                LlmReply("", listOf(ToolCall("search_notes", buildJsonObject { put("query", JsonPrimitive("проект")) })))
            last.role == "tool" && last.toolName == "search_notes" ->
                LlmReply(
                    "По заметке «План проекта AppProgress»: каркас готов, дальше — канбан, календарь, студия, почта и ассистент.",
                    listOf(ToolCall("create_note", buildJsonObject {
                        put("title", JsonPrimitive("Дела на завтра"))
                        put("content", JsonPrimitive("1. Проверить канбан\n2. Настроить почту\n3. Установить Ollama"))
                    })),
                )
            else -> LlmReply("Готово.", emptyList())
        }
    }
}

private class OfflineEngine : LlmEngine {
    override suspend fun status() = EngineStatus.NotRunning("connection refused")
    override suspend fun chat(model: String, messages: List<LlmMessage>, tools: List<ToolSpec>) = error("offline")
}

class AssistantScreenshotTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun conversationWithPendingAction() {
        val container = Screenshots.demoContainer { c ->
            c.notes.create("План проекта AppProgress", "Каркас готов. Дальше: канбан, календарь, студия, почта, ассистент.")
        }
        val scene = ImageComposeScene(1280, 800, Density(1f)) {
            AppTheme(ThemeMode.Light) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AssistantScreen(container) { ScriptedEngine() }
                }
            }
        }
        try {
            repeat(6) { scene.render(); Thread.sleep(120) }
            // Нажимаем подсказку «Что у меня в заметках про проект?».
            val chip = Offset(330f, 184f)
            scene.sendPointerEvent(PointerEventType.Press, chip)
            scene.sendPointerEvent(PointerEventType.Release, chip)
            repeat(10) { scene.render(); Thread.sleep(150) }
            Screenshots.save(scene, "assistant-chat")
        } finally {
            scene.close()
        }
    }

    @Test
    fun setupGuideWhenOllamaMissing() {
        val container = Screenshots.demoContainer()
        val scene = ImageComposeScene(1280, 800, Density(1f)) {
            AppTheme(ThemeMode.Light) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AssistantScreen(container) { OfflineEngine() }
                }
            }
        }
        try {
            repeat(6) { scene.render(); Thread.sleep(120) }
            Screenshots.save(scene, "assistant-setup")
        } finally {
            scene.close()
        }
    }

    @Test fun inAppWithoutOllama() = Screenshots.shoot("assistant-in-app", Section.Assistant)
}
