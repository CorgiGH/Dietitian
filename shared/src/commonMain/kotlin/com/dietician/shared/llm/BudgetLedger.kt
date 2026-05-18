package com.dietician.shared.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Reserve-then-finalize budget interface.
 *
 * Two-phase contract:
 *   1. [reserve] adds [estimateCostCents] to the subject+provider ledger up front. If the
 *      addition would breach the per-subject cap, [LlmError.BudgetExhausted] is thrown
 *      BEFORE the upstream call happens — preserving Plan-2's "no LLM call without
 *      headroom" invariant.
 *   2. [finalize] corrects the reserved value by the delta between estimate and realized.
 *      Called after the LLM response returns and `ModelPriceLookup.computeCostCents` runs.
 *   3. [release] reverses the reservation entirely (used on LLM call failure).
 *
 * Server-side production impl: wraps Plan-3's `BudgetRepository.consumeOrFail` which is
 * backed by V019 PG fn `consume_or_fail` (atomic UPDATE … WHERE used+? <= cap … RETURNING).
 * Plan-2 Batch B/C will land that wrapper. Plan-2 Batch A ships the interface + in-memory
 * impl for client-side dry-runs + unit tests.
 *
 * Council 1779062699 RC7 cross-cut: under N concurrent reserve() calls the in-memory impl
 * MUST serialize via [Mutex] so the budget cap holds atomically (the V019 PG fn provides
 * the same guarantee at the DB level via a single UPDATE statement).
 */
interface BudgetLedger {
    suspend fun reserve(
        subjectId: String,
        provider: ProviderId,
        estimateTokens: Int,
        estimateCostCents: Int,
    ): Reservation

    suspend fun finalize(reservation: Reservation, actualTokens: Int, actualCostCents: Int)

    suspend fun release(reservation: Reservation)
}

data class Reservation(
    val id: String,
    val subjectId: String,
    val provider: ProviderId,
    val reservedTokens: Int,
    val reservedCostCents: Int,
)

/**
 * In-memory [BudgetLedger]. Used by:
 *   - unit tests for Router behavior (Batch B)
 *   - client-side dry-run preview ("can subject X afford this prompt?" without DB round-trip)
 *
 * NOT used in :server production. The server wires `BudgetRepositoryAdapter` (Batch B/C)
 * around Plan-3 `BudgetRepository`.
 */
class InMemoryBudgetLedger(
    private val capCentsPerSubject: Map<String, Int> = emptyMap(),
) : BudgetLedger {
    private val mutex = Mutex()
    private val used = mutableMapOf<Pair<String, ProviderId>, Int>()

    override suspend fun reserve(
        subjectId: String,
        provider: ProviderId,
        estimateTokens: Int,
        estimateCostCents: Int,
    ): Reservation =
        mutex.withLock {
            val key = subjectId to provider
            val current = used.getOrDefault(key, 0)
            val cap = capCentsPerSubject[subjectId] ?: Int.MAX_VALUE
            if (current + estimateCostCents > cap) throw LlmError.BudgetExhausted(provider)
            used[key] = current + estimateCostCents
            Reservation(
                id = randomId(),
                subjectId = subjectId,
                provider = provider,
                reservedTokens = estimateTokens,
                reservedCostCents = estimateCostCents,
            )
        }

    override suspend fun finalize(reservation: Reservation, actualTokens: Int, actualCostCents: Int) {
        mutex.withLock {
            val key = reservation.subjectId to reservation.provider
            val diff = actualCostCents - reservation.reservedCostCents
            used[key] = (used.getOrDefault(key, 0) + diff).coerceAtLeast(0)
        }
    }

    override suspend fun release(reservation: Reservation) {
        mutex.withLock {
            val key = reservation.subjectId to reservation.provider
            used[key] = (used.getOrDefault(key, 0) - reservation.reservedCostCents).coerceAtLeast(0)
        }
    }

    /** Test-helper: observe used cents (no mutation). */
    suspend fun usedCents(subjectId: String, provider: ProviderId): Int =
        mutex.withLock { used.getOrDefault(subjectId to provider, 0) }

    private fun randomId(): String {
        val bytes = ByteArray(16).also { Random.nextBytes(it) }
        return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
