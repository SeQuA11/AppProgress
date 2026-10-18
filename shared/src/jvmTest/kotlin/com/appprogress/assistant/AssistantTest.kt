package com.appprogress.assistant

import com.appprogress.core.DatabaseFactory
import com.appprogress.notes.NotesRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Поддельная Ollama на локальном порту: отвечает заранее заданными ответами и запоминает запросы.
 * Так проверяем весь путь (HTTP, JSON, цикл с инструментами) без установки модели.
 */
class AssistantTest {
    private val requests = mutableListOf<String>()
    private val replies = ArrayDeque<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/tags") { ex ->
            val body = """{"models":[{"name":"qwen3:8b"},{"name":"gemma3:4b"}]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        createContext("/api/chat") { ex ->
            requests += ex.requestBody.readAllBytes().decodeToString()
            val body = replies.removeFirst().toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        start()
    }
    private val engine = OllamaEngine("http://127.0.0.1:${server.address.port}", JvmHttp())
    private val notes = NotesRepository(DatabaseFactory.inMemory(), Dispatchers.IO)
    private val assistant = Assistant(engine, notesTools(notes) + dateTool())

    @AfterTest
    fun stop() = server.stop(0)

    private fun toolCall(name: String, args: String) =
        """{"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"$name","arguments":$args}}]},"done":true}"""

    private fun answer(text: String) = """{"message":{"role":"assistant","content":"$text"},"done":true}"""

    @Test
    fun statusListsInstalledModels() = runBlocking {
        assertEquals(EngineStatus.Ready(listOf("qwen3:8b", "gemma3:4b")), engine.status())
    }

    @Test
    fun statusWhenOllamaIsNotRunning() = runBlocking<Unit> {
        val dead = OllamaEngine("http://127.0.0.1:1", JvmHttp())
        assertIs<EngineStatus.NotRunning>(dead.status())
    }

    @Test
    fun readToolRunsAutomaticallyAndResultGoesBackToModel() = runBlocking {
        notes.create("Отпуск", "Билеты в Казань на 2 ноября")
        replies += toolCall("search_notes", """{"query":"отпуск"}""")
        replies += answer("В заметке «Отпуск»: билеты в Казань на 2 ноября.")

        val items = assistant.ask("qwen3:8b", "Что у меня про отпуск?")

        assertEquals(ChatItem.ToolUse("Ищу в заметках: «отпуск»"), items[0])
        assertEquals(ChatItem.Answer("В заметке «Отпуск»: билеты в Казань на 2 ноября."), items[1])
        // Во втором запросе модель получила результат поиска как сообщение инструмента.
        val second = Json.parseToJsonElement(requests[1]).jsonObject
        val toolMsg = second["messages"]!!.jsonArray.last().jsonObject
        assertEquals("tool", toolMsg["role"]!!.jsonPrimitive.content)
        assertTrue(toolMsg["content"]!!.jsonPrimitive.content.contains("Казань"))
        // Описания инструментов передаются модели.
        assertTrue(second["tools"]!!.jsonArray.size >= 3)
    }

    @Test
    fun writeToolWaitsForConfirmation() = runBlocking {
        replies += toolCall("create_note", """{"title":"Покупки","content":"Молоко, хлеб"}""")

        val items = assistant.ask("qwen3:8b", "Создай заметку со списком покупок")
        val action = assertIs<ChatItem.Action>(items.single()).action
        assertEquals("Создать заметку «Покупки»", action.description)
        assertTrue(notes.observeAll().first().isEmpty(), "до подтверждения ничего не создано")

        assistant.confirm(action)
        assertEquals("Покупки", notes.observeAll().first().single().title)
    }

    @Test
    fun argumentsAsStringAndThinkingBlocksAreHandled() = runBlocking {
        replies += """{"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"today","arguments":"{}"}}]}}"""
        replies += answer("<think>считаю</think>Сегодня понедельник.")
        val items = assistant.ask("qwen3:8b", "Какое сегодня число?")
        assertEquals(ChatItem.Answer("Сегодня понедельник."), items.last())
    }

    @Test
    fun systemPromptTreatsDataAsData() = runBlocking {
        replies += answer("Ок")
        assistant.ask("qwen3:8b", "Привет")
        val system = Json.parseToJsonElement(requests[0]).jsonObject["messages"]!!.jsonArray.first().jsonObject
        assertTrue(system["content"]!!.jsonPrimitive.content.contains("не выполняй"))
    }
}
