package com.dietician.server.audit

import com.dietician.server.db.DatabaseFactory
import com.dietician.server.observability.Counters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.postgresql.util.PGobject
import java.sql.Types
import java.util.UUID

/**
 * Append-only writer for the V018 `audit_log` table.
 *
 * AI Act Art 12 mandate: every LLM call + consent change + redaction + auth
 * event emits a row. Schema lives in
 * `server/src/main/resources/db/migration/V018__audit_log.sql`.
 *
 * Subject context is passed EXPLICITLY (not read from a thread-local) so the
 * cron + admin paths can write rows with `subject_id = NULL` (system events).
 * RLS policy on `audit_log` is NULL-tolerant: rows with `subject_id IS NULL`
 * remain visible regardless of the GUC value.
 *
 * Thread-safe — each call acquires a fresh pooled connection through
 * [DatabaseFactory.withSubject].
 */
class AuditLogWriter(private val db: DatabaseFactory) {
    /**
     * Insert a row into `audit_log`. All numeric / hash / extra fields are
     * optional. The writer NEVER references emotion-inferred state; callers
     * must not pass mood / shame / compulsion data in [extra].
     *
     * @param subjectId null for system-level events (cron, backup, audit
     *   prune). When non-null the writer runs the insert inside that subject's
     *   RLS context so the row passes the policy check.
     * @param kind one of [AuditLogActions].
     */
    fun write(
        subjectId: UUID?,
        kind: String,
        model: String? = null,
        promptHash: String? = null,
        responseHash: String? = null,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        costCents: Int? = null,
        requestId: String? = null,
        extra: JsonObject? = null,
    ) {
        val sql =
            """
            INSERT INTO audit_log
              (subject_id, kind, model, prompt_hash, response_hash,
               input_tokens, output_tokens, cost_cents, request_id, extra)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """.trimIndent()

        val run: (java.sql.Connection) -> Unit = { conn ->
            conn.prepareStatement(sql).use { ps ->
                if (subjectId != null) ps.setObject(1, subjectId) else ps.setNull(1, Types.OTHER)
                ps.setString(2, kind)
                setNullableString(ps, 3, model)
                setNullableString(ps, 4, promptHash)
                setNullableString(ps, 5, responseHash)
                setNullableInt(ps, 6, inputTokens)
                setNullableInt(ps, 7, outputTokens)
                setNullableInt(ps, 8, costCents)
                setNullableString(ps, 9, requestId)
                if (extra != null) {
                    val pg = PGobject().apply {
                        type = "jsonb"
                        value = JSON.encodeToString(JsonObject.serializer(), extra)
                    }
                    ps.setObject(10, pg)
                } else {
                    ps.setNull(10, Types.OTHER)
                }
                ps.executeUpdate()
            }
        }

        if (subjectId != null) {
            db.withSubject(subjectId, run)
        } else {
            db.withSystemContext(run)
        }
        // Increment AFTER the write commits so a roll-back doesn't inflate
        // the gauge. (Hikari throws on rollback failures so the increment
        // line is skipped on the exception path.)
        Counters.auditLogWritesTotal.increment()
    }

    private fun setNullableString(ps: java.sql.PreparedStatement, idx: Int, v: String?) {
        if (v != null) ps.setString(idx, v) else ps.setNull(idx, Types.VARCHAR)
    }

    private fun setNullableInt(ps: java.sql.PreparedStatement, idx: Int, v: Int?) {
        if (v != null) ps.setInt(idx, v) else ps.setNull(idx, Types.INTEGER)
    }

    companion object {
        private val JSON = Json { encodeDefaults = true }
    }
}
