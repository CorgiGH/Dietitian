package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderId
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Desktop actual for ClaudeMax CLI — Plan-2 Tasks 14-18.
 *
 * RC2 (Council 1779062699) — CANONICAL companion-factory construction. Two entry points:
 *   - `forTesting(spawner, skipWarmUp)` — injects a fake spawner + optionally short-circuits
 *     warm-up so unit tests assert behavior without spawning real processes.
 *   - `production(config)` — builds the real [ProcessSpawnerImpl], warm-pool sized to
 *     `min(cores-2, 3)`, full 60s timeout, eager warm-up.
 *
 * Zero reflection, zero lateinit, zero secondary-constructor-swap. Primary constructor is
 * `internal` so only the companion factories construct outside of tests in the same module.
 *
 * Call flow:
 *   1. Check circuit-breaker — if OPEN, throw ProviderUnavailable.
 *   2. acquire() a warm proc from the pool (blocks if all in use).
 *   3. send() under withTimeout — on timeout/exception, markSick + recordFailure.
 *   4. On success → recordSuccess + release.
 */
actual class ClaudeMaxCliProvider internal constructor(
    @Suppress("UnusedPrivateProperty", "unused") private val spawner: ProcessSpawner,
    private val warmPool: ClaudeMaxWarmPool,
    private val circuitBreaker: CircuitBreaker,
    private val timeoutMs: Long,
) {
    actual suspend fun call(request: LlmRequest, model: String): LlmResponse {
        if (circuitBreaker.isOpen()) {
            throw LlmError.ProviderUnavailable(ProviderId("claudemax-cli"))
        }
        val proc = warmPool.acquire()
        var failed = false
        return try {
            val output = withTimeout(timeoutMs) { proc.send(request, model) }
            circuitBreaker.recordSuccess()
            output
        } catch (e: TimeoutCancellationException) {
            failed = true
            circuitBreaker.recordFailure()
            warmPool.markSick(proc)
            throw LlmError.Timeout("claudemax-cli")
        } catch (e: LlmError) {
            failed = true
            circuitBreaker.recordFailure()
            warmPool.markSick(proc)
            throw e
        } catch (e: Throwable) {
            failed = true
            circuitBreaker.recordFailure()
            warmPool.markSick(proc)
            throw LlmError.TransientFailure(e)
        } finally {
            if (!failed) {
                warmPool.release(proc)
            } else {
                // markSick already closed + decremented; we still need to release the semaphore permit.
                warmPool.release(proc)
            }
        }
    }

    actual fun isAvailable(): Boolean = warmPool.healthyCount() > 0 && !circuitBreaker.isOpen()

    companion object {
        /**
         * Test-only entry. Injects an arbitrary [ProcessSpawner] (typically a Fake that returns
         * canned SpawnedProcess values). `skipWarmUp=true` (default) keeps the pool empty —
         * the first call lazily spawns under the semaphore so tests can assert spawn counts
         * deterministically.
         */
        fun forTesting(
            spawner: ProcessSpawner,
            skipWarmUp: Boolean = true,
            poolSize: Int = 1,
            timeoutMs: Long = 5_000,
            circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000),
        ): ClaudeMaxCliProvider {
            val pool = ClaudeMaxWarmPool(
                spawner = spawner,
                size = poolSize,
                warmUpOnInit = !skipWarmUp,
            )
            return ClaudeMaxCliProvider(spawner, pool, circuitBreaker, timeoutMs)
        }

        /**
         * Production entry. Spawns the real `claude` binary at [ClaudeMaxConfig.binaryPath],
         * sizes the pool to `min(cores-2, 3)` (cap to prevent oversubscription on small
         * desktops; clamp to at least 1).
         */
        fun production(config: ClaudeMaxConfig = ClaudeMaxConfig()): ClaudeMaxCliProvider {
            val spawner = ProcessSpawnerImpl(config.binaryPath)
            val cores = Runtime.getRuntime().availableProcessors()
            val poolSize = minOf((cores - 2).coerceAtLeast(1), 3)
            val pool = ClaudeMaxWarmPool(
                spawner = spawner,
                size = poolSize,
                warmUpOnInit = true,
            )
            val cb = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000)
            return ClaudeMaxCliProvider(spawner, pool, cb, timeoutMs = 60_000)
        }
    }
}

/**
 * Production config — `binaryPath` defaults to `"claude"` so the launcher resolves via PATH.
 * Tests override to point at a fake binary or a wrapper script.
 */
data class ClaudeMaxConfig(val binaryPath: String = "claude")
