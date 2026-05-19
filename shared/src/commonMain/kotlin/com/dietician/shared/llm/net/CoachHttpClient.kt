package com.dietician.shared.llm.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * iter-11 — HTTP wrapper for `/coach/reserve`, `/coach/commit`, `/coach/stream`.
 *
 * The SSE parser strips `data: ` prefixes from data frames, ignores `: ...`
 * comment frames (server heartbeat), and ignores `event: ...` frames
 * (`event: end` terminal, `event: timeout` idle). Terminates on stream-close.
 */
class CoachHttpClient(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    @Serializable
    data class ReserveBody(
        val idempotencyKey: String,
        val prompt: String,
        val locale: String,
        val provider: String,
        val estimatedCostCents: Int,
        val reservationTtlSeconds: Int,
    )

    @Serializable
    data class ReserveResponse(
        val reservationId: String,
        val auditId: String,
        val redactedPromptHash: String,
        val reservedUntilEpochMs: Long,
    )

    @Serializable
    data class CommitBody(
        val idempotencyKey: String,
        val status: String,
        val promptTokens: Int,
        val completionTokens: Int,
        val costCents: Int,
        val provider: String,
        val latencyMs: Long,
        val responseHash: String,
    )

    @Serializable
    data class CommitResponse(val auditId: String, val status: String)

    @Serializable
    data class StreamBody(val idempotencyKey: String, val prompt: String, val locale: String)

    @Suppress("LongParameterList")
    suspend fun reserve(
        idempotencyKey: String,
        prompt: String,
        locale: String,
        provider: String,
        estimatedCostCents: Int,
        reservationTtlSeconds: Int,
    ): ReserveResponse {
        val resp =
            http.post("$baseUrl/coach/reserve") {
                contentType(ContentType.Application.Json)
                setBody(
                    ReserveBody(
                        idempotencyKey,
                        prompt,
                        locale,
                        provider,
                        estimatedCostCents,
                        reservationTtlSeconds,
                    ),
                )
            }
        return resp.body()
    }

    @Suppress("LongParameterList")
    suspend fun commit(
        idempotencyKey: String,
        status: String,
        promptTokens: Int,
        completionTokens: Int,
        costCents: Int,
        provider: String,
        latencyMs: Long,
        responseHash: String,
    ): CommitResponse {
        val resp =
            http.post("$baseUrl/coach/commit") {
                contentType(ContentType.Application.Json)
                setBody(
                    CommitBody(
                        idempotencyKey,
                        status,
                        promptTokens,
                        completionTokens,
                        costCents,
                        provider,
                        latencyMs,
                        responseHash,
                    ),
                )
            }
        return resp.body()
    }

    fun stream(idempotencyKey: String, prompt: String, locale: String): Flow<String> =
        flow {
            val resp =
                http.post("$baseUrl/coach/stream") {
                    contentType(ContentType.Application.Json)
                    setBody(StreamBody(idempotencyKey, prompt, locale))
                }
            val channel = resp.bodyAsChannel()
            // SSE event blocks are separated by blank lines. An `event: <type>`
            // line scopes ALL data lines in the same block to that event — non-
            // default events (e.g. `end`, `timeout`) are not chat tokens and
            // must be filtered out alongside the comment heartbeat.
            var blockIsNonDefaultEvent = false
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) {
                    blockIsNonDefaultEvent = false
                    continue
                }
                if (line.startsWith(":")) continue
                if (line.startsWith("event:")) {
                    blockIsNonDefaultEvent = true
                    continue
                }
                if (line.startsWith("data:") && !blockIsNonDefaultEvent) {
                    val payload = line.removePrefix("data:")
                    emit(if (payload.startsWith(" ")) payload.drop(1) else payload)
                }
            }
        }
}
