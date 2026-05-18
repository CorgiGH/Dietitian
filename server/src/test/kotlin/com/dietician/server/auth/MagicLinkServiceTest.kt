package com.dietician.server.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagicLinkServiceTest {
    private class FixedClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
    }

    @Test
    fun `issue then verifyAndConsume returns the email and removes the token`() {
        val svc = MagicLinkService()
        val issued = svc.issue("Victor@Example.com  ")
        // Email is normalized to lowercase + trimmed.
        assertEquals("victor@example.com", issued.email)
        // First verify succeeds.
        val verified = svc.verifyAndConsume(issued.plaintextToken)
        assertEquals("victor@example.com", verified)
        // Second verify with the SAME token returns null (single-use).
        assertNull(svc.verifyAndConsume(issued.plaintextToken))
    }

    @Test
    fun `expired token returns null`() {
        val clock = FixedClock(Instant.parse("2026-05-18T12:00:00Z"))
        val svc = MagicLinkService(ttl = Duration.ofMinutes(15), clock = clock)
        val issued = svc.issue("alice@example.com")
        clock.now = clock.now.plus(Duration.ofMinutes(30))
        assertNull(svc.verifyAndConsume(issued.plaintextToken), "expired token must not verify")
    }

    @Test
    fun `unknown token returns null`() {
        val svc = MagicLinkService()
        assertNull(svc.verifyAndConsume("garbage-token-not-issued"))
    }

    @Test
    fun `plaintext token is high-entropy URL-safe random`() {
        val svc = MagicLinkService()
        val a = svc.issue("a@e.com")
        val b = svc.issue("b@e.com")
        assertNotEquals(a.plaintextToken, b.plaintextToken)
        assertEquals(43, a.plaintextToken.length, "32 bytes URL-safe no-pad b64 = 43 chars")
        assertTrue(a.plaintextToken.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `tokenHash is not equal to plaintext token`() {
        val svc = MagicLinkService()
        val issued = svc.issue("victor@example.com")
        assertNotNull(issued.tokenHashHex)
        assertNotEquals(
            issued.plaintextToken,
            issued.tokenHashHex,
            "stored hash must differ from plaintext token",
        )
    }
}
