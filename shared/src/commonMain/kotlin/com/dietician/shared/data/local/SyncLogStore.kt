package com.dietician.shared.data.local

import com.dietician.shared.data.sql.DieticianDatabase

/**
 * Records every sync-trigger fire + its pull boundaries (council BREAK #7).
 * /diag reads from here. Unreported entries are mirrored to VPS via /sync/push.
 */
class SyncLogStore(private val db: DieticianDatabase) {
    private val q get() = db.`0005_cache_metaQueries`

    fun recordTrigger(source: String, firedAtMs: Long): Long = db.transactionWithResult {
        q.insertSyncLog(source, firedAtMs)
        q.selectLastSyncLogId().executeAsOne()
    }

    fun recordDebounced(id: Long, debouncedToMs: Long) =
        q.updateSyncLogDebounced(debouncedToMs, id)

    fun recordPullCompleted(id: Long, pullStartedAt: Long, pullEndedAt: Long, eventsPulled: Int, error: String?) =
        q.updateSyncLogPullBoundaries(pullStartedAt, pullEndedAt, eventsPulled.toLong(), error, id)

    fun recent(n: Int) = q.selectRecentSyncLog(n.toLong()).executeAsList()
    fun unreported() = q.selectUnreportedSyncLog().executeAsList()
    fun markReported(id: Long, reportedAtMs: Long) = q.markSyncLogReported(reportedAtMs, id)
}
