package com.dietician.shared.llm.provider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Warm-pool of `claude` subprocesses — Plan-2 Task 17.
 *
 * Maintains up to [size] healthy [SpawnedProcess] instances. On [acquire] returns the
 * least-recently-used healthy one (deque pop-first); on [release] returns it to the tail. On
 * [markSick] evicts + spawns a replacement asynchronously so the pool refills without
 * blocking the call path.
 *
 * Stress invariant (RC11 — Plan-2 Task 18 `WarmPoolDequeStressTest`): N=64 concurrent
 * acquire/release pairs never exceed [size] total spawns. Enforced by [spawnCount] +
 * [poolSemaphore] gating.
 *
 * Concurrency: [poolSemaphore] blocks acquire when N callers exceed [size]; [stateMutex]
 * guards the deque + sick-eviction transitions.
 */
class ClaudeMaxWarmPool(
    private val spawner: ProcessSpawner,
    val size: Int,
    val warmUpOnInit: Boolean,
    private val refillScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val available = ConcurrentLinkedDeque<SpawnedProcess>()
    private val stateMutex = Mutex()
    private val poolSemaphore = Semaphore(permits = size.coerceAtLeast(1))
    private val spawnCount = AtomicInteger(0)

    init {
        if (warmUpOnInit && size > 0) {
            repeat(size) {
                val proc = spawnOne()
                available.addLast(proc)
            }
        }
    }

    /**
     * Acquire a warm process, spawning lazily if the pool was started cold (`warmUpOnInit=false`).
     * Blocks (via semaphore) when [size] callers are already holding a process.
     */
    suspend fun acquire(): SpawnedProcess {
        poolSemaphore.acquire()
        try {
            stateMutex.withLock {
                while (true) {
                    val proc = available.pollFirst() ?: break
                    if (proc.isAlive) return proc
                    // Dead in-pool — drop reference + try next.
                    proc.close()
                    spawnCount.decrementAndGet()
                }
            }
            // Pool empty or all dead — spawn under the held permit (the permit caps total
            // outstanding to `size` so spawn-on-demand still respects the cap).
            return spawnOne()
        } catch (e: Throwable) {
            poolSemaphore.release()
            throw e
        }
    }

    suspend fun release(proc: SpawnedProcess) {
        try {
            if (proc.isAlive) {
                stateMutex.withLock { available.addLast(proc) }
            } else {
                proc.close()
                spawnCount.decrementAndGet()
                refillAsync()
            }
        } finally {
            poolSemaphore.release()
        }
    }

    /**
     * Mark a process as sick — close it + spawn replacement async. Caller MUST still call
     * [release] afterward (the typical pattern: `markSick(p); release(p)`). markSick is
     * idempotent on a closed process (close() short-circuits via isAlive in impl).
     */
    suspend fun markSick(proc: SpawnedProcess) {
        proc.close()
        spawnCount.decrementAndGet()
        refillAsync()
    }

    fun healthyCount(): Int = available.count { it.isAlive }

    /** Test inspection — total spawn invocations since pool creation. */
    internal fun totalSpawns(): Int = spawnCount.get()

    private fun spawnOne(): SpawnedProcess {
        val proc = spawner.spawn(listOf("--bare", "-p", "--stream-json"))
        spawnCount.incrementAndGet()
        return proc
    }

    private fun refillAsync() {
        refillScope.launch {
            stateMutex.withLock {
                // Only refill up to the configured size — never overspawn.
                val current = available.size + (size - poolSemaphore.availablePermits)
                if (current < size) {
                    val proc = spawnOne()
                    available.addLast(proc)
                }
            }
        }
    }
}
