package com.dietician.shared.ui.data

import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.local.EventStore
import com.dietician.shared.data.local.PantrySnapshotStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * Plan-1-backed PantryReader + PantryWriter. Replaces [InMemoryPantryStore]
 * once the platform shell wires `DieticianDatabase` into the Koin graph.
 *
 * Read path: [PantrySnapshotStore.currentAll] → Flow<List<PantryCurrentRow>> →
 * mapped into [PantryItem]. The Compose UI calls `viewModel.load()` which
 * pulls the latest emission via `flowSnapshot().first()` — same shape as the
 * in-memory store.
 *
 * Write path: [EventStore.enqueuePantryEvent] writes a `pantry_events` row +
 * outbox row + snapshot delta atomically in one SQLDelight transaction. The
 * snapshot flow re-emits after the tx commits so the next `load()` pulls the
 * new state.
 *
 * **DisplayName preservation:** Plan-1 `pantry_snapshot` table doesn't carry
 * a display-name column (only sku_uuid + unit + qty). To round-trip the
 * user-typed name back into the UI we encode `displayName` into the SKU UUID
 * prefix: `"manual:Chicken breast"`. The adapter parses the prefix back when
 * mapping rows to [PantryItem]. Real metadata lookup (via
 * `pantry_metadata_lww` or a fresh `pantry_metadata` table) lands when
 * Plan-3 receipt OCR ships its OCR'd-display-name path.
 *
 * **Event UUID:** local-only generator (millis + random hex). Real Plan-1
 * usage outside this adapter uses Plan-1's HybridLogicalClock for ordering;
 * here we only need uniqueness, not orderability.
 */
class SqlDelightPantryStore(
    private val snapshot: PantrySnapshotStore,
    private val events: EventStore,
    private val deviceId: () -> String,
    private val nowMs: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) : PantryReader, PantryWriter {

    override fun flowSnapshot(): Flow<List<PantryItem>> =
        snapshot.currentAll().map { rows ->
            rows.map { row ->
                PantryItem(
                    skuUuid = row.skuUuid,
                    displayName = decodeDisplayName(row.skuUuid),
                    qty = row.qty,
                    unit = row.unit,
                )
            }
        }

    override fun addItem(item: PantryItem) {
        events.enqueuePantryEvent(
            EventPayload.Pantry(
                eventUuid = newEventUuid(),
                deviceId = deviceId(),
                originatedAtMs = nowMs(),
                skuUuid = item.skuUuid,
                deltaQty = item.qty,
                unit = item.unit,
                reason = "manual_add",
            ),
        )
    }

    override fun removeItem(sku: String, qty: Double, unit: String) {
        events.enqueuePantryEvent(
            EventPayload.Pantry(
                eventUuid = newEventUuid(),
                deviceId = deviceId(),
                originatedAtMs = nowMs(),
                skuUuid = sku,
                deltaQty = -qty,
                unit = unit,
                reason = "manual_remove",
            ),
        )
    }

    private fun newEventUuid(): String =
        "evt-${nowMs()}-${Random.nextLong().toString(16).removePrefix("-")}"

    private fun decodeDisplayName(skuUuid: String): String {
        val idx = skuUuid.indexOf(':')
        return if (idx in 1..skuUuid.length - 2) skuUuid.substring(idx + 1) else skuUuid
    }

    companion object {
        /** Helper for the UI: encode a typed display-name + freshness ts into the SKU. */
        fun encodeSku(displayName: String): String =
            "manual:${displayName.trim()}"
    }
}
