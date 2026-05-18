package com.dietician.server.middleware

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory sliding-window-ish rate limiter keyed by `(scope, key)`
 * where `scope` is a route name (e.g. `magic-link-request`) and `key` is a
 * caller identifier (subject id, email address, or remote IP).
 *
 * V1 first-ship: single-node in-memory. Restart = limits reset. Council
 * 1779120000 RC9 baseline rates:
 *   - default scope: 30 req/min per subject (RC9 anti-misbehaving-phone).
 *   - magic-link request: 5 req/hour per email (anti-spam).
 *
 * Plan-3.5 swaps the impl for a Postgres / Redis backing store when the
 * cohort grows beyond single-node.
 *
 * Tracking model: a per-key fixed window. Each `permit` call slides the
 * window forward when the current window is exhausted. Coarser than true
 * sliding-window but adequate for V1 caps + matches the council-approved
 * "60-per-minute / 5-per-hour" coarse semantics.
 */
class RateLimiter(
    private val clock: Clock = Clock.systemUTC(),
) {
    private data class WindowState(
        val windowStart: Instant,
        val count: Int,
    )

    private val state = ConcurrentHashMap<String, WindowState>()

    /**
     * Attempts to take one permit. Returns true if allowed, false if the
     * current window is exhausted. Atomic via `compute`.
     */
    fun permit(scope: String, key: String, limit: Int, window: Duration): Boolean {
        require(limit > 0) { "limit must be positive" }
        require(!window.isZero && !window.isNegative) { "window must be positive" }
        val now = clock.instant()
        val composite = "$scope::$key"
        var allowed = false
        state.compute(composite) { _, prev ->
            val windowOpen = prev != null && prev.windowStart.plus(window).isAfter(now)
            if (!windowOpen) {
                allowed = true
                WindowState(windowStart = now, count = 1)
            } else if (prev!!.count < limit) {
                allowed = true
                prev.copy(count = prev.count + 1)
            } else {
                allowed = false
                prev
            }
        }
        return allowed
    }

    /** Test/debug — peeks the current count for a (scope, key) tuple. */
    fun currentCount(scope: String, key: String): Int =
        state["$scope::$key"]?.count ?: 0

    /** Test/debug — clears all state (does not affect concurrent callers). */
    fun resetAll() {
        state.clear()
    }

    companion object {
        // Council 1779120000 RC9 + spec §5.5 baseline limits.
        val DEFAULT_LIMIT: Int = 30
        val DEFAULT_WINDOW: Duration = Duration.ofMinutes(1)
        val MAGIC_LINK_LIMIT: Int = 5
        val MAGIC_LINK_WINDOW: Duration = Duration.ofHours(1)
    }
}
