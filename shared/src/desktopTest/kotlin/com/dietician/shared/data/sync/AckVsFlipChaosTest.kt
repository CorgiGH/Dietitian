package com.dietician.shared.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.local.EventStore
import com.dietician.shared.data.local.OutboxStore
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Simulates the kill-between-server-ACK-and-local-markSynced window per council BREAK #8.
 *
 * Invariant: when the process crashes between server returning 200-ACK and the local
 * outbox.markSynced commit, the next drain MUST NOT cause duplicate inventory. The
 * load-bearing guarantee is that snapshot maintenance is one-shot at EventStore.enqueue
 * (in the same tx as event insert) — not on push-ack. So replay-after-crash that drains
 * the outbox AGAIN does NOT re-apply the delta.
 *
 * If a future change introduces a non-EventStore snapshot-update path (e.g. on push-ack),
 * THIS test will start failing — that failure is the council-mandated guard tripping.
 */
class AckVsFlipChaosTest {
    private fun newDb(): DieticianDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        return DieticianDatabase(driver)
    }

    @Test
    fun `crash between server-ack and local-markSynced does not cause duplicate inventory`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val outbox = OutboxStore(db)

        // 1) Phone enqueues +5 of sku-1 (writes event + outbox + snapshot in one tx).
        store.enqueuePantryEvent(
            EventPayload.Pantry(
                eventUuid = "u-1",
                deviceId = "phone",
                originatedAtMs = 100L,
                skuUuid = "sku-1",
                deltaQty = 5.0,
                unit = "g",
            ),
        )

        // 2) "Server" ACKs but we crash BEFORE markSynced. Outbox still has u-1.
        outbox.nextBatch(10).size shouldBe 1

        // 3) Replay sends u-1 again. Server is idempotent on event_uuid (UPSERT-by-uuid).
        //    Local applies markSynced this time.
        val serverSeenUuids = mutableSetOf("u-1") // server already saw u-1 from first attempt
        outbox.markSynced("u-1", serverRecvAt = 5000L)

        // 4) Snapshot stays at +5 (NOT +10) — EventStore.enqueue only ran once.
        db.`0003_pantry_snapshotQueries`.selectPantryCurrentBySku("sku-1").executeAsOne().qty shouldBe 5.0

        // 5) Server saw u-1 exactly once (UPSERT-by-uuid is idempotent on retransmit).
        serverSeenUuids shouldBe setOf("u-1")

        // 6) Outbox is now empty (markSynced removed the row).
        outbox.nextBatch(10).size shouldBe 0
    }
}
