package com.dietician.shared.data.compaction

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.local.EventStore
import com.dietician.shared.data.local.PantrySnapshotStore
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test

class PantryCompactorTest {
    @Test
    fun `compact replays from checkpoint and snapshot remains correct`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        val db = DieticianDatabase(driver)
        val store = EventStore(db, Json)
        val snap = PantrySnapshotStore(db)
        val compactor = PantryCompactor(db)

        repeat(50) { i ->
            store.enqueuePantryEvent(EventPayload.Pantry(
                eventUuid = UUID.randomUUID().toString(),
                deviceId = "test", originatedAtMs = i.toLong(),
                skuUuid = "sku-1", deltaQty = 1.0, unit = "buc",
            ))
        }
        val before = snap.currentForSku("sku-1")!!.qty

        compactor.compact()

        snap.currentForSku("sku-1")!!.qty shouldBe before
        db.`0003_pantry_snapshotQueries`.selectCheckpoint().executeAsOne() shouldBe 49L
    }
}
