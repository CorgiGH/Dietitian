package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse

/**
 * Test-seam for spawning + driving the `claude --bare -p --stream-json` subprocess. Production
 * uses [ProcessSpawnerImpl]; tests inject a fake that emits canned stream-json payloads.
 *
 * The interface is intentionally narrow: spawn → returns a [SpawnedProcess] handle, which the
 * warm-pool/circuit-breaker layer drives. `args` is just the CLI argv list — caller decides
 * the model + system-prompt wiring (see [ClaudeMaxProcess]).
 */
interface ProcessSpawner {
    fun spawn(args: List<String>): SpawnedProcess
}

/**
 * Single-shot or warm subprocess handle. [send] writes a request to the proc's stdin, reads
 * the full stream-json output until message_stop, and returns the parsed response.
 *
 * [isAlive] is checked by the warm-pool on acquire — a dead proc is evicted + replaced.
 * [close] is called on shutdown OR after `markSick` in the warm-pool.
 */
interface SpawnedProcess {
    val isAlive: Boolean

    suspend fun send(request: LlmRequest, model: String): LlmResponse

    fun close()
}
