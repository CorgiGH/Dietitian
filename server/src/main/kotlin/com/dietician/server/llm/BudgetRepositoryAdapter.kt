package com.dietician.server.llm

import com.dietician.server.repo.BudgetRepository
import com.dietician.shared.llm.BudgetLedger
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Reservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Plan-2 Task 28 — Server-side adapter from `:shared:llm` [BudgetLedger] to Plan-3
 * [BudgetRepository].
 *
 * `reserve` maps to V019 `consume_or_fail` (atomic UPDATE … WHERE used+? <= cap RETURNING).
 * On a false return (cap breach), throws [LlmError.BudgetExhausted] so the Router skips this
 * provider and continues the failover chain.
 *
 * `finalize` adjusts `cost_cents_used` by the actual-vs-estimate delta + bumps
 * `finalized_tokens`. `release` reverses the reservation entirely.
 *
 * The repo is synchronous JDBC; coroutine callers are bounced through [Dispatchers.IO].
 */
class BudgetRepositoryAdapter(
    private val repo: BudgetRepository,
) : BudgetLedger {
    override suspend fun reserve(
        subjectId: String,
        provider: ProviderId,
        estimateTokens: Int,
        estimateCostCents: Int,
    ): Reservation {
        val uuid = parseSubjectUuid(subjectId)
        val ok = withContext(Dispatchers.IO) {
            repo.consumeOrFail(
                subjectId = uuid,
                provider = provider.raw,
                tokensNeeded = estimateTokens,
                costCentsEstimated = estimateCostCents,
            )
        }
        if (!ok) throw LlmError.BudgetExhausted(provider)
        return Reservation(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            provider = provider,
            reservedTokens = estimateTokens,
            reservedCostCents = estimateCostCents,
        )
    }

    override suspend fun finalize(reservation: Reservation, actualTokens: Int, actualCostCents: Int) {
        val uuid = parseSubjectUuid(reservation.subjectId)
        val delta = actualCostCents - reservation.reservedCostCents
        withContext(Dispatchers.IO) {
            repo.finalize(
                subjectId = uuid,
                provider = reservation.provider.raw,
                actualTokens = actualTokens,
                costCentsDelta = delta,
            )
        }
    }

    override suspend fun release(reservation: Reservation) {
        val uuid = parseSubjectUuid(reservation.subjectId)
        withContext(Dispatchers.IO) {
            repo.release(
                subjectId = uuid,
                provider = reservation.provider.raw,
                reservedTokens = reservation.reservedTokens,
                reservedCostCents = reservation.reservedCostCents,
            )
        }
    }

    private fun parseSubjectUuid(s: String): UUID = try {
        UUID.fromString(s)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Router subjectId must be a UUID for :server adapter, got: $s", e)
    }
}
