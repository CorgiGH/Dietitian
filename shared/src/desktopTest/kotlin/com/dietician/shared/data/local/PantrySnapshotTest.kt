package com.dietician.shared.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Correctness contract for [PantrySnapshotStore]:
 *  - Accumulated `enqueuePantryEvent` deltas project to the expected per-SKU qty.
 *  - SKUs whose aggregate falls to zero/negative are omitted from `currentAll` reads
 *    (filtered by the underlying `selectPantryCurrentAll` `WHERE qty > 0` clause).
 *
 * Lives in `desktopTest` because [JdbcSqliteDriver] + [java.util.UUID] are JVM-only.
 * Not using `runTest` because [EventStore.enqueuePantryEvent] is non-suspend (Task 14
 * errata — SQLDelight transactions are synchronous lambdas).
 */
class PantrySnapshotTest {
    private fun newDb(): DieticianDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        return DieticianDatabase(driver)
    }

    @Test
    fun `snapshot reflects accumulated deltas`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val snap = PantrySnapshotStore(db)

        store.enqueuePantryEvent(p("sku-1", 10.0, 1_000L))
        store.enqueuePantryEvent(p("sku-1", -3.0, 2_000L))
        store.enqueuePantryEvent(p("sku-2", 5.0, 3_000L))

        val all = snap.currentAllOnce()
        all.first { it.skuUuid == "sku-1" }.qty shouldBe 7.0
        all.first { it.skuUuid == "sku-2" }.qty shouldBe 5.0
    }

    @Test
    fun `snapshot omits zero-or-negative aggregate`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val snap = PantrySnapshotStore(db)

        store.enqueuePantryEvent(p("sku-1", 5.0, 1L))
        store.enqueuePantryEvent(p("sku-1", -5.0, 2L))

        snap.currentAllOnce().any { it.skuUuid == "sku-1" } shouldBe false
    }

    private fun p(
        sku: String,
        qty: Double,
        t: Long,
    ) = EventPayload.Pantry(
        eventUuid = java.util.UUID.randomUUID().toString(),
        deviceId = "test",
        originatedAtMs = t,
        skuUuid = sku,
        deltaQty = qty,
        unit = "buc",
    )
}
