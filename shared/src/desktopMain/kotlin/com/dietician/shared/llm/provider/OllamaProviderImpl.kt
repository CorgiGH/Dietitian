package com.dietician.shared.llm.provider

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Desktop HTTP impl for [OllamaProvider]. Targets locally-running `ollama serve` on
 * [endpoint] (default `http://localhost:11434`). Availability is best-effort — the Router
 * (Plan-2 Batch C) treats a connect-refused on first call as "not available" and skips
 * the Ollama rung in the chain.
 */
class OllamaProviderImpl(
    private val client: HttpClient,
    private val endpoint: String = "http://localhost:11434",
) : OllamaProvider {
    override suspend fun embed(text: String, model: String): FloatArray {
        val response: OllamaEmbedResponse = client.post("$endpoint/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbedRequest(model = model, prompt = text))
        }.body()
        return FloatArray(response.embedding.size) { i -> response.embedding[i].toFloat() }
    }

    /**
     * Availability heuristic: caller can pre-check by trying an embed and catching, OR rely on
     * the Router's failover logic to skip Ollama on connect-refused. We return `true` here so
     * the Router attempts a call rather than silently skipping on a fresh-install desktop.
     */
    override fun isAvailable(): Boolean = true
}
