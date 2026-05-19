package com.dietician.server.coach

import com.dietician.server.db.DatabaseFactory
import java.sql.Timestamp
import java.util.UUID

/**
 * iter-11 — SQL boundary for the 2-phase commit Coach audit pipeline.
 *
 * All writes go through audit_log (V018) with the iter-11 status columns from V022
 * applied. consume_or_fail (V019) is invoked directly by CoachService via
 * BudgetRepository, NOT this repo — that keeps the budget-lock semantics colocated
 * with the orchestration.
 *
 * Connections are acquired via [DatabaseFactory.withSubject] / withSystemContext only
 * — `RlsBypassPreventionTest` (Council 1779120000 RC2) forbids raw pool connection
 * acquisition outside DatabaseFactory itself.
 *
 * Identity contract:
 *   - idempotencyKey is the load-bearing identity. The partial unique index from
 *     V022 lets the same key resolve via findByIdempotencyKey for commit retries.
 *   - auditId (PK) is server-allocated on insertPendingAudit; returned for caller
 *     visibility but not load-bearing for retries.
 */
@Suppress("MagicNumber", "LongParameterList")
class CoachRepository(private val db: DatabaseFactory) {

    data class AuditRow(
        val auditId: UUID,
        val subjectId: UUID,
        val status: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val costCents: Int?,
        val reservedUntilMs: Long?,
    )

    fun insertPendingAudit(
        subjectId: UUID,
        idempotencyKey: UUID,
        promptHash: String,
        provider: String,
        estimatedCostCents: Int,
        reservationTtlSeconds: Int,
    ): Pair<UUID, Long> =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                INSERT INTO audit_log
                    (subject_id, kind, model, prompt_hash, cost_cents,
                     status, idempotency_key, reserved_until)
                VALUES (?, 'llm_call', ?, ?, ?, 'pending', ?,
                        now() + make_interval(secs => ?))
                RETURNING id, extract(epoch from reserved_until) * 1000
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setString(2, provider)
                ps.setString(3, promptHash)
                ps.setInt(4, estimatedCostCents)
                ps.setObject(5, idempotencyKey)
                ps.setInt(6, reservationTtlSeconds)
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getObject(1, UUID::class.java) to rs.getDouble(2).toLong()
                }
            }
        }

    fun updateAuditOnCommit(
        subjectId: UUID,
        idempotencyKey: UUID,
        status: String,
        promptTokens: Int,
        completionTokens: Int,
        costCents: Int,
        provider: String,
        latencyMs: Long,
        responseHash: String,
    ) {
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                UPDATE audit_log
                SET status = ?,
                    model = ?,
                    input_tokens = ?,
                    output_tokens = ?,
                    cost_cents = ?,
                    response_hash = ?,
                    reserved_until = NULL,
                    extra = jsonb_set(COALESCE(extra, '{}'::jsonb),
                                      '{latency_ms}', to_jsonb(?::bigint))
                WHERE idempotency_key = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, status)
                ps.setString(2, provider)
                ps.setInt(3, promptTokens)
                ps.setInt(4, completionTokens)
                ps.setInt(5, costCents)
                ps.setString(6, responseHash)
                ps.setLong(7, latencyMs)
                ps.setObject(8, idempotencyKey)
                ps.executeUpdate()
            }
        }
    }

    fun findByIdempotencyKey(subjectId: UUID, idempotencyKey: UUID): AuditRow? =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                SELECT id, subject_id, status, input_tokens, output_tokens,
                       cost_cents, reserved_until
                FROM audit_log
                WHERE idempotency_key = ?
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, idempotencyKey)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@withSubject null
                    AuditRow(
                        auditId = rs.getObject(1, UUID::class.java),
                        subjectId = rs.getObject(2, UUID::class.java),
                        status = rs.getString(3),
                        promptTokens = rs.getObject(4) as Int?,
                        completionTokens = rs.getObject(5) as Int?,
                        costCents = rs.getObject(6) as Int?,
                        reservedUntilMs = (rs.getTimestamp(7) as Timestamp?)?.time,
                    )
                }
            }
        }
}
