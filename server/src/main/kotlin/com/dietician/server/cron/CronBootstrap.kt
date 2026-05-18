package com.dietician.server.cron

import com.dietician.server.observability.Counters
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-JVM cron scheduler — Plan-3 Task 33 + Council 1779120000 RC4.
 *
 * Replaces systemd `.service` + `.timer` units so that:
 *
 *   1. Crons emit `AuditAction.AUDIT_PRUNE_COMPLETED` / `BACKUP_COMPLETED`
 *      via [com.dietician.server.audit.AuditLogWriter] — AI Act Art 12
 *      compliance survives a pg_dump or audit prune.
 *   2. Restart resilience: every scheduled job recomputes its next-fire
 *      time from `now()` on JVM start. No missed fires after a backend
 *      redeploy.
 *   3. Operators inspect `journalctl -u dietician-backend` for cron status,
 *      not `systemctl list-timers`.
 *
 * Each registered job runs in its own coroutine launched on [scope]. The
 * job loops `next = fireAt(now); delay(next - now); work()` until cancelled.
 *
 * Operator visibility:
 *   - `dietician.cron.next_fire_epoch_seconds{job}` gauge — next fire time
 *   - `dietician.cron.completed.total{job}` counter — successful runs
 *   - `dietician.cron.failed.total{job}` counter — exception-raising runs
 *
 * Failure semantics: an exception in [work] is logged + recorded on the
 * failure counter; the coroutine continues so a transient pg_dump glitch
 * doesn't permanently stop the scheduler.
 *
 * Disable knob: env `DIETICIAN_DISABLE_INJVM_CRONS=true` makes the
 * Application bootstrap skip registration (see runbook
 * `docs/runbooks/cron-systemd-fallback.md`).
 */
class CronBootstrap(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val registry: MeterRegistry? = null,
) {
    private val log = LoggerFactory.getLogger(CronBootstrap::class.java)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val nextFires = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Registers [work] under [name], firing at `fireAt(now)` and rescheduling
     * after each run. The receiver of [fireAt] is the current wall-clock time
     * in the configured [clock]'s zone — `nextDayAtTime(4, 0)` etc work as
     * expected.
     */
    fun schedule(
        name: String,
        fireAt: (ZonedDateTime) -> ZonedDateTime,
        work: suspend () -> Unit,
    ) {
        val nextHolder = nextFires.computeIfAbsent(name) { AtomicLong(0) }
        registry?.let { reg ->
            Gauge.builder("dietician.cron.next_fire_epoch_seconds", nextHolder) { it.get().toDouble() }
                .tags(Tags.of("job", name))
                .register(reg)
        }
        val job = scope.launch {
            while (isActive) {
                val now = ZonedDateTime.now(clock)
                val next = fireAt(now)
                val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0L)
                nextHolder.set(next.toEpochSecond())
                log.info("Cron '{}' next fires at {} (delay {} ms)", name, next, delayMs)
                delay(delayMs)
                try {
                    work()
                    Counters.cronCompletedTotal(name).increment()
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // A bad cron must NOT kill the loop. Failure is recorded
                    // on the failure counter + journalctl; next-fire reschedules.
                    log.error("Cron '{}' failed", name, e)
                    Counters.cronFailedTotal(name).increment()
                }
            }
        }
        jobs[name] = job
    }

    /**
     * Returns the next-fire epoch seconds map keyed by job name. Used by
     * `/diag` (Victor-only) to surface the current schedule timetable.
     */
    fun nextFires(): Map<String, Long> = nextFires.mapValues { it.value.get() }

    /** Cancels every registered job. Tests + shutdown hooks call this. */
    fun shutdown() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}

/**
 * Returns a [ZonedDateTime] at the same date with [hour]:[minute]:00 if that
 * moment is still in the future, otherwise the same time tomorrow. Used by
 * Plan-3 daily crons (audit prune at 04:00, backup at 04:30).
 */
fun ZonedDateTime.nextDayAtTime(hour: Int, minute: Int): ZonedDateTime {
    val candidate = this.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    return if (candidate.isAfter(this)) candidate else candidate.plusDays(1)
}
