package com.dietician.server.coach

import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.PiiRedactor
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.security.MessageDigest
import java.util.UUID

/**
 * iter-11 — orchestration for the 2-phase commit Coach pipeline.
 *
 * reserve() ordering matters:
 *   1. Idempotency dedupe via [CoachRepository.findByIdempotencyKey] — repeated
 *      calls with the same key return the existing reservation without re-locking
 *      the budget (avoids double-charge under client retries).
 *   2. PII redaction on the prompt ([PiiRedactor] from `:shared:llm`).
 *   3. consume_or_fail row-lock on llm_budget (BudgetRepository) — fail fast
 *      if cap exceeded BEFORE the audit row is written. This is the saga gate.
 *   4. insertPendingAudit writes status=pending with the redacted prompt hash
 *      + estimated cost (T1 contract: cost_cents = estimate, model = provider).
 *   5. Return reservation envelope.
 *
 * commit() is idempotent — a duplicate call after the audit row has been moved
 * out of `pending` returns the stored terminal status without re-writing.
 */
sealed interface CoachServiceReserveResult {
    data class Reserved(val envelope: CoachReserveResponse) : CoachServiceReserveResult

    data class Rejected(
        val reason: String,
        val capUsd: Double? = null,
        val spentUsd: Double? = null,
    ) : CoachServiceReserveResult
}

@Suppress("LongParameterList")
class CoachService(
    private val repo: CoachRepository,
    private val budgets: BudgetRepository,
    private val redactor: PiiRedactor,
    private val llmStream: LlmStream,
) {
    fun reserve(
        subjectId: UUID,
        request: CoachReserveRequest,
    ): CoachServiceReserveResult {
        val key = UUID.fromString(request.idempotencyKey)

        repo.findByIdempotencyKey(subjectId, key)?.let { existing ->
            return CoachServiceReserveResult.Reserved(
                CoachReserveResponse(
                    reservationId = existing.auditId.toString(),
                    auditId = existing.auditId.toString(),
                    redactedPromptHash = "replay",
                    reservedUntilEpochMs = existing.reservedUntilMs
                        ?: (System.currentTimeMillis() + request.reservationTtlSeconds * 1000L),
                ),
            )
        }

        val redacted = redactor.redact(request.prompt).text
        val promptHash = sha256(redacted)
        val budgetOk =
            budgets.consumeOrFail(
                subjectId = subjectId,
                provider = request.provider,
                tokensNeeded = 0,
                costCentsEstimated = request.estimatedCostCents,
            )
        if (!budgetOk) {
            return CoachServiceReserveResult.Rejected(reason = "over_budget")
        }
        val (auditId, reservedUntilMs) =
            repo.insertPendingAudit(
                subjectId = subjectId,
                idempotencyKey = key,
                promptHash = promptHash,
                provider = request.provider,
                estimatedCostCents = request.estimatedCostCents,
                reservationTtlSeconds = request.reservationTtlSeconds,
            )
        return CoachServiceReserveResult.Reserved(
            CoachReserveResponse(
                reservationId = auditId.toString(),
                auditId = auditId.toString(),
                redactedPromptHash = promptHash,
                reservedUntilEpochMs = reservedUntilMs,
            ),
        )
    }

    fun commit(
        subjectId: UUID,
        request: CoachCommitRequest,
    ): CoachCommitResponse {
        val key = UUID.fromString(request.idempotencyKey)
        val existing =
            repo.findByIdempotencyKey(subjectId, key)
                ?: error("commit before reserve: $key")
        if (existing.status != "pending") {
            return CoachCommitResponse(auditId = existing.auditId.toString(), status = existing.status)
        }
        repo.updateAuditOnCommit(
            subjectId = subjectId,
            idempotencyKey = key,
            status = request.status,
            promptTokens = request.promptTokens,
            completionTokens = request.completionTokens,
            costCents = request.costCents,
            provider = request.provider,
            latencyMs = request.latencyMs,
            responseHash = request.responseHash,
        )
        return CoachCommitResponse(auditId = existing.auditId.toString(), status = request.status)
    }

    /**
     * Server-routed SSE flow used by Android + Desktop-non-ClaudeMax fallback.
     * Internally pairs reserve → LlmRouter.streamRoute → commit in one coroutine.
     * Each emitted [String] is a token chunk text payload; heartbeat frames are
     * inserted by the route handler (T11), not here.
     *
     * On reserve rejection: emits "event: error\ndata: <reason>" and returns.
     * On provider failure mid-stream: commit with status='failed' + rethrow.
     */
    fun streamServerRouted(
        subjectId: UUID,
        request: CoachStreamRequest,
    ): Flow<String> =
        flow {
            val reserved =
                reserve(
                    subjectId,
                    CoachReserveRequest(
                        idempotencyKey = request.idempotencyKey,
                        prompt = request.prompt,
                        locale = request.locale,
                        provider = "openrouter",
                        estimatedCostCents = DEFAULT_ESTIMATE_COST_CENTS,
                        reservationTtlSeconds = DEFAULT_RESERVATION_TTL_SECONDS,
                    ),
                )
            if (reserved is CoachServiceReserveResult.Rejected) {
                emit("event: error\ndata: ${reserved.reason}")
                return@flow
            }
            val startMs = System.currentTimeMillis()
            var totalCompletionTokens = 0
            var finalResponse: LlmResponse? = null
            var status = "success"
            @Suppress("TooGenericExceptionCaught")
            try {
                llmStream.streamRoute(
                    LlmRequest(
                        subjectId = subjectId.toString(),
                        task = TaskType.TEXT,
                        deviceClass = DeviceClass.ANY,
                        capability = Capability.STREAMING,
                        messages =
                        listOf(
                            LlmMessage(Role.USER, request.prompt),
                        ),
                        systemPrompt = CoachSystemPrompts.forLocale(request.locale),
                    ),
                ).collect { chunk ->
                    emit(chunk.text)
                    if (chunk.tokenCount > 0) totalCompletionTokens = chunk.tokenCount
                    if (chunk.isDone) finalResponse = chunk.finalResponse
                }
            } catch (t: Throwable) {
                status = "failed"
                throw t
            } finally {
                commit(
                    subjectId,
                    CoachCommitRequest(
                        idempotencyKey = request.idempotencyKey,
                        status = status,
                        promptTokens = finalResponse?.inputTokens ?: 0,
                        completionTokens = finalResponse?.outputTokens ?: totalCompletionTokens,
                        costCents = finalResponse?.costCents ?: 0,
                        provider = finalResponse?.provider?.raw?.lowercase() ?: "openrouter",
                        latencyMs = System.currentTimeMillis() - startMs,
                        responseHash = finalResponse?.text?.let { sha256(it) } ?: "n/a",
                    ),
                )
            }
        }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_ESTIMATE_COST_CENTS = 5
        const val DEFAULT_RESERVATION_TTL_SECONDS = 60
    }
}
