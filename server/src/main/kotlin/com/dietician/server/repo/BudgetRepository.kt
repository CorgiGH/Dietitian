package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import java.util.UUID

/**
 * Thin Kotlin wrapper over the V019 `consume_or_fail(subject, provider,
 * tokens, cost_cents)` plpgsql function (Plan-2 two-phase reserve, RC12
 * budget ceiling).
 *
 * Returns:
 *   - true  → reservation accepted + budget bumped.
 *   - false → period cap exceeded; caller responds 402 / 429 to client.
 *
 * RLS: the call is wrapped in [DatabaseFactory.withSubject] so the function
 * sees the same `app.current_subject_id` GUC the policies check. The PG fn
 * itself is SECURITY DEFINER-free — it relies on its own UPDATE filter +
 * the policy. (V019 ships the fn as plain LANGUAGE plpgsql.)
 *
 * `consume_or_fail` returns BOOLEAN; we extract via `getBoolean(1)`.
 *
 * NOTE: this is a Plan-3 Task-28 thin repo. The two-phase finalize side
 * (reservations table → `final_tokens` / `final_cost_cents`) lives in
 * Plan-2 Router and ships in a separate batch.
 */
class BudgetRepository(private val db: DatabaseFactory) {
    /**
     * Attempts to reserve [tokensNeeded] + [costCentsEstimated] for
     * [subjectId] + [provider]. Returns true on success, false on cap hit.
     */
    fun consumeOrFail(
        subjectId: UUID,
        provider: String,
        tokensNeeded: Int,
        costCentsEstimated: Int,
    ): Boolean =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "SELECT consume_or_fail(?, ?, ?, ?)",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, provider)
                ps.setInt(3, tokensNeeded)
                ps.setInt(4, costCentsEstimated)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getBoolean(1)
                }
            }
        }

    /**
     * Plan-2 Router two-phase finalize. Adjusts `llm_budget.cost_cents_used` by
     * `actualCostCents - reservedCostCents` (positive = under-reserved, negative =
     * over-reserved) and bumps `finalized_tokens` by [actualTokens].
     *
     * `cost_cents_used` is clamped at zero — never goes negative.
     */
    fun finalize(
        subjectId: UUID,
        provider: String,
        actualTokens: Int,
        costCentsDelta: Int,
    ) {
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                UPDATE llm_budget
                SET cost_cents_used = GREATEST(0, cost_cents_used + ?),
                    finalized_tokens = finalized_tokens + ?
                WHERE subject_id = ? AND provider = ?
                  AND period_starts_at = date_trunc('month', now())::DATE
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, costCentsDelta)
                ps.setInt(2, actualTokens)
                ps.setObject(3, subjectId)
                ps.setString(4, provider)
                ps.executeUpdate()
            }
        }
    }

    /**
     * Plan-2 Router two-phase release. Reverses a prior [consumeOrFail] reservation —
     * subtracts [reservedCostCents] back from `cost_cents_used` and removes the reserved
     * token estimate.
     *
     * `cost_cents_used` + `reserved_tokens` are clamped at zero.
     */
    fun release(subjectId: UUID, provider: String, reservedTokens: Int, reservedCostCents: Int) {
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                UPDATE llm_budget
                SET cost_cents_used = GREATEST(0, cost_cents_used - ?),
                    reserved_tokens = GREATEST(0, reserved_tokens - ?)
                WHERE subject_id = ? AND provider = ?
                  AND period_starts_at = date_trunc('month', now())::DATE
                """.trimIndent(),
            ).use { ps ->
                ps.setInt(1, reservedCostCents)
                ps.setInt(2, reservedTokens)
                ps.setObject(3, subjectId)
                ps.setString(4, provider)
                ps.executeUpdate()
            }
        }
    }

    /**
     * Returns the trial-queries-remaining for [subjectId]'s current month +
     * [provider]. Computed as `cap - used` where NULL cap means unbounded.
     * Used by `/me` to render the BYOK widget. If no budget row exists yet
     * (subject hasn't called LLM this month), returns null.
     */
    fun remainingThisPeriod(subjectId: UUID, provider: String): Int? =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                SELECT cost_cents_cap, cost_cents_used FROM llm_budget
                WHERE subject_id = ? AND provider = ?
                  AND period_starts_at = date_trunc('month', now())::DATE
                LIMIT 1
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, provider)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    val cap = rs.getObject("cost_cents_cap") as? Int
                    val used = rs.getInt("cost_cents_used")
                    cap?.let { (it - used).coerceAtLeast(0) }
                }
            }
        }
}
