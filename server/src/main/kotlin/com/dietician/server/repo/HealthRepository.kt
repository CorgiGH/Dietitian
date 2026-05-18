package com.dietician.server.repo

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.db.DatabaseFactory
import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET /health` — Plan-3 Task 37 + Council 1779120000 RC13.
 *
 * Snake-case JSON because the original RC13 contract was authored alongside
 * the systemd-fallback runbook in snake_case shell parlance; the Plan-3 doc
 * preserves it.
 *
 * Fields:
 *   - `status` — "ok" while the aggregate query succeeds.
 *   - `tombstone_grace_stale_count` — tombstone_events rows older than 7
 *     days. >0 = a redact-orchestration job was lost; runbook reference in
 *     `docs/runbooks/restart.md` covers retry.
 *   - `audit_log_last_pruned_at` — ISO timestamp of last AUDIT_PRUNE_COMPLETED
 *     row, or null if the prune cron has never fired (first 24h after first
 *     deploy).
 *   - `embedding_provider_version` — most recent
 *     `corpus_embeddings.embedding_provider_version` value; null = no
 *     embeddings indexed yet.
 *   - `last_backup_at` — ISO timestamp of last BACKUP_COMPLETED, or null on
 *     a fresh install.
 *   - `queue_depths` — open work in `paper_fetch_queue` / `pii_review_queue`.
 *   - `db_pool_active` — placeholder; Hikari MicrometerMetrics covers the
 *     real gauge. -1 = "see Prometheus, not /health".
 */
@Serializable
data class HealthResponse(
    val status: String,
    val tombstone_grace_stale_count: Int,
    val audit_log_last_pruned_at: String?,
    val embedding_provider_version: String?,
    val last_backup_at: String?,
    val queue_depths: Map<String, Int>,
    val db_pool_active: Int,
)

/**
 * Single-aggregate query repository for `GET /health`. The choice to do it
 * in one [DatabaseFactory.withSystemContext] is deliberate — `/health` is
 * polled by Tailnet monitoring + the desktop client, so one round-trip
 * matters.
 */
class HealthRepository(private val db: DatabaseFactory) {
    /**
     * Builds the [HealthResponse] aggregate. Each query is wrapped in its
     * own statement (no JOIN) because the source tables are heterogeneous
     * and the result set is tiny.
     */
    fun aggregate(): HealthResponse = db.withSystemContext { conn ->
        val tombstoneStale = conn.createStatement().executeQuery(
            "SELECT count(*) FROM tombstone_events WHERE redacted_at < NOW() - INTERVAL '7 days'",
        ).use { rs -> rs.next(); rs.getInt(1) }
        val lastPrune = conn.prepareStatement(
            "SELECT MAX(occurred_at) FROM audit_log WHERE kind = ?",
        ).use { ps ->
            ps.setString(1, AuditLogActions.AUDIT_PRUNE_COMPLETED)
            ps.executeQuery().use { rs -> rs.next(); rs.getTimestamp(1)?.toInstant()?.toString() }
        }
        val lastBackup = conn.prepareStatement(
            "SELECT MAX(occurred_at) FROM audit_log WHERE kind = ?",
        ).use { ps ->
            ps.setString(1, AuditLogActions.BACKUP_COMPLETED)
            ps.executeQuery().use { rs -> rs.next(); rs.getTimestamp(1)?.toInstant()?.toString() }
        }
        val embedVer = conn.createStatement().executeQuery(
            "SELECT MAX(embedding_provider_version) FROM corpus_embeddings",
        ).use { rs -> rs.next(); rs.getString(1) }
        val paperQ = conn.createStatement().executeQuery(
            "SELECT count(*) FROM paper_fetch_queue WHERE status = 'queued'",
        ).use { rs -> rs.next(); rs.getInt(1) }
        val piiQ = conn.createStatement().executeQuery(
            "SELECT count(*) FROM pii_review_queue WHERE reviewed_at IS NULL",
        ).use { rs -> rs.next(); rs.getInt(1) }

        HealthResponse(
            status = "ok",
            tombstone_grace_stale_count = tombstoneStale,
            audit_log_last_pruned_at = lastPrune,
            embedding_provider_version = embedVer,
            last_backup_at = lastBackup,
            queue_depths = mapOf(
                "paper_fetch_queue" to paperQ,
                "pii_review_queue" to piiQ,
            ),
            // Hikari/Micrometer gauge owns the real number — surface a marker
            // so the field is present (RC13 wants the key) without colliding
            // with the per-pool Micrometer name.
            db_pool_active = -1,
        )
    }
}
