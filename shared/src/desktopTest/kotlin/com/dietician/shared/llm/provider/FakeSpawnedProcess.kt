package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test double for [SpawnedProcess] — replays canned stream-json output.
 *
 * Construction modes:
 *   - `cannedOutput` → instantly return the parsed response on every send().
 *   - `latencyMs` → suspend for this duration before returning (use with `withTimeout` tests).
 *   - `neverReturns=true` → never completes (simulates Windows-hang where the proc's stdout
 *     pipe is buffered + the proc is technically alive but produces no bytes).
 *   - `throwOnSend` → throws the given exception (simulates Anthropic CLI hard-failures).
 */
class FakeSpawnedProcess(
    private val cannedOutput: String = "",
    private val latencyMs: Long = 0,
    private val neverReturns: Boolean = false,
    private val throwOnSend: Throwable? = null,
    private val parser: ClaudeMaxStreamParser = ClaudeMaxStreamParser(),
) : SpawnedProcess {
    private val alive = AtomicBoolean(true)
    val sendCount = AtomicInteger(0)

    override val isAlive: Boolean
        get() = alive.get()

    override suspend fun send(request: LlmRequest, model: String): LlmResponse {
        sendCount.incrementAndGet()
        throwOnSend?.let { throw it }
        if (neverReturns) {
            // Suspend forever; test should wrap in withTimeout.
            kotlinx.coroutines.awaitCancellation()
        }
        if (latencyMs > 0) delay(latencyMs)
        return parser.parse(cannedOutput, model)
    }

    override fun close() {
        alive.set(false)
    }
}

/**
 * [ProcessSpawner] test double — returns a pre-built sequence of FakeSpawnedProcess
 * instances. Throws if the test asks for more spawns than supplied (catches over-spawn bugs
 * in warm-pool stress tests).
 */
class FakeProcessSpawner(
    private val factory: () -> FakeSpawnedProcess,
) : ProcessSpawner {
    val spawnCount = AtomicInteger(0)
    val spawned = mutableListOf<FakeSpawnedProcess>()

    override fun spawn(args: List<String>): SpawnedProcess {
        spawnCount.incrementAndGet()
        val proc = factory()
        synchronized(spawned) { spawned.add(proc) }
        return proc
    }
}
