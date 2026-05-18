package com.dietician.shared.data.sync

import com.dietician.shared.data.api.Cursor
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.test.Test

/** Simulated server: holds (originatedAt, uuid)-sorted rows; serves strictly `>` of cursor. */
class FakeServer {
    data class Row(val ts: Long, val uuid: String, val table: String, val payload: String)

    val rows = sortedSetOf<Row>(compareBy({ it.ts }, { it.uuid }))

    fun add(r: Row) {
        rows.add(r)
    }

    fun pullSince(
        cursor: Cursor,
        limit: Int,
    ): List<Row> =
        rows.asSequence()
            .filter { Cursor(it.ts, it.uuid) > cursor }
            .take(limit)
            .toList()
}

class PullCursorPropertyTest {
    @Test
    fun `repeated bounded pulls drop nothing and double-apply nothing`() {
        kotlinx.coroutines.runBlocking {
            checkAll(20, Arb.list(Arb.long(0L..1_000L), 1..100)) { stamps ->
                val server = FakeServer()
                stamps.forEach {
                    server.add(FakeServer.Row(it, UUID.randomUUID().toString(), "pantry_events", "{}"))
                }

                val seen = mutableSetOf<Pair<Long, String>>()
                var cursor = Cursor.ZERO
                // Mimic PullCoordinator drain: repeated bounded pulls until exhausted.
                while (true) {
                    val batch = server.pullSince(cursor, limit = 7)
                    if (batch.isEmpty()) break
                    batch.forEach { seen.add(it.ts to it.uuid) }
                    val last = batch.last()
                    cursor = Cursor(last.ts, last.uuid)
                }

                // No drop: every server row was seen exactly once.
                seen.size shouldBe server.rows.size
            }
        }
    }
}
