package com.appprogress.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Языковая модель на этом компьютере через Ollama (https://ollama.com). Ollama — отдельная бесплатная
 * программа: она скачивает открытые модели и запускает их на видеокарте. Мы общаемся с ней по адресу
 * localhost — запросы не выходят за пределы компьютера.
 */
class OllamaEngine(
    private val baseUrl: String = DEFAULT_URL,
    private val http: SimpleHttp = platformHttp(),
) : LlmEngine {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun status(): EngineStatus {
        val (code, body) = try {
            http.get("$baseUrl/api/tags")
        } catch (e: Exception) {
            return EngineStatus.NotRunning(e.message ?: "нет соединения")
        }
        if (code != 200) return EngineStatus.NotRunning("Ollama ответила кодом $code")
        val models = json.parseToJsonElement(body).jsonObject["models"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }.orEmpty()
        return if (models.isEmpty()) EngineStatus.NoModels else EngineStatus.Ready(models)
    }

    override suspend fun chat(model: String, messages: List<LlmMessage>, tools: List<ToolSpec>): LlmReply {
        val request = buildJsonObject {
            put("model", model)
            put("stream", false)
            // Модели с «размышлениями» (например, Qwen3) отвечают быстрее и чище без них.
            put("think", false)
            put("options", buildJsonObject { put("temperature", 0.2) })
            put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
            if (tools.isNotEmpty()) put("tools", buildJsonArray { tools.forEach { add(it.toJson()) } })
        }
        val (code, body) = http.post("$baseUrl/api/chat", request.toString())
        if (code != 200) {
            val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            error("Ollama: ${error ?: "код $code"}")
        }
        val message = json.parseToJsonElement(body).jsonObject["message"]?.jsonObject ?: error("Ollama: пустой ответ")
        val calls = (message["tool_calls"] as? JsonArray).orEmpty().mapNotNull { call ->
            val fn = call.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
            val name = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            // Некоторые модели присылают аргументы строкой с JSON внутри.
            val args = when (val a = fn["arguments"]) {
                is JsonObject -> a
                is JsonPrimitive -> runCatching { json.parseToJsonElement(a.content).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                else -> JsonObject(emptyMap())
            }
            ToolCall(name, args)
        }
        return LlmReply(stripThinking(message["content"]?.jsonPrimitive?.content.orEmpty()), calls)
    }

    private fun LlmMessage.toJson() = buildJsonObject {
        put("role", role)
        put("content", content)
        toolName?.let { put("tool_name", it) }
        if (toolCalls.isNotEmpty()) {
            put("tool_calls", buildJsonArray {
                toolCalls.forEach { c -> add(buildJsonObject { put("function", buildJsonObject { put("name", c.name); put("arguments", c.arguments) }) }) }
            })
        }
    }

    private fun ToolSpec.toJson() = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        })
    }

    companion object {
        const val DEFAULT_URL = "http://localhost:11434"

        /** Убирает блок <think>…</think>, если модель всё же его прислала. */
        fun stripThinking(text: String) = text.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }
}
