package com.dietician.server.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStoreTest {
    private class FixedClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
    }

    @Test
    fun `create + get round-trip`() {
        val store = SessionStore(ttl = Duration.ofHours(1))
        val subject = UUID.randomUUID()
        val s = store.create(subject)
        assertEquals(subject, s.subjectId)
        val read = store.get(s.sessionId)
        assertNotNull(read)
        assertEquals(s.sessionId, read.sessionId)
    }

    @Test
    fun `expired session is evicted on get`() {
        val clock = FixedClock(Instant.parse("2026-05-18T12:00:00Z"))
        val store = SessionStore(ttl = Duration.ofMinutes(5), clock = clock)
        val s = store.create(UUID.randomUUID())
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        assertNull(store.get(s.sessionId), "expired session should be evicted")
    }

    @Test
    fun `invalidate removes session`() {
        val store = SessionStore()
        val s = store.create(UUID.randomUUID())
        assertTrue(store.invalidate(s.sessionId))
        assertNull(store.get(s.sessionId))
    }

    @Test
    fun `invalidateAllFor kills every session for the subject`() {
        val store = SessionStore()
        val victor = UUID.randomUUID()
        val other = UUID.randomUUID()
        val v1 = store.create(victor)
        val v2 = store.create(victor)
        val o1 = store.create(other)
        val n = store.invalidateAllFor(victor)
        assertEquals(2, n)
        assertNull(store.get(v1.sessionId))
        assertNull(store.get(v2.sessionId))
        assertNotNull(store.get(o1.sessionId), "other subject's session must survive")
    }

    @Test
    fun `sessionId is URL-safe random of expected length`() {
        val store = SessionStore()
        val s1 = store.create(UUID.randomUUID())
        val s2 = store.create(UUID.randomUUID())
        assertNotEquals(s1.sessionId, s2.sessionId, "session ids must differ")
        // 32-byte URL-safe-no-pad base64 → 43 chars.
        assertEquals(43, s1.sessionId.length)
        assertTrue(s1.sessionId.matches(Regex("^[A-Za-z0-9_-]+$")), "URL-safe charset only")
    }
}
