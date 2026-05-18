package com.dietician.shared.llm.provider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CircuitBreakerTest {
    @Test
    fun `starts closed`() {
        val cb = CircuitBreaker()
        cb.isOpen() shouldBe false
    }

    @Test
    fun `opens after 5 failures within window`() {
        val cb = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000)
        repeat(5) { cb.recordFailure() }
        cb.isOpen() shouldBe true
    }

    @Test
    fun `transitions to half-open after reset timeout then closes on success`() {
        var now = 0L
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1_000, clock = { now })
        repeat(3) { cb.recordFailure() }
        cb.isOpen() shouldBe true
        now = 1_001
        // half-open: isOpen() returns false (next call is a trial)
        cb.isOpen() shouldBe false
        cb.recordSuccess()
        cb.isOpen() shouldBe false
    }

    @Test
    fun `half-open trial failure reopens the breaker`() {
        var now = 0L
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1_000, clock = { now })
        repeat(3) { cb.recordFailure() }
        now = 1_001
        cb.isOpen() shouldBe false
        // single failure in half-open reopens
        cb.recordFailure()
        cb.isOpen() shouldBe true
    }

    @Test
    fun `successful call resets failure counter`() {
        val cb = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 1_000)
        repeat(2) { cb.recordFailure() }
        cb.recordSuccess()
        repeat(2) { cb.recordFailure() }
        // counter reset to 0 by success → 2 fails < threshold
        cb.isOpen() shouldBe false
    }
}
