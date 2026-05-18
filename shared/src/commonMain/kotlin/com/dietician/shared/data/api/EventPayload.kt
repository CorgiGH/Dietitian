package com.dietician.shared.data.api

import kotlinx.serialization.Serializable

/**
 * Polymorphic envelope for events that are inserted into the local ledger and queued for sync.
 *
 * The four variants mirror the four event tables (`pantry_events`, `meal_events`, `weight_events`,
 * `receipt_events`). All variants share the cross-table coordinates: a globally-unique
 * [eventUuid] (assigned at write time and used as the idempotency key), the [deviceId] that
 * originated the write, and the originator-local [originatedAtMs] timestamp (HLC.wallMs).
 *
 * Stored on-disk as the JSON column `outbox.payload_json`. Encoded with the polymorphic-aware
 * [EventPayload.serializer], so the encoded form carries a `type` discriminator distinguishing
 * the four variants when the sync client posts to `POST /events`.
 */
@Serializable
sealed interface EventPayload {
    val eventUuid: String
    val deviceId: String
    val originatedAtMs: Long

    @Serializable
    data class Pantry(
        override val eventUuid: String,
        override val deviceId: String,
        override val originatedAtMs: Long,
        val skuUuid: String,
        val deltaQty: Double,
        val unit: String,
        val reason: String? = null,
        val evidenceRef: String? = null,
    ) : EventPayload

    @Serializable
    data class Meal(
        override val eventUuid: String,
        override val deviceId: String,
        override val originatedAtMs: Long,
        val mealLabel: String,
        val recipeId: String? = null,
        val ingredientsJson: String,
        val kcalActual: Double? = null,
        val proteinActual: Double? = null,
        val rating1to5: Int? = null,
        val notes: String? = null,
    ) : EventPayload

    @Serializable
    data class Weight(
        override val eventUuid: String,
        override val deviceId: String,
        override val originatedAtMs: Long,
        val weightKg: Double,
        val timeOfDay: String? = null,
        val conditions: String? = null,
    ) : EventPayload

    @Serializable
    data class Receipt(
        override val eventUuid: String,
        override val deviceId: String,
        override val originatedAtMs: Long,
        val storeId: String,
        val totalLei: Double? = null,
        val imageRef: String,
        val ocrStatus: String,
        val ocrProvider: String? = null,
        val lineItemsJson: String? = null,
    ) : EventPayload
}
