package com.dietician.shared.llm.provider

/** Result of one `claude` CLI invocation: process exit code + full stdout (stderr folded in). */
data class CliResult(val exitCode: Int, val stdout: String)

/**
 * Test-seam for a ONE-SHOT `claude` CLI invocation. Given an argv list + the
 * stdin payload, run the process to completion and return its exit code + full
 * stdout. Production impl is [ProcessClaudeCliRunner]; tests inject
 * [com.dietician.shared.llm.provider.FakeClaudeCliRunner] returning a canned [CliResult].
 *
 * The seam is deliberately "argv + stdin in, exitCode + stdout out" — the exact
 * one-shot contract of `claude -p`. A fake that returns a canned [CliResult]
 * exercises the real exit-code and parse decisions, unlike the previous
 * `FakeSpawnedProcess` which bypassed the protocol entirely (council 1779276774).
 */
interface ClaudeCliRunner {
    suspend fun run(args: List<String>, stdin: String): CliResult
}
