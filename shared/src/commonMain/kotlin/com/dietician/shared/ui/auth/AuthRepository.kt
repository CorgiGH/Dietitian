package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * Thin Ktor wrapper over Plan-3 magic-link endpoints (`/auth/magic-link/request`
 * + `/auth/magic-link/verify`).
 *
 * Plan-3 anti-enumeration contract: `/request` always returns 202 — never 404 even
 * when the email is unknown. [requestMagicLink] therefore returns [Result.success]
 * on any 2xx. On network failure it returns [Result.failure].
 *
 * `/verify` returns 200 + `{sessionId, subjectId, expiresAt}` on a valid token;
 * 401 on expired/invalid. The Ktor [HttpClient] is configured by the caller so we
 * keep this class agnostic of cookie storage / interceptor wiring.
 */
class AuthRepository(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    @Serializable
    private data class MagicLinkRequestBody(val email: String)

    @Serializable
    private data class MagicLinkVerifyBody(val token: String)

    @Serializable
    data class VerifyResponse(
        val sessionId: String,
        val subjectId: String,
        val expiresAt: String,
    )

    /** Always succeeds on 202 (anti-enumeration). Only fails on network error. */
    suspend fun requestMagicLink(email: String): Result<Unit> =
        runCatching {
            http.post {
                url("$baseUrl/auth/magic-link/request")
                contentType(ContentType.Application.Json)
                setBody(MagicLinkRequestBody(email))
            }
            Unit
        }

    /**
     * Verifies a magic-link token. On success the [Session] is stored in
     * [SessionStore]. On 401 the failure carries [AuthError.InvalidToken];
     * other non-2xx → [AuthError.Server]; network failure → [AuthError.Network].
     */
    suspend fun verifyMagicLink(token: String): Result<Session> =
        runCatching {
            val response = http.post {
                url("$baseUrl/auth/magic-link/verify")
                contentType(ContentType.Application.Json)
                setBody(MagicLinkVerifyBody(token))
            }
            val body = response.body<VerifyResponse>()
            Session(
                sessionId = body.sessionId,
                subjectId = body.subjectId,
                expiresAt = body.expiresAt,
            )
        }.onSuccess { SessionStore.set(it) }.recoverCatching { t ->
            throw mapError(t)
        }

    private fun mapError(t: Throwable): AuthError =
        when (t) {
            is ResponseException ->
                if (t.response.status == HttpStatusCode.Unauthorized) {
                    AuthError.InvalidToken
                } else {
                    AuthError.Server(t.response.status.value)
                }
            else -> AuthError.Network(t.message ?: "network error")
        }
}

/** Typed auth-failure modes consumed by the UI to choose a retry surface. */
sealed class AuthError(message: String) : RuntimeException(message) {
    data object InvalidToken : AuthError("Magic link expired or invalid — request a new one.")
    data class Server(val statusCode: Int) : AuthError("Server returned $statusCode.")
    data class Network(val detail: String) : AuthError("Network error: $detail")
}
