package com.dietician.shared.data.remote

import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {
    @Test
    fun `delay is bounded by 30s + 25 percent jitter at attempt 5`() {
        val cap = 30.seconds
        val maxJitter = 7500.milliseconds
        repeat(20) {
            val d = RetryPolicy.nextDelay(attempt = 5)
            d shouldBeGreaterThanOrEqualTo cap
            d shouldBeLessThanOrEqualTo (cap + maxJitter)
        }
    }

    @Test
    fun `low attempt yields short base`() {
        val d0 = RetryPolicy.nextDelay(attempt = 0)
        d0 shouldBeGreaterThanOrEqualTo 1.seconds
        d0 shouldBeLessThanOrEqualTo (1.seconds + 250.milliseconds)
    }
}
