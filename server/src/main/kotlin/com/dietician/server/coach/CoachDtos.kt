package com.dietician.server.coach

import kotlinx.serialization.Serializable

/**
 * iter-11 — request/response data classes for the 2-phase commit Coach surface.
 *
 * Wire format mirrors Plan-3 conventions: snake_case ignored (kotlinx.serialization
 * defaults to property names), all fields explicit, no defaults that hide intent.
 *
 * idempotencyKey is a client-generated UUIDv4 persisted to the desktop outbox BEFORE
 * the LlmCall. Same key flows into /coach/commit so server can dedupe retries.
 */
@Serializable
data class CoachReserveRequest(
    val idempotencyKey: String,
    val prompt: String,
    val locale: String,
    val provider: String,
    val estimatedCostCents: Int,
    val reservationTtlSeconds: Int,
)

@Serializable
data class CoachReserveResponse(
    val reservationId: String,
    val auditId: String,
    val redactedPromptHash: String,
    val reservedUntilEpochMs: Long,
)

@Serializable
data class CoachReserveRejected(
    val reason: String,
    val capUsd: Double? = null,
    val spentUsd: Double? = null,
)

@Serializable
data class CoachCommitRequest(
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
data class CoachCommitResponse(val auditId: String, val status: String)

@Serializable
data class CoachStreamRequest(
    val idempotencyKey: String,
    val prompt: String,
    val locale: String,
)
