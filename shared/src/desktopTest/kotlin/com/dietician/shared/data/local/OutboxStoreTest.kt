package com.dietician.shared.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.EventPayload
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Plan-1 Task 19 — TDD spec for [OutboxStore].
 *
 * Lives in `desktopTest` (NOT `commonTest`) because [JdbcSqliteDriver] is JVM-only.
 */
class OutboxStoreTest {
    private fun newDb(): DieticianDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        return DieticianDatabase(driver)
    }

    private fun pantry(
        uuid: String,
        t: Long,
    ) = EventPayload.Pantry(
        eventUuid = uuid,
        deviceId = "d",
        originatedAtMs = t,
        skuUuid = "s",
        deltaQty = 1.0,
        unit = "g",
    )

    @Test
    fun `nextBatch returns rows ordered by queued_at ascending`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val outbox = OutboxStore(db)

        store.enqueuePantryEvent(pantry("u-1", 5L))
        store.enqueuePantryEvent(pantry("u-2", 3L))

        val batch = outbox.nextBatch(10)
        batch.map { it.event_uuid } shouldBe listOf("u-2", "u-1")
    }

    @Test
    fun `markSynced removes row from outbox and stamps event synced_at`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val outbox = OutboxStore(db)

        store.enqueuePantryEvent(pantry("u-1", 5L))
        outbox.markSynced("u-1", serverRecvAt = 1234L)

        outbox.nextBatch(10).size shouldBe 0
        db.`0001_event_ledgerQueries`
            .selectPantryEvent("u-1")
            .executeAsOne()
            .synced_at shouldBe 1234L
    }

    @Test
    fun `recordFailure increments attempts and stores last_error`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val outbox = OutboxStore(db)

        store.enqueuePantryEvent(pantry("u-1", 5L))
        outbox.recordFailure("u-1", "boom")
        outbox.recordFailure("u-1", "still boom")

        val row = db.`0002_outboxQueries`.selectOutboxRow("u-1").executeAsOne()
        row.attempts shouldBe 2L
        row.last_error shouldBe "still boom"
    }

    @Test
    fun `promoteIfDead at attempt 10 moves to outbox_dead`() {
        val db = newDb()
        val store = EventStore(db, Json)
        val outbox = OutboxStore(db)

        store.enqueuePantryEvent(pantry("u-1", 5L))
        repeat(10) { outbox.recordFailure("u-1", "fail #$it") }

        outbox.promoteIfDead("u-1", nowMs = 9999L) shouldBe true

        db.`0002_outboxQueries`.selectOutboxRow("u-1").executeAsOneOrNull() shouldBe null
        val dead = outbox.deadLetters().first()
        dead.event_uuid shouldBe "u-1"
        dead.attempt_count shouldBe 10L
    }
}
