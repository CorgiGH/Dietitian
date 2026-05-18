package com.dietician.shared.data.remote

import com.dietician.shared.data.api.PullRequest
import com.dietician.shared.data.api.PullResponse
import com.dietician.shared.data.api.PushRequest
import com.dietician.shared.data.api.PushResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor client wrapper for /sync/push, /sync/pull, /health. Platform driver
 * (OkHttp on Android, CIO on Desktop) is supplied via httpFactory.
 */
class SyncClient(
    private val baseUrl: String,
    private val httpFactory: () -> HttpClient,
) {
    private val http: HttpClient by lazy {
        httpFactory().config {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
            }
            // Council #2: cap network calls so a stuck socket never hangs the drain forever.
            // A 20s WAN blip used to dead-letter the entire outbox because every push call
            // returned a generic Throwable that incremented attempts to 10 on a single
            // failure. With non-infinite timeouts we get a typed timeout exception that
            // OutboxError.classifyError routes as Transient.
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    suspend fun push(req: PushRequest): PushResponse =
        http.post {
            url("$baseUrl/sync/push")
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun pull(req: PullRequest): PullResponse =
        http.post {
            url("$baseUrl/sync/pull")
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun health(): Boolean =
        runCatching {
            http.get { url("$baseUrl/health") }
        }.isSuccess
}
