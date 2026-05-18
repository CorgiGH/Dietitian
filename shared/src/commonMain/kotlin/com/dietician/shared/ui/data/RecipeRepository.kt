package com.dietician.shared.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * UI-layer recipe corpus surface.
 *
 * Read: [RecipeReader.all] returns the local recipes corpus. Plan-1 V010
 * recipes table is empty at first-ship — the empty list drives the
 * "no recipes yet — ingest your first" empty-state in CookbookScreen.
 *
 * Ingest: [RecipeIngestClient.ingest] POSTs to Plan-3 `/jobs/queue` with
 * `{kind: "recipe_ingest", url: "..."}`. The server-side ingest pipeline
 * lands in Plan-3 Task 25 — first-ship returns 501 and the client falls
 * back to "Queued for local outbox" semantics so the URL is preserved.
 */
interface RecipeReader {
    suspend fun all(): List<Recipe>
}

/**
 * Local-corpus recipe row. Matches Plan-1 V010 recipes table columns; full
 * nutritional + provenance + Romanian-tradition tags land in Plan-7.
 */
data class Recipe(
    val id: String,
    val title: String,
    val ingredientsCsv: String,
    val servings: Int,
)

/**
 * Result of an `/jobs/queue` POST. Three variants:
 *   - [Queued] — server accepted (200/202)
 *   - [NotImplemented] — server returned 501 (pipeline not yet shipped, fallback
 *     path: client stashes the URL in a local outbox row)
 *   - [Failed] — network / 4xx / 5xx
 */
sealed interface RecipeIngestResult {
    data object Queued : RecipeIngestResult
    data object NotImplemented : RecipeIngestResult
    data class Failed(val reason: String) : RecipeIngestResult
}

interface RecipeIngestClient {
    suspend fun ingest(url: String): RecipeIngestResult
}

/**
 * Default Ktor-backed [RecipeIngestClient]. Wired by Koin in Batch E.
 */
class HttpRecipeIngestClient(
    private val http: HttpClient,
    private val baseUrl: String,
) : RecipeIngestClient {

    @Serializable
    private data class IngestBody(val kind: String, val url: String)

    override suspend fun ingest(url: String): RecipeIngestResult = try {
        http.post {
            url("$baseUrl/jobs/queue")
            contentType(ContentType.Application.Json)
            setBody(IngestBody(kind = "recipe_ingest", url = url))
        }
        RecipeIngestResult.Queued
    } catch (e: ResponseException) {
        if (e.response.status == HttpStatusCode.NotImplemented) {
            RecipeIngestResult.NotImplemented
        } else {
            RecipeIngestResult.Failed("server ${e.response.status.value}")
        }
    } catch (t: Throwable) {
        RecipeIngestResult.Failed(t.message ?: "network error")
    }
}
