package com.dietician.shared.data.compaction

import com.dietician.shared.data.sql.DieticianDatabase

/**
 * Periodically advances the pantry_snapshot_checkpoint so the ledger can be safely
 * truncated (or summarized) past the checkpoint without losing snapshot correctness.
 *
 * The materialized pantry_snapshot is maintained by EventStore.enqueuePantryEvent
 * inside the same tx as the event insert (Tasks 6 + 14). Compaction here is a
 * checkpoint-advancement only — folding events older than the checkpoint into the
 * snapshot is implicit (the snapshot already reflects them via EventStore).
 *
 * Plan-deviation note: plan body had a half-written rebuildFromScratch() with a
 * TODO comment ("deferred to Plan-7's data-migration tooling"). Stripped here;
 * Plan-7 owns full rebuild semantics.
 */
class PantryCompactor(private val db: DieticianDatabase) {

    fun compact() = db.transaction {
        val events = db.`0003_pantry_snapshotQueries`.selectEventsAfterCheckpoint().executeAsList()
        if (events.isEmpty()) return@transaction
        val maxTs = events.maxOf { it.originated_at }
        db.`0003_pantry_snapshotQueries`.advanceCheckpoint(maxTs)
    }
}
