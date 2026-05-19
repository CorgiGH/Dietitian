package com.dietician.server.coach

import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.PiiRedactor
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

class CoachService(
    private val repo: CoachRepository,
    private val budgets: BudgetRepository,
    private val redactor: PiiRedactor,
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

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
