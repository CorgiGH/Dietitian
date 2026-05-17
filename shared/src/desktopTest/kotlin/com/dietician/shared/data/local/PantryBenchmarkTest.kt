package com.dietician.shared.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.longs.shouldBeLessThan
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlin.test.Test

/**
 * Council BREAK #1 acceptance: `currentAll` must remain bounded read-cost regardless of
 * event-ledger length. With 100k pantry events distributed across 200 SKUs, a one-shot
 * snapshot read must complete in <10ms because the projection is materialized into
 * `pantry_snapshot` (~200 rows) at write time — NOT folded from the event ledger at
 * read time.
 *
 * A warm-up read is performed before the timed read so JIT compilation + JDBC query-plan
 * cache reflect a long-lived app process, not a cold first-touch. Standard benchmarking
 * practice; documented in the Task 15 commit body.
 */
class PantryBenchmarkTest {
    @Test
    fun `currentAll under 10ms with 100k events across 200 SKUs`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        val db = DieticianDatabase(driver)
        val store = EventStore(db, Json)
        val snap = PantrySnapshotStore(db)

        val skus = (0 until 200).map { "sku-$it" }
        val rand = java.util.Random(42)
        repeat(100_000) { i ->
            store.enqueuePantryEvent(
                EventPayload.Pantry(
                    eventUuid = UUID.randomUUID().toString(),
                    deviceId = "bench",
                    originatedAtMs = i.toLong(),
                    skuUuid = skus[rand.nextInt(skus.size)],
                    deltaQty = if (rand.nextBoolean()) rand.nextDouble() * 5 else -(rand.nextDouble() * 2),
                    unit = "buc",
                ),
            )
        }

        // Warm-up read (JIT + JDBC query plan cache).
        snap.currentAllOnce()
        // Measured read. Recorded value at Task 15 (DELL-G5, JDK 17): ~4 ms.
        val ms = measureTimeMillis { snap.currentAllOnce() }
        ms.toLong() shouldBeLessThan 10L
    }
}
