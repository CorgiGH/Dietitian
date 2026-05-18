package com.dietician.shared.llm.provider

import kotlinx.datetime.Clock

/**
 * Subprocess circuit-breaker — Plan-2 Task 16.
 *
 * State machine:
 *   - CLOSED → on `failureThreshold` failures within an unspecified window → OPEN
 *   - OPEN → block all calls for `resetTimeoutMs` then → HALF_OPEN on first probe
 *   - HALF_OPEN → next call is a trial; success → CLOSED, failure → OPEN (reset counter)
 *
 * Note: we intentionally do NOT use Resilience4j's `CircuitBreaker` here because we want
 * deterministic state transitions for the warm-pool stress test (Task 18) without depending on
 * timed-window roll-up. Plan-2 Batch C HTTP providers DO use Resilience4j for HTTP retries.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    @Volatile
    private var state: State = State.CLOSED

    @Volatile
    private var failures: Int = 0

    @Volatile
    private var openedAtMs: Long = 0L

    @Synchronized
    fun isOpen(): Boolean {
        if (state == State.OPEN && clock() - openedAtMs >= resetTimeoutMs) {
            state = State.HALF_OPEN
            return false
        }
        return state == State.OPEN
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        state = State.CLOSED
    }

    @Synchronized
    fun recordFailure() {
        failures += 1
        if (state == State.HALF_OPEN || failures >= failureThreshold) {
            state = State.OPEN
            openedAtMs = clock()
        }
    }

    /** Test-only inspection — production code should treat the breaker as opaque. */
    @Synchronized
    internal fun currentState(): State = state

    internal enum class State { CLOSED, OPEN, HALF_OPEN }
}
