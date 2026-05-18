package com.dietician.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Ktor-client wrapper around the Resend REST API
 * (POST https://api.resend.com/emails).
 *
 * Production wiring: API key from env `RESEND_API_KEY`. For free-tier the
 * `from` address should be `onboarding@resend.dev` (Resend's default
 * verified sender) — verified custom domains can override per call.
 *
 * The client is `Closeable` via [close]; tests should call it in tearDown.
 */
class ResendClient(
    private val apiKey: String = System.getenv("RESEND_API_KEY")
        ?: error("RESEND_API_KEY env var not set"),
    private val endpoint: String = "https://api.resend.com/emails",
    private val engine: HttpClient = HttpClient(CIO),
) : EmailSender, AutoCloseable {
    private val log = LoggerFactory.getLogger(ResendClient::class.java)

    @Serializable
    private data class ResendRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val html: String,
    )

    override suspend fun send(
        to: String,
        from: String,
        subject: String,
        htmlBody: String,
    ): Boolean {
        val body = ResendRequest(from = from, to = listOf(to), subject = subject, html = htmlBody)
        val rsp = engine.post(endpoint) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ResendRequest.serializer(), body))
        }
        return when (rsp.status) {
            HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.Created -> true
            else -> {
                // Do NOT log the body — it may echo the user email.
                log.warn("Resend rejected message: status={} reason={}", rsp.status, rsp.status.description)
                false
            }
        }
    }

    override fun close() {
        engine.close()
    }
}
