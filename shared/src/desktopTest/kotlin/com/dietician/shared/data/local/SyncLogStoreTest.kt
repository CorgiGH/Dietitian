package com.dietician.shared.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.api.Cursor
import com.dietician.shared.data.sql.DieticianDatabase
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SyncLogStoreTest {
    private fun newDb(): DieticianDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DieticianDatabase.Schema.create(driver)
        return DieticianDatabase(driver)
    }

    @Test
    fun `records each trigger plus pull boundaries`() {
        val db = newDb()
        val log = SyncLogStore(db)
        val id = log.recordTrigger(source = "ws", firedAtMs = 100L)
        log.recordDebounced(id, debouncedToMs = 150L)
        log.recordPullCompleted(id, pullStartedAt = 200L, pullEndedAt = 250L, eventsPulled = 5, error = null)
        val recent = log.recent(10)
        recent.first().trigger_source shouldBe "ws"
        recent.first().events_pulled shouldBe 5L
        recent.first().debounced_to shouldBe 150L
    }

    @Test
    fun `cursor round-trip per table`() {
        val db = newDb()
        val meta = CacheMetaStore(db)
        meta.cursorFor("pantry_events") shouldBe Cursor.ZERO
        meta.advanceCursor("pantry_events", Cursor(timestampMs = 500L, eventUuid = "u-99"))
        meta.cursorFor("pantry_events") shouldBe Cursor(500L, "u-99")
    }
}
