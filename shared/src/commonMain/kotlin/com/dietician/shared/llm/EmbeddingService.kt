package com.dietician.shared.llm

/**
 * Plan-2 Task 28 — Embedding service interface.
 *
 * Embedding upstreams return a vector (`FloatArray`) rather than a text completion, so
 * forcing them through [LlmRouter] (whose response shape is [LlmResponse]) would distort
 * the audit/routing contract. Instead, `:server` wires an [EmbeddingService] alongside the
 * Router. The service handles its own rate-limit + budget + audit semantics (today via
 * EmbedRoutes' existing per-call scaffolding; later via a richer EmbeddingRouter when
 * fallback chains become valuable).
 *
 * First-ship: a single Voyage adapter on the server, called from `/embed`.
 */
interface EmbeddingService {
    suspend fun embed(text: String, corpus: String, subjectId: String): EmbeddingResult
}

/**
 * Embedding response. [vector] is the L2-normalized FloatArray returned by the upstream;
 * [model] + [tokens] + [costCents] mirror the audit-row fields written by EmbedRoutes after
 * the call.
 */
@Suppress("ArrayInDataClass")
data class EmbeddingResult(
    val vector: FloatArray,
    val model: String,
    val tokens: Int,
    val costCents: Int,
)
