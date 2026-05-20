package com.dietician.shared.llm.provider

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * Test double for [ClaudeCliRunner]. Records the args + stdin it was called with,
 * and returns a canned [CliResult] (or throws, or hangs).
 */
class FakeClaudeCliRunner(
    private val result: CliResult = CliResult(0, ""),
    private val latencyMs: Long = 0,
    private val neverReturns: Boolean = false,
    private val throwOnRun: Throwable? = null,
) : ClaudeCliRunner {
    var lastArgs: List<String>? = null
    var lastStdin: String? = null
    var runCount: Int = 0
        private set

    override suspend fun run(args: List<String>, stdin: String): CliResult {
        runCount += 1
        lastArgs = args
        lastStdin = stdin
        throwOnRun?.let { throw it }
        if (neverReturns) awaitCancellation()
        if (latencyMs > 0) delay(latencyMs)
        return result
    }
}
