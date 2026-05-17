package com.dietician.shared.data.local

import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.sql.DieticianDatabase
import kotlinx.serialization.json.Json

/**
 * Writes domain events atomically into the local SQLite ledger.
 *
 * Each `enqueueXxxEvent` call performs all of the following inside a single SQLDelight
 * transaction (BEGIN…COMMIT) so the three concerns are committed-or-rolled-back together:
 *  1. Insert the canonical row into the corresponding event table (`pantry_events`,
 *     `meal_events`, `weight_events`, `receipt_events`).
 *  2. Enqueue the serialized JSON payload into `outbox` for the sync worker.
 *  3. (Pantry only) Apply the qty delta to `pantry_snapshot`. Task 6 dropped the original
 *     AFTER-INSERT trigger because (a) the SQLDelight sqlite_3_18 parser does not accept
 *     NEW/OLD bindings and (b) running the trigger alongside explicit application here would
 *     double-count. The trigger is replaced by the explicit `snapshotSeedIfAbsent` +
 *     `snapshotApplyDelta` calls below — kept inside the same tx for the same atomicity
 *     guarantee the trigger would have provided.
 *
 * Atomic-write contract / why serialization is inside `db.transaction { }`:
 * The JSON encoding step happens INSIDE the transaction. If `Json.encodeToString` throws
 * (e.g. strict-mode JSON refusing `Double.NaN`), SQLDelight rolls the transaction back, so
 * the event row + outbox row + snapshot delta are ALL absent on disk. Placing the encode
 * before `db.transaction { }` would also work for `enqueuePantryEvent` (no row written yet),
 * but the in-tx form encodes the invariant "either everything lands or nothing does" directly
 * in the code shape and matches the corresponding `failed serialize rolls back …` test
 * verbatim.
 *
 * Suspend modifier: deliberately omitted. SQLDelight's `db.transaction { … }` block is a
 * synchronous (non-suspending) lambda, so making the public surface `suspend` would imply a
 * suspension point that does not exist and would force callers to wrap each event-enqueue in
 * a coroutine for no reason.
 */
class EventStore(
    private val db: DieticianDatabase,
    private val json: Json,
) {
    fun enqueuePantryEvent(ev: EventPayload.Pantry) {
        db.transaction {
            val payloadJson = json.encodeToString(EventPayload.serializer(), ev)
            db.`0001_event_ledgerQueries`.insertPantryEvent(
                event_uuid = ev.eventUuid,
                device_id = ev.deviceId,
                originated_at = ev.originatedAtMs,
                sku_uuid = ev.skuUuid,
                delta_qty = ev.deltaQty,
                unit = ev.unit,
                reason = ev.reason,
                evidence_ref = ev.evidenceRef,
            )
            db.`0002_outboxQueries`.enqueueOutbox(
                event_uuid = ev.eventUuid,
                table_name = "pantry_events",
                payload_json = payloadJson,
                queued_at = ev.originatedAtMs,
            )
            // Snapshot delta in same tx — replaces the trigger dropped in Task 6.
            db.`0003_pantry_snapshotQueries`.snapshotSeedIfAbsent(
                sku_uuid = ev.skuUuid,
                unit = ev.unit,
            )
            db.`0003_pantry_snapshotQueries`.snapshotApplyDelta(
                deltaQty = ev.deltaQty,
                originatedAt = ev.originatedAtMs,
                skuUuid = ev.skuUuid,
                unit = ev.unit,
            )
        }
    }

    fun enqueueMealEvent(ev: EventPayload.Meal) {
        db.transaction {
            val payloadJson = json.encodeToString(EventPayload.serializer(), ev)
            db.`0001_event_ledgerQueries`.insertMealEvent(
                event_uuid = ev.eventUuid,
                device_id = ev.deviceId,
                originated_at = ev.originatedAtMs,
                meal_label = ev.mealLabel,
                recipe_id = ev.recipeId,
                ingredients_json = ev.ingredientsJson,
                kcal_actual = ev.kcalActual,
                protein_actual = ev.proteinActual,
                rating_1_5 = ev.rating1to5?.toLong(),
                notes = ev.notes,
            )
            db.`0002_outboxQueries`.enqueueOutbox(
                event_uuid = ev.eventUuid,
                table_name = "meal_events",
                payload_json = payloadJson,
                queued_at = ev.originatedAtMs,
            )
        }
    }

    fun enqueueWeightEvent(ev: EventPayload.Weight) {
        db.transaction {
            val payloadJson = json.encodeToString(EventPayload.serializer(), ev)
            db.`0001_event_ledgerQueries`.insertWeightEvent(
                event_uuid = ev.eventUuid,
                device_id = ev.deviceId,
                originated_at = ev.originatedAtMs,
                weight_kg = ev.weightKg,
                time_of_day = ev.timeOfDay,
                conditions = ev.conditions,
            )
            db.`0002_outboxQueries`.enqueueOutbox(
                event_uuid = ev.eventUuid,
                table_name = "weight_events",
                payload_json = payloadJson,
                queued_at = ev.originatedAtMs,
            )
        }
    }

    fun enqueueReceiptEvent(ev: EventPayload.Receipt) {
        db.transaction {
            val payloadJson = json.encodeToString(EventPayload.serializer(), ev)
            db.`0001_event_ledgerQueries`.insertReceiptEvent(
                event_uuid = ev.eventUuid,
                device_id = ev.deviceId,
                originated_at = ev.originatedAtMs,
                store_id = ev.storeId,
                total_lei = ev.totalLei,
                image_ref = ev.imageRef,
                ocr_status = ev.ocrStatus,
                ocr_provider = ev.ocrProvider,
                line_items_json = ev.lineItemsJson,
            )
            db.`0002_outboxQueries`.enqueueOutbox(
                event_uuid = ev.eventUuid,
                table_name = "receipt_events",
                payload_json = payloadJson,
                queued_at = ev.originatedAtMs,
            )
        }
    }
}
