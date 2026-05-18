package com.dietician.shared.ui.data

import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.local.EventStore
import com.dietician.shared.data.local.PantrySnapshotStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UI-layer pantry projection. Wraps Plan-1's [PantrySnapshotStore] (read) and
 * [EventStore.enqueuePantryEvent] (write) into a Compose-friendly shape.
 *
 * The :shared:data ledger is single-subject per device (Plan-1 design: SQLite is
 * the per-device local store; subject_id is implicit because the device only
 * runs one signed-in session at a time). [SessionStore.currentSubjectId] therefore
 * does NOT need to be plumbed into PantrySnapshotStore queries — the RLS-equivalent
 * happens at sync-push time when the outbox row is decorated with the server-side
 * subject_id by the auth-bound /sync/push handler (Plan-3 Task 14).
 *
 * Per-item display metadata (expiry date, "open" status, display name) is not in
 * the V003 pantry_snapshot table today — it's tracked via pantry_metadata (Plan-1
 * V003 separate table, populated by Plan-3 /receipts/upload OCR line items). For
 * Batch C first-ship we surface only the snapshot row fields + a placeholder
 * display name = skuUuid; expiry + open will be wired when Plan-3 receipts ship.
 */
interface PantryReader {
    fun flowSnapshot(): Flow<List<PantryItem>>
}

interface PantryWriter {
    fun addItem(item: PantryItem)
    fun removeItem(sku: String, qty: Double, unit: String)
}

/**
 * UI-layer Pantry item. Cosmetic columns (displayName, expiresAtMs, open) come
 * from pantry_metadata + receipt OCR; quantity + unit come from pantry_snapshot.
 *
 * [expiresAtMs] null = no known expiry (not "expired now"). [open] = the package
 * has been opened and a tighter use-by window applies (Plan-1 schema field).
 */
data class PantryItem(
    val skuUuid: String,
    val displayName: String,
    val qty: Double,
    val unit: String,
    val expiresAtMs: Long? = null,
    val open: Boolean = false,
)

/**
 * Concrete adapter. Reads from Plan-1 [PantrySnapshotStore]; writes through
 * Plan-1 [EventStore]. The reader returns [PantryItem] with display fields set
 * to defaults pending Plan-3 metadata-table wiring (Batch D/E task).
 */
class PantryRepository(
    private val snapshots: PantrySnapshotStore,
    private val events: EventStore,
    private val deviceIdProvider: () -> String,
    private val eventUuidGen: () -> String,
    private val clockNowMs: () -> Long,
) : PantryReader, PantryWriter {

    override fun flowSnapshot(): Flow<List<PantryItem>> =
        snapshots.currentAll().map { rows ->
            rows.map { row ->
                PantryItem(
                    skuUuid = row.skuUuid,
                    displayName = row.skuUuid, // Plan-3 metadata wiring TODO Batch E.
                    qty = row.qty,
                    unit = row.unit,
                    expiresAtMs = null, // Plan-3 metadata wiring TODO Batch E.
                    open = false,
                )
            }
        }

    override fun addItem(item: PantryItem) {
        events.enqueuePantryEvent(
            EventPayload.Pantry(
                eventUuid = eventUuidGen(),
                deviceId = deviceIdProvider(),
                originatedAtMs = clockNowMs(),
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
                eventUuid = eventUuidGen(),
                deviceId = deviceIdProvider(),
                originatedAtMs = clockNowMs(),
                skuUuid = sku,
                deltaQty = -qty, // consume = negative delta
                unit = unit,
                reason = "consume",
            ),
        )
    }
}

