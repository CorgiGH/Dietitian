package com.dietician.shared.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dietician.shared.data.sql.DieticianDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * In-memory view-model row for the pantry "current" projection. Mirrors the SQLDelight
 * `Pantry_snapshot` table-row class but stays in shared/api namespace so callers don't have
 * to depend on generated SQL types.
 */
data class PantryCurrentRow(
    val skuUuid: String,
    val unit: String,
    val qty: Double,
    val lastEventAt: Long,
)

/**
 * Materialized read of pantry "current" quantities — closes council BREAK #1 (event-folding on
 * every read would be O(events) and would degrade as the ledger grows). All snapshot rows are
 * served from `pantry_snapshot`, populated and maintained out-of-band by
 * [EventStore.enqueuePantryEvent] which performs `snapshotSeedIfAbsent` +
 * `snapshotApplyDelta` inside the SAME SQLDelight transaction as the event-row + outbox-row
 * insert. That gives the snapshot all-or-nothing atomicity against the event ledger and lets
 * reads here be O(distinct SKUs) instead of O(events).
 *
 * Compaction (Task 16, [PantryCompactor]) folds older events into
 * `pantry_snapshot_checkpoint` to bound storage growth; the `pantry_snapshot` rows
 * themselves remain the authoritative read for "current pantry".
 *
 * The store is non-suspend by design — SQLDelight queries are synchronous against the JDBC
 * driver, so a `suspend` surface here would imply a suspension point that does not exist.
 * For reactive UIs use [currentAll] (Flow); for one-shot reads (sync, benchmarks, tests)
 * use [currentAllOnce] / [currentForSku].
 */
class PantrySnapshotStore(private val db: DieticianDatabase) {
    /**
     * Reactive view of every SKU with positive on-hand quantity, ordered most-recently-touched
     * first. Re-emits when `pantry_snapshot` is mutated (i.e. on every event-store write).
     */
    fun currentAll(): Flow<List<PantryCurrentRow>> =
        db.`0003_pantry_snapshotQueries`.selectPantryCurrentAll()
            .asFlow().mapToList(Dispatchers.Default).map { rows ->
                rows.map {
                    PantryCurrentRow(
                        skuUuid = it.sku_uuid,
                        unit = it.unit,
                        qty = it.qty,
                        lastEventAt = it.last_event_at,
                    )
                }
            }

    /**
     * One-shot snapshot read. Used by sync workers, benchmarks, and tests where Flow
     * subscription is overkill.
     */
    fun currentAllOnce(): List<PantryCurrentRow> =
        db.`0003_pantry_snapshotQueries`.selectPantryCurrentAll().executeAsList().map {
            PantryCurrentRow(
                skuUuid = it.sku_uuid,
                unit = it.unit,
                qty = it.qty,
                lastEventAt = it.last_event_at,
            )
        }

    /**
     * One-shot read for a single SKU. Returns `null` when the SKU has never been seeded OR
     * when its aggregate quantity is zero/negative (filtered by the underlying query).
     */
    fun currentForSku(skuUuid: String): PantryCurrentRow? =
        db.`0003_pantry_snapshotQueries`.selectPantryCurrentBySku(skuUuid).executeAsOneOrNull()?.let {
            PantryCurrentRow(
                skuUuid = it.sku_uuid,
                unit = it.unit,
                qty = it.qty,
                lastEventAt = it.last_event_at,
            )
        }
}
