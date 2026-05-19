package com.dietician.server.cron

import com.dietician.server.db.DatabaseFactory
import org.slf4j.LoggerFactory

/**
 * iter-11 — saga compensation for the 2-phase commit Coach pipeline.
 *
 * Calls refund_orphaned(60) every 30s via [CronBootstrap]. The PG fn (V022)
 * flips audit_log rows where status='pending' AND reserved_until < now() to
 * 'orphaned' and decrements llm_budget.cost_cents_used by the reserved
 * cost_cents. Returns the number of rows compensated.
 *
 * The PG fn ships `SECURITY DEFINER` (T1 fix) so RLS doesn't filter rows out
 * under withSystemContext — the cron runs without a subject GUC and still
 * sees + compensates orphaned reservations across all subjects.
 *
 * Operator visibility: log line "coach: orphaned N pending rows" at INFO
 * when N > 0. Silent when N = 0 to avoid log spam.
 */
class CoachOrphanCleanupCron(private val db: DatabaseFactory) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun runOnce(staleSeconds: Int = STALE_SECONDS_DEFAULT): Int =
        db.withSystemContext { c ->
            c.prepareStatement("SELECT refund_orphaned(?)").apply {
                setInt(1, staleSeconds)
            }.executeQuery().use { rs ->
                rs.next()
                val n = rs.getInt(1)
                if (n > 0) log.info("coach: orphaned {} pending rows (saga compensation)", n)
                n
            }
        }

    private companion object {
        const val STALE_SECONDS_DEFAULT = 60
    }
}
