package com.appprogress.assistant

import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** HTTP через встроенный в Java клиент. Ответ модели может идти минуты — таймаут большой. */
class JvmHttp(private val requestTimeout: Duration = Duration.ofMinutes(5)) : SimpleHttp {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    override suspend fun get(url: String): Pair<Int, String> =
        send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(5)).GET().build())

    override suspend fun post(url: String, json: String): Pair<Int, String> = send(
        HttpRequest.newBuilder(URI(url)).timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build(),
    )

    private suspend fun send(request: HttpRequest): Pair<Int, String> {
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        return response.statusCode() to response.body()
    }
}

actual fun platformHttp(): SimpleHttp = JvmHttp()
