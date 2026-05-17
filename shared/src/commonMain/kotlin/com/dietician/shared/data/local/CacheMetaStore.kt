package com.dietician.shared.data.local

import com.dietician.shared.data.api.Cursor
import com.dietician.shared.data.sql.DieticianDatabase

/**
 * Per-table pull cursor storage. cursorFor returns Cursor.ZERO for unknown tables.
 * advanceCursor uses portable seed-if-absent + UPDATE (SQLite UPSERT requires >= 3.24
 * but android-min=26 ships 3.18).
 */
class CacheMetaStore(private val db: DieticianDatabase) {
    private val q get() = db.`0005_cache_metaQueries`

    fun cursorFor(tableName: String): Cursor {
        val row = q.cursorFor(tableName).executeAsOneOrNull() ?: return Cursor.ZERO
        return Cursor(row.last_ts, row.last_event_uuid)
    }

    fun advanceCursor(
        tableName: String,
        c: Cursor,
    ) = db.transaction {
        q.cursorSeedIfAbsent(tableName)
        q.cursorAdvance(c.timestampMs, c.eventUuid, tableName)
    }
}
