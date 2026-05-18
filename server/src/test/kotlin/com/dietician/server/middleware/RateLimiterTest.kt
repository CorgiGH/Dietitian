package com.dietician.server.middleware

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the in-memory [RateLimiter]. Covers:
 *   - permit up to limit, deny once limit hit.
 *   - window rollover restores permits.
 *   - per-scope + per-key isolation.
 *   - default vs magic-link tier defaults match council 1779120000 RC9.
 */
class RateLimiterTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
        fun advance(d: Duration) { instant = instant.plus(d) }
    }

    @Test
    fun `permits up to limit then denies`() {
        val rl = RateLimiter(MutableClock(Instant.parse("2026-01-01T00:00:00Z")))
        repeat(3) { i ->
            assertTrue(rl.permit("scope", "k", limit = 3, window = Duration.ofMinutes(1)),
                "permit #$i within limit must succeed")
        }
        assertFalse(rl.permit("scope", "k", limit = 3, window = Duration.ofMinutes(1)),
            "4th call must be denied once 3-of-3 are used in-window")
    }

    @Test
    fun `window rollover restores permits`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val rl = RateLimiter(clock)
        repeat(2) { assertTrue(rl.permit("scope", "k", 2, Duration.ofMinutes(1))) }
        assertFalse(rl.permit("scope", "k", 2, Duration.ofMinutes(1)))
        // Advance past the window boundary.
        clock.advance(Duration.ofMinutes(1).plusSeconds(1))
        assertTrue(rl.permit("scope", "k", 2, Duration.ofMinutes(1)),
            "after window expiry, new permits must be issued")
    }

    @Test
    fun `per-key isolation — limit on Alice doesn't bleed onto Bob`() {
        val rl = RateLimiter(MutableClock(Instant.parse("2026-01-01T00:00:00Z")))
        repeat(2) { assertTrue(rl.permit("scope", "alice", 2, Duration.ofMinutes(1))) }
        assertFalse(rl.permit("scope", "alice", 2, Duration.ofMinutes(1)))
        // Bob has a fresh budget — Alice's limit is local to her key.
        assertTrue(rl.permit("scope", "bob", 2, Duration.ofMinutes(1)),
            "Bob's permit must be allowed even though Alice is throttled")
    }

    @Test
    fun `per-scope isolation — magic-link tier separate from default`() {
        val rl = RateLimiter(MutableClock(Instant.parse("2026-01-01T00:00:00Z")))
        repeat(2) { assertTrue(rl.permit("default", "victor", 2, Duration.ofMinutes(1))) }
        assertFalse(rl.permit("default", "victor", 2, Duration.ofMinutes(1)))
        // Magic-link is its own scope — separate window/budget.
        assertTrue(rl.permit("magic-link-request", "victor", 5, Duration.ofHours(1)),
            "different scope must have its own bucket")
    }

    @Test
    fun `currentCount surfaces in-window usage`() {
        val rl = RateLimiter(MutableClock(Instant.parse("2026-01-01T00:00:00Z")))
        assertEquals(0, rl.currentCount("scope", "k"))
        rl.permit("scope", "k", 5, Duration.ofMinutes(1))
        rl.permit("scope", "k", 5, Duration.ofMinutes(1))
        assertEquals(2, rl.currentCount("scope", "k"))
    }

    @Test
    fun `RC9 default limits match council baseline 30 per minute, magic-link 5 per hour`() {
        assertEquals(30, RateLimiter.DEFAULT_LIMIT)
        assertEquals(Duration.ofMinutes(1), RateLimiter.DEFAULT_WINDOW)
        assertEquals(5, RateLimiter.MAGIC_LINK_LIMIT)
        assertEquals(Duration.ofHours(1), RateLimiter.MAGIC_LINK_WINDOW)
    }
}
