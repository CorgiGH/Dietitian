package com.dietician.shared.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Atomicity contract for [EventStore]:
 *  - On success: event row + outbox row + (pantry only) snapshot delta are all present.
 *  - On serialize failure inside the transaction: NONE of the three are present.
 *
 * Lives in `desktopTest` (NOT `commonTest`) because [JdbcSqliteDriver] is JVM-only.
 */
class EventStoreAtomicityTest {
    private fun newDb(): DieticianDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        return DieticianDatabase(driver)
    }

    @Test
    fun `enqueuePantryEvent writes event + outbox + snapshot in one transaction`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val ev =
            EventPayload.Pantry(
                eventUuid = "11111111-1111-1111-1111-111111111111",
                deviceId = "test-dev",
                originatedAtMs = 1_000L,
                skuUuid = "sku-1",
                deltaQty = 5.0,
                unit = "buc",
            )

        store.enqueuePantryEvent(ev)

        db.`0001_event_ledgerQueries`
            .selectPantryEvent(ev.eventUuid)
            .executeAsOne()
            .sku_uuid shouldBe "sku-1"

        db.`0002_outboxQueries`
            .selectOutboxRow(ev.eventUuid)
            .executeAsOne()
            .table_name shouldBe "pantry_events"

        db.`0003_pantry_snapshotQueries`
            .selectPantryCurrentBySku("sku-1")
            .executeAsOne()
            .qty shouldBe 5.0
    }

    @Test
    fun `failed serialize rolls back event and outbox`() {
        val db = newDb()
        // Default kotlinx-serialization Json rejects Double.NaN; the failing path is the default.
        val strictJson = Json { allowSpecialFloatingPointValues = false }
        val store = EventStore(db, strictJson)
        val badUuid = "22222222-2222-2222-2222-222222222222"

        runCatching {
            store.enqueuePantryEvent(
                EventPayload.Pantry(
                    eventUuid = badUuid,
                    deviceId = "test-dev",
                    originatedAtMs = 1L,
                    skuUuid = "sku-x",
                    // Double.NaN trips strict-mode JSON encoding.
                    deltaQty = Double.NaN,
                    unit = "g",
                ),
            )
        }

        db.`0001_event_ledgerQueries`
            .selectPantryEvent(badUuid)
            .executeAsOneOrNull() shouldBe null

        db.`0002_outboxQueries`
            .selectOutboxRow(badUuid)
            .executeAsOneOrNull() shouldBe null

        db.`0003_pantry_snapshotQueries`
            .selectPantryCurrentBySku("sku-x")
            .executeAsOneOrNull() shouldBe null
    }
}
