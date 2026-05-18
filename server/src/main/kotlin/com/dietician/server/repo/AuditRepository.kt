package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Read-only view onto V018 `audit_log`. The `AuditLogWriter` owns writes —
 * this repo is the read counterpart used by `/me/audit` exports + `/me/dsar`.
 *
 * RLS: `audit_log` policy allows `subject_id IS NULL OR subject_id = guc`,
 * so a query under the subject's context returns their own rows plus system
 * rows. Plan-3 first-ship: `/me/audit` filters to the caller subject only —
 * NULL-subject rows are NOT included in the per-subject export (those are
 * cron/backup/etc and not part of any individual's DSAR).
 */
data class AuditRow(
    val id: UUID,
    val subjectId: UUID?,
    val occurredAt: OffsetDateTime,
    val kind: String,
    val model: String?,
    val promptHash: String?,
    val responseHash: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val costCents: Int?,
    val requestId: String?,
    val extraJson: String?,
)

class AuditRepository(private val db: DatabaseFactory) {
    /**
     * Lists audit rows for [subjectId] in the half-open `[from, to)` window,
     * ordered chronologically. NULL-subject rows are excluded so the export
     * is scoped strictly to the subject.
     */
    fun list(subjectId: UUID, from: Instant, to: Instant, limit: Int = 10_000): List<AuditRow> =
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                """
                SELECT id, subject_id, occurred_at, kind, model, prompt_hash, response_hash,
                       input_tokens, output_tokens, cost_cents, request_id, extra::TEXT AS extra_json
                FROM audit_log
                WHERE subject_id = ? AND occurred_at >= ? AND occurred_at < ?
                ORDER BY occurred_at ASC
                LIMIT ?
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.setObject(2, OffsetDateTime.ofInstant(from, java.time.ZoneOffset.UTC))
                ps.setObject(3, OffsetDateTime.ofInstant(to, java.time.ZoneOffset.UTC))
                ps.setInt(4, limit)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<AuditRow>()
                    while (rs.next()) {
                        out += AuditRow(
                            id = rs.getObject("id", UUID::class.java),
                            subjectId = rs.getObject("subject_id", UUID::class.java),
                            occurredAt = rs.getObject("occurred_at", OffsetDateTime::class.java),
                            kind = rs.getString("kind"),
                            model = rs.getString("model"),
                            promptHash = rs.getString("prompt_hash"),
                            responseHash = rs.getString("response_hash"),
                            inputTokens = rs.getObject("input_tokens") as? Int,
                            outputTokens = rs.getObject("output_tokens") as? Int,
                            costCents = rs.getObject("cost_cents") as? Int,
                            requestId = rs.getString("request_id"),
                            extraJson = rs.getString("extra_json"),
                        )
                    }
                    out
                }
            }
        }
}
