package com.appprogress.assistant

import kotlinx.serialization.json.JsonObject

/** Сообщение в разговоре с языковой моделью. role: system | user | assistant | tool. */
data class LlmMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolName: String? = null,
)

/** Модель просит вызвать инструмент [name] с аргументами [arguments]. */
data class ToolCall(val name: String, val arguments: JsonObject)

/** Описание инструмента для модели: имя, что делает и JSON-схема параметров. */
data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

data class LlmReply(val content: String, val toolCalls: List<ToolCall>)

sealed interface EngineStatus {
    data class Ready(val models: List<String>) : EngineStatus
    data object NoModels : EngineStatus
    data class NotRunning(val details: String) : EngineStatus
}

/** «Мозг» ассистента. Сейчас — локальная модель через Ollama; интерфейс позволяет подключить и другую. */
interface LlmEngine {
    suspend fun status(): EngineStatus
    suspend fun chat(model: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply
}

/** Минимальный HTTP-клиент: реализация своя на каждой платформе (на ПК — java.net.http). */
interface SimpleHttp {
    /** Возвращает (код ответа, тело). Сетевые ошибки — исключением. */
    suspend fun get(url: String): Pair<Int, String>
    suspend fun post(url: String, json: String): Pair<Int, String>
}

expect fun platformHttp(): SimpleHttp
