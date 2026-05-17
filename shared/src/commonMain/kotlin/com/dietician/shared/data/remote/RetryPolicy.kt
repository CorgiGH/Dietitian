package com.dietician.shared.data.remote

import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exponential backoff with jitter for outbox drain + pull-trigger retries.
 * base = 2^attempt seconds clamped at 30s. jitter ∈ [0, 25% of base].
 */
object RetryPolicy {
    private val CAP: Duration = 30.seconds

    fun nextDelay(attempt: Int, rand: Random = Random.Default): Duration {
        val baseSeconds = (1L shl min(attempt, 16)).coerceAtMost(30L)
        val base = baseSeconds.seconds
        val capped = if (base > CAP) CAP else base
        val jitterMs = rand.nextLong(0L, capped.inWholeMilliseconds / 4 + 1)
        return capped + jitterMs.milliseconds
    }
}
