package com.dietician.shared.llm.provider

import kotlinx.serialization.Serializable

/**
 * Desktop-only Ollama embedding fallback — Plan-2 Task 13.
 *
 * Interface in commonMain so the Router (Plan-2 Batch C Task 19) can declare a dependency
 * without forcing Android (which can't reach localhost Ollama anyway — `localhost` on Android
 * is the device loopback, not the user's desktop). On Android the Router skips the Ollama
 * fallback rung by checking `OllamaProvider.isAvailable()`.
 *
 * Production wiring (desktop): user runs `ollama serve` locally; `endpoint` defaults to
 * `http://localhost:11434`. Plan-3 V019 ledger tracks per-model token spend at zero cost
 * (self-hosted; cost_cents=0 forced for ollama provider in audit row).
 */
interface OllamaProvider {
    suspend fun embed(text: String, model: String): FloatArray

    fun isAvailable(): Boolean
}

@Serializable
internal data class OllamaEmbedRequest(val model: String, val prompt: String)

@Serializable
internal data class OllamaEmbedResponse(val embedding: List<Double> = emptyList())

/** Stub used by Router on platforms without Ollama support (Android primarily). */
object NoOpOllamaProvider : OllamaProvider {
    override suspend fun embed(text: String, model: String): FloatArray =
        throw UnsupportedOperationException("Ollama not available on this platform")

    override fun isAvailable(): Boolean = false
}
