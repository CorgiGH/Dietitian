package com.dietician.shared.llm

import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.llm.net.CoachHttpClient
import org.slf4j.LoggerFactory

/**
 * iter-11 — replay-on-startup for outbox rows that didn't receive a `/coach/commit`
 * ACK before desktop shut down. Re-POSTs commit with status='orphaned' so the
 * server-side audit row flips out of 'pending' and the budget reservation is
 * released. Server is idempotent on commit so duplicate calls are safe.
 */
class DesktopOutboxReplay(
    private val db: DieticianDatabase,
    private val http: CoachHttpClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun replayPending() {
        val rows = db.`0009_audit_pending_outboxQueries`.findUncommitted().executeAsList()
        if (rows.isEmpty()) return
        log.info("DesktopOutboxReplay: {} pending row(s) to reconcile", rows.size)
        rows.forEach { row ->
            runCatching {
                http.commit(
                    idempotencyKey = row.idempotency_key,
                    status = "orphaned",
                    promptTokens = 0,
                    completionTokens = 0,
                    costCents = 0,
                    provider = row.provider,
                    latencyMs = 0,
                    responseHash = "replay",
                )
                db.`0009_audit_pending_outboxQueries`.markCommitted(row.idempotency_key)
            }.onFailure { log.warn("replay failed for {}: {}", row.idempotency_key, it.message) }
        }
    }
}
