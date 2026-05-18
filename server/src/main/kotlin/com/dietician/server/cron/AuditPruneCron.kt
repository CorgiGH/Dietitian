package com.dietician.server.cron

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.db.DatabaseFactory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Nightly audit-log retention prune — Plan-3 Task 33.
 *
 * Deletes rows from `audit_log` older than 12 months. The retention horizon
 * is mandated by:
 *   - GDPR Art 5(1)(e) — storage limitation (audit data is not "needed
 *     forever").
 *   - AI Act Art 12 — the surface that must be retained for traceability
 *     ("during the period that they are in operation") sets the floor, not
 *     the ceiling. 12mo > all known incident-investigation windows for an
 *     n=1 personal system.
 *
 * Emits one [AuditLogActions.AUDIT_PRUNE_COMPLETED] row per run with
 * `deleted_rows` in `extra`. The very prune emission survives — it's the
 * marker `/health` reads as `audit_log_last_pruned_at`.
 */
class AuditPruneCron(
    private val db: DatabaseFactory,
    private val auditLog: AuditLogWriter,
) {
    private val log = LoggerFactory.getLogger(AuditPruneCron::class.java)

    /**
     * Performs the DELETE. Returns the deleted-row count for telemetry/test
     * hooks; the caller (in CronBootstrap) typically discards the value.
     */
    fun run(): Int {
        // SECURITY DEFINER function (V021) — bypasses audit_log RLS so the
        // DELETE sees every subject's stale rows, not just NULL-subject
        // rows. App role has EXECUTE grant.
        val deleted = db.withSystemContext { conn ->
            conn.prepareStatement(
                "SELECT prune_audit_log_older_than_12mo()",
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
        log.info("audit-prune: deleted {} rows older than 12 months", deleted)
        auditLog.write(
            subjectId = null,
            kind = AuditLogActions.AUDIT_PRUNE_COMPLETED,
            extra = JsonObject(mapOf("deleted_rows" to JsonPrimitive(deleted))),
        )
        return deleted
    }
}
