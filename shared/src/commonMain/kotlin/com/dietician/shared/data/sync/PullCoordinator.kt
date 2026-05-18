package com.dietician.shared.data.sync

import com.dietician.shared.data.WallClock
import com.dietician.shared.data.api.Cursor
import com.dietician.shared.data.api.PullRequest
import com.dietician.shared.data.api.PullResponse
import com.dietician.shared.data.deviceId
import com.dietician.shared.data.local.CacheMetaStore
import com.dietician.shared.data.local.SyncLogStore
import com.dietician.shared.data.remote.SyncClient
import com.dietician.shared.data.sql.DieticianDatabase
import kotlinx.coroutines.CancellationException

/**
 * Drives /sync/pull. Reads per-table cursors, batches a single pull, applies rows to local
 * caches inside a transaction, and advances cursors using max (originatedAt, eventUuid) of
 * the rows that successfully applied, per table.
 *
 * Council BREAK #3: cursor uses strict `>` (timestamp, eventUuid) half-open windowing. The
 * PullCursorPropertyTest property holds because the cursor is a totally-ordered tuple and
 * every batch's last applied row becomes the next cursor; no row can be served twice.
 *
 * Council #2 fix (cursor-advance guard): cursors are now advanced ONLY for rows that
 * applyPulledRow returned `true` for. Plan-1 ships applyPulledRow as an explicit no-op
 * returning `false`, so cursors will STALL until Plan-3 wires per-table UPSERT routing.
 * This is intentional: a stalled cursor causes the same rows to be re-fetched next pull
 * (idempotent), whereas advancing past unapplied rows silently discards them — the council's
 * "silent data loss" objection. Better stall than drop.
 */
class PullCoordinator(
    private val db: DieticianDatabase,
    private val client: SyncClient,
    private val cacheMeta: CacheMetaStore,
    private val syncLog: SyncLogStore,
    private val clock: WallClock,
) {
    private val tables =
        listOf(
            "pantry_events",
            "meal_events",
            "weight_events",
            "receipt_events",
            "pantry_metadata",
        )

    suspend fun pullOnce(triggerLogId: Long): PullResult {
        val start = clock.nowMillis()
        val cursors = tables.associateWith { cacheMeta.cursorFor(it) }
        val resp: PullResponse =
            try {
                client.pull(PullRequest(deviceId(), cursors))
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val end = clock.nowMillis()
                syncLog.recordPullCompleted(triggerLogId, start, end, 0, e.message ?: e::class.simpleName ?: "unknown")
                return PullResult.Failure(e)
            }

        db.transaction {
            // Track per-table "max applied" cursor so we only advance past rows we actually
            // committed to local caches. Rows where applyPulledRow returns false (the Plan-1
            // stub default) do NOT contribute to the cursor and will be re-pulled next tick.
            val maxAppliedPerTable = mutableMapOf<String, Cursor>()
            for (row in resp.rows) {
                val applied = applyPulledRow(row.tableName, row.eventUuid, row.payloadJson, row.serverRecvAt)
                if (applied) {
                    val rowCursor = Cursor(row.originatedAtMs, row.eventUuid)
                    val current = maxAppliedPerTable[row.tableName]
                    if (current == null || rowCursor > current) {
                        maxAppliedPerTable[row.tableName] = rowCursor
                    }
                }
            }
            maxAppliedPerTable.forEach { (table, cursor) ->
                cacheMeta.advanceCursor(table, cursor)
            }
        }

        val end = clock.nowMillis()
        syncLog.recordPullCompleted(triggerLogId, start, end, resp.rows.size, null)
        return PullResult.Success(resp.rows.size)
    }

    /**
     * Stub — Plan-3 wires per-table UPSERT routing (JSON -> typed DTO -> INSERT OR REPLACE
     * into the appropriate cache table). Returning `false` here means cursors freeze until
     * Plan-3 lands. This is intentional: better stall (re-pull next tick) than silent data
     * loss (advance cursor past rows we never wrote).
     *
     * @return `true` iff the row was successfully written to a local cache and the cursor
     *   may advance past it.
     */
    @Suppress("UnusedPrivateMember", "FunctionOnlyReturningConstant")
    private fun applyPulledRow(
        table: String,
        uuid: String,
        payload: String,
        serverRecvAt: Long,
    ): Boolean {
        // STUB per plan note "the only intentional stub in Plan-1". Per-table JSON -> DTO ->
        // INSERT OR REPLACE routing is wired in Plan-3; Plan-1's contract is the
        // cursor/transactional scaffold + property proof. Explicit `false` (vs the prior
        // implicit no-op + unconditional advance) guards the cursor against advancing past
        // rows we did not commit — council #2 BREAK.
        return false
    }

    sealed interface PullResult {
        data class Success(val count: Int) : PullResult

        data class Failure(val cause: Throwable) : PullResult
    }
}
