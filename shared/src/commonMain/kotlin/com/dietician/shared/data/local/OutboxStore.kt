package com.dietician.shared.data.local

import com.dietician.shared.data.sql.DieticianDatabase

/**
 * OutboxStore — drains the outbox, records sync failures, and promotes incurable rows to
 * `outbox_dead`. Backs Plan-1 Task 19.
 *
 * ## Dead-letter contract (council BREAK #6 — "NEVER silent-drop")
 *
 * The outbox has bounded retry. After [maxAttempts] failures the row is moved (not deleted)
 * into `outbox_dead` together with the full payload, the failure history (`last_error`,
 * `attempt_count`), and timestamps. The dead-letter row survives until a human resolves it via
 * `/diag` — at which point [markDeadLetterResolved] flips `resolved_at`. The unreported-dead
 * queue ([unreportedDeadLetters]) is consumed by the diagnostics reporter so the user is
 * always told about silent sync corpses.
 *
 * ## Suspend modifier
 *
 * Deliberately omitted — SQLDelight `db.transaction { … }` and `db.transactionWithResult { … }`
 * are synchronous blocks. Adding `suspend` would imply a suspension point that does not exist.
 * Matches the same decision made on [EventStore].
 */
class OutboxStore(private val db: DieticianDatabase) {
    private val outbox get() = db.`0002_outboxQueries`
    private val ledger get() = db.`0001_event_ledgerQueries`

    /** Returns the next [limit] queued outbox rows ordered by `queued_at ASC, event_uuid ASC`. */
    fun nextBatch(limit: Int) = outbox.selectOutboxBatch(limit.toLong()).executeAsList()

    /**
     * Marks the underlying event row as `synced_at = serverRecvAt` and removes the outbox row.
     * No-op if the outbox row has already been drained (idempotent re-ack from the server).
     */
    fun markSynced(
        eventUuid: String,
        serverRecvAt: Long,
    ) {
        db.transaction {
            val row = outbox.selectOutboxRow(eventUuid).executeAsOneOrNull() ?: return@transaction
            when (row.table_name) {
                "pantry_events" -> ledger.markPantryEventSynced(serverRecvAt, eventUuid)
                "meal_events" -> ledger.markMealEventSynced(serverRecvAt, eventUuid)
                "weight_events" -> ledger.markWeightEventSynced(serverRecvAt, eventUuid)
                "receipt_events" -> ledger.markReceiptEventSynced(serverRecvAt, eventUuid)
            }
            outbox.deleteOutboxRow(eventUuid)
        }
    }

    /** Increments `attempts` and records [error] as the last failure message. */
    fun recordFailure(
        eventUuid: String,
        error: String,
    ) {
        outbox.recordOutboxFailure(error, eventUuid)
    }

    /**
     * Promotes the row to `outbox_dead` if it has reached [maxAttempts] attempts.
     *
     * Council BREAK #6: we NEVER silently drop a payload. After promotion the row lives in
     * `outbox_dead` with the full payload + error history for manual replay via `/diag`.
     *
     * @return `true` iff a row was promoted.
     */
    fun promoteIfDead(
        eventUuid: String,
        nowMs: Long,
        maxAttempts: Int = 10,
    ): Boolean =
        db.transactionWithResult {
            val row =
                outbox.selectOutboxRow(eventUuid).executeAsOneOrNull()
                    ?: return@transactionWithResult false
            if (row.attempts < maxAttempts.toLong()) return@transactionWithResult false
            outbox.promoteToDeadLetter(nowMs, eventUuid)
            outbox.deleteFromOutboxAfterDeadLetter(eventUuid)
            true
        }

    /** Unresolved dead-letter rows, newest failure first. */
    fun deadLetters() = outbox.selectDeadLetters().executeAsList()

    /** Operator marks a dead-letter row resolved (via `/diag` manual replay). */
    fun markDeadLetterResolved(
        uuid: String,
        resolvedAt: Long,
    ) = outbox.markDeadLetterResolved(resolvedAt, uuid)

    /** Dead-letter rows that have not yet been reported to the diagnostics reporter. */
    fun unreportedDeadLetters() = outbox.selectUnreportedDeadLetters().executeAsList()

    /** Stamps `reported_at` so the diagnostics reporter does not double-report. */
    fun markDeadLetterReported(
        uuid: String,
        reportedAt: Long,
    ) = outbox.markDeadLetterReported(reportedAt, uuid)
}
