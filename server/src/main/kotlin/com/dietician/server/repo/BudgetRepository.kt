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
