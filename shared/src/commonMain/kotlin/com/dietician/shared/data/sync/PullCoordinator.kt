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

/**
 * Drives /sync/pull. Reads per-table cursors, batches a single pull, applies rows to local
 * caches inside a transaction, advances cursors using max (originatedAt, eventUuid) per table.
 *
 * Council BREAK #3: cursor uses strict `>` (timestamp, eventUuid) half-open windowing. The
 * PullCursorPropertyTest property holds because the cursor is a totally-ordered tuple and
 * every batch's last row becomes the next cursor; no row can be served twice.
 *
 * applyPulledRow is intentionally STUBBED for Plan-1. The plan's own File Structure note
 * marks this as an open stub: per-table JSON->DTO->insert-or-ignore routing is hand-written
 * here in real-impl; Plan-7 may codegen it. Plan-1's deliverable is the cursor/transactional
 * scaffold + property proof; the row-application body is wired in a subsequent plan.
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
                val end = clock.nowMillis()
                syncLog.recordPullCompleted(triggerLogId, start, end, 0, e.message ?: e::class.simpleName ?: "unknown")
                return PullResult.Failure(e)
            }

        db.transaction {
            for (row in resp.rows) {
                applyPulledRow(row.tableName, row.eventUuid, row.payloadJson, row.serverRecvAt)
            }
            // Advance cursor per table using max (ts, uuid) per table seen.
            resp.rows.groupBy { it.tableName }.forEach { (table, rows) ->
                val last = rows.maxByOrNull { Cursor(it.originatedAtMs, it.eventUuid) }!!
                cacheMeta.advanceCursor(table, Cursor(last.originatedAtMs, last.eventUuid))
            }
        }

        val end = clock.nowMillis()
        syncLog.recordPullCompleted(triggerLogId, start, end, resp.rows.size, null)
        return PullResult.Success(resp.rows.size)
    }

    private fun applyPulledRow(
        table: String,
        uuid: String,
        payload: String,
        serverRecvAt: Long,
    ) {
        // STUB per plan note "the only intentional stub in Plan-1". Per-table JSON -> DTO ->
        // insert-or-ignore routing is wired in a subsequent plan; Plan-1's contract is the
        // cursor/transactional scaffold + property proof above.
    }

    sealed interface PullResult {
        data class Success(val count: Int) : PullResult

        data class Failure(val cause: Throwable) : PullResult
    }
}
