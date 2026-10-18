package com.appprogress.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Инструмент, которым ассистент пользуется для работы с данными приложения.
 * [writes] = true — инструмент что-то меняет; такие вызовы выполняются только после подтверждения человеком.
 */
interface AssistantTool {
    val spec: ToolSpec
    val writes: Boolean get() = false

    /** Человеческое описание действия для карточки подтверждения: «Создать заметку „Покупки“». */
    suspend fun describe(args: JsonObject): String = spec.name

    /** Что ассистент делает при чтении — строка в чате: «Ищу в заметках: „отпуск“». */
    fun activity(args: JsonObject): String = "Использую ${spec.name}"

    /** Выполняет инструмент и возвращает текст результата для модели. */
    suspend fun run(args: JsonObject): String
}

/** Удобная сборка JSON-схемы параметров: params("query" to "Что искать"). Все параметры — строки. */
fun stringParams(vararg params: Pair<String, String>, required: List<String> = params.map { it.first }): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        params.forEach { (name, description) ->
            put(name, buildJsonObject { put("type", "string"); put("description", description) })
        }
    })
    put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
}

fun JsonObject.str(name: String): String = (this[name] as? JsonPrimitive)?.jsonPrimitive?.content.orEmpty()
