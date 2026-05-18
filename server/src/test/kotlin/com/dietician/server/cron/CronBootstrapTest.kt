package com.dietician.server.cron

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [CronBootstrap]. Verify:
 *   - Scheduled jobs run on the configured cadence.
 *   - Failures bump the failed-counter and don't kill the loop.
 *   - `nextFires` reports a future epoch second for each registered job.
 *   - `shutdown` cancels the loops.
 */
class CronBootstrapTest {
    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `scheduled job fires repeatedly`() = runBlocking {
        val counter = AtomicInteger(0)
        val cron = CronBootstrap(scope(), Clock.systemUTC(), SimpleMeterRegistry())
        cron.schedule(
            name = "tick",
            fireAt = { now -> now.plusNanos(50_000_000) }, // 50 ms
        ) {
            counter.incrementAndGet()
        }
        try {
            withTimeout(2_000) {
                while (counter.get() < 3) delay(20)
            }
        } finally {
            cron.shutdown()
        }
        assertTrue(counter.get() >= 3, "expected ≥3 ticks, got ${counter.get()}")
    }

    @Test
    fun `failure in job is caught and loop continues`() = runBlocking {
        val attempts = AtomicInteger(0)
        val cron = CronBootstrap(scope(), Clock.systemUTC(), SimpleMeterRegistry())
        cron.schedule(
            name = "boom",
            fireAt = { now -> now.plusNanos(40_000_000) },
        ) {
            val n = attempts.incrementAndGet()
            if (n == 1) throw RuntimeException("simulated failure")
        }
        try {
            withTimeout(2_000) {
                while (attempts.get() < 2) delay(20)
            }
        } finally {
            cron.shutdown()
        }
        assertTrue(attempts.get() >= 2, "loop must survive exception; got ${attempts.get()}")
    }

    @Test
    fun `nextFires reports a positive epoch second after scheduling`() = runBlocking {
        val cron = CronBootstrap(scope(), Clock.systemUTC(), SimpleMeterRegistry())
        cron.schedule(
            name = "tomorrow",
            fireAt = { now -> now.nextDayAtTime(4, 0) },
        ) { /* no-op */ }
        // Let the coroutine reach the gauge-set line.
        delay(100)
        val next = cron.nextFires()["tomorrow"]
        cron.shutdown()
        assertTrue(next != null && next > 0, "next-fire epoch must be positive")
    }

    @Test
    fun `nextDayAtTime returns same-day candidate when still in the future`() {
        val now = ZonedDateTime.parse("2026-05-18T03:00:00Z")
        val next = now.nextDayAtTime(4, 0)
        assertEquals(4, next.hour)
        assertEquals(0, next.minute)
        assertEquals(now.dayOfMonth, next.dayOfMonth)
    }

    @Test
    fun `nextDayAtTime rolls to next day when candidate already passed`() {
        val now = ZonedDateTime.parse("2026-05-18T05:00:00Z")
        val next = now.nextDayAtTime(4, 0)
        assertEquals(now.dayOfMonth + 1, next.dayOfMonth)
    }
}
