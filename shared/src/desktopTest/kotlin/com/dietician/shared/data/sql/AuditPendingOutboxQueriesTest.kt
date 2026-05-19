package com.dietician.shared.data.sql

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuditPendingOutboxQueriesTest {
    private val driver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            DieticianDatabase.Schema.create(it)
        }
    private val db = DieticianDatabase(driver)

    @Test
    fun `insertOutboxRow stores idempotency key + prompt hash`() {
        val key = "11111111-1111-1111-1111-111111111111"
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            idempotency_key = key,
            reservation_id = null,
            prompt_hash = "abc",
            started_at_ms = 1_780_000_000_000L,
            last_attempt_at_ms = 1_780_000_000_000L,
            attempts = 0L,
            provider = "claudemax",
        )
        val row = db.`0009_audit_pending_outboxQueries`.findByKey(key).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("claudemax", row.provider)
        assertEquals("abc", row.prompt_hash)
    }

    @Test
    fun `markCommitted deletes the row`() {
        val key = "22222222-2222-2222-2222-222222222222"
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            key,
            null,
            "abc",
            0L,
            0L,
            0L,
            "claudemax",
        )
        db.`0009_audit_pending_outboxQueries`.markCommitted(key)
        assertNull(db.`0009_audit_pending_outboxQueries`.findByKey(key).executeAsOneOrNull())
    }

    @Test
    fun `findUncommitted returns rows ordered oldest-first`() {
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            "k1",
            null,
            "a",
            100L,
            100L,
            0L,
            "claudemax",
        )
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            "k2",
            null,
            "b",
            50L,
            50L,
            0L,
            "claudemax",
        )
        val rows = db.`0009_audit_pending_outboxQueries`.findUncommitted().executeAsList()
        assertEquals(listOf("k2", "k1"), rows.map { it.idempotency_key })
    }

    @Test
    fun `bumpAttempt increments attempts + updates last_attempt_at_ms`() {
        val key = "33333333-3333-3333-3333-333333333333"
        db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
            key,
            null,
            "h",
            100L,
            100L,
            0L,
            "claudemax",
        )
        db.`0009_audit_pending_outboxQueries`.bumpAttempt(last_attempt_at_ms = 500L, idempotency_key = key)
        val row = db.`0009_audit_pending_outboxQueries`.findByKey(key).executeAsOne()
        assertEquals(1L, row.attempts)
        assertEquals(500L, row.last_attempt_at_ms)
    }
}
