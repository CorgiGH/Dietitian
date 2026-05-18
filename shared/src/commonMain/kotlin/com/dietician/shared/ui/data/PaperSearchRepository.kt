package com.dietician.shared.ui.data

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
 * Paper-search repo. Calls Plan-3 `POST /embed` with `{text: query}`.
 *
 * Outcomes:
 *   - Server returns 501 (vector backend not ready) → [PaperSearchOutcome.NotImplemented].
 *     UI surfaces "Search coming soon, embedding service is still ramping" copy.
 *   - Server returns 200 + vector → fan to per-corpus search (deferred to Plan-7).
 *     For Batch C first-ship: vector ≠ null but no downstream corpus search is wired,
 *     so we return [PaperSearchOutcome.Empty] until Plan-7 backfill. Tests fake the
 *     fully-wired [PaperSearchOutcome.Hits] outcome to exercise the UI path.
 *   - Network / 4xx / 5xx → [PaperSearchOutcome.Failed].
 *
 * Tests inject a Fake [PaperSearchRepository] directly. Production wiring lands in
 * Batch E Task 27 Koin module + Plan-7 corpus.
 */
interface PaperSearchRepository {
    suspend fun search(query: String, corpora: Set<String>): PaperSearchOutcome
}

/**
 * UI-layer paper result row. Score is the cosine similarity (or trigram score on
 * fallback path); UI shows it as a small grey number.
 */
data class PaperResult(
    val id: String,
    val title: String,
    val abstractSnippet: String,
    val score: Double,
)

sealed interface PaperSearchOutcome {
    data class Hits(val results: List<PaperResult>) : PaperSearchOutcome
    data object Empty : PaperSearchOutcome
    data object NotImplemented : PaperSearchOutcome
    data class Failed(val reason: String) : PaperSearchOutcome
}

/**
 * Default Ktor-backed [PaperSearchRepository]. Wired by Koin in Batch E.
 */
class HttpPaperSearchRepository(
    private val http: HttpClient,
    private val baseUrl: String,
) : PaperSearchRepository {

    @Serializable
    private data class EmbedBody(val text: String)

    @Serializable
    private data class EmbedResponse(val vector: List<Double>? = null)

    override suspend fun search(query: String, corpora: Set<String>): PaperSearchOutcome = try {
        val response = http.post {
            url("$baseUrl/embed")
            contentType(ContentType.Application.Json)
            setBody(EmbedBody(text = query))
        }
        val parsed = response.body<EmbedResponse>()
        if (parsed.vector.isNullOrEmpty()) {
            PaperSearchOutcome.Empty
        } else {
            // Plan-7 corpus search fans out here. First-ship: Empty.
            PaperSearchOutcome.Empty
        }
    } catch (e: ResponseException) {
        if (e.response.status == HttpStatusCode.NotImplemented) {
            PaperSearchOutcome.NotImplemented
        } else {
            PaperSearchOutcome.Failed("server ${e.response.status.value}")
        }
    } catch (t: Throwable) {
        PaperSearchOutcome.Failed(t.message ?: "network error")
    }
}
