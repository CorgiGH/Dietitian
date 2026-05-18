package com.dietician.shared.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * BYOK (Bring Your Own Key) repository — wraps `POST /me/byok` and `DELETE
 * /me/byok/{provider}` Plan-3 endpoints. Encryption of the key in transit
 * happens at the Tailscale tunnel layer (mTLS already provides at-rest envelope
 * — the key column is `pgp_sym_encrypt`'d server-side via Plan-3 Task 21).
 */
interface ByokRepository {
    suspend fun save(provider: String, key: String): ByokSaveOutcome
    suspend fun delete(provider: String): ByokSaveOutcome
}

sealed interface ByokSaveOutcome {
    data object Ok : ByokSaveOutcome
    data class Failed(val reason: String) : ByokSaveOutcome
}

/** Default Ktor-backed impl. Wired by Koin in Batch E. */
class HttpByokRepository(
    private val http: HttpClient,
    private val baseUrl: String,
) : ByokRepository {

    @Serializable
    private data class SaveBody(val provider: String, val key: String)

    override suspend fun save(provider: String, key: String): ByokSaveOutcome = try {
        val response = http.post {
            url("$baseUrl/me/byok")
            contentType(ContentType.Application.Json)
            setBody(SaveBody(provider = provider, key = key))
        }
        if (response.status.value in 200..299) ByokSaveOutcome.Ok
        else ByokSaveOutcome.Failed("server ${response.status.value}")
    } catch (e: ResponseException) {
        ByokSaveOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        ByokSaveOutcome.Failed(t.message ?: "network error")
    }

    override suspend fun delete(provider: String): ByokSaveOutcome = try {
        val response = http.post {
            url("$baseUrl/me/byok/delete")
            contentType(ContentType.Application.Json)
            setBody(SaveBody(provider = provider, key = ""))
        }
        if (response.status.value in 200..299) ByokSaveOutcome.Ok
        else ByokSaveOutcome.Failed("server ${response.status.value}")
    } catch (e: ResponseException) {
        ByokSaveOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        ByokSaveOutcome.Failed(t.message ?: "network error")
    }
}
