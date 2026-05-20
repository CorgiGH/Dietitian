package com.dietician.shared.llm.provider

import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeMaxCliProviderTest {

    private fun request(prompt: String = "how much protein?"): LlmRequest =
        LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, prompt)),
        )

    private val successStdout = """
        [
          {"type":"assistant","message":{"model":"claude-opus-4-7","content":[{"type":"text","text":"hi"}]}},
          {"type":"result","subtype":"success","is_error":false,"result":"137 g.",
           "stop_reason":"end_turn","usage":{"input_tokens":900,"output_tokens":12}}
        ]
    """.trimIndent()

    @Test
    fun `call returns parsed response on a clean run`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(exitCode = 0, stdout = successStdout))
        val provider = ClaudeMaxCliProvider.forTesting(runner)
        val resp = provider.call(request(), model = "")
        assertEquals("137 g.", resp.text)
        assertEquals(12, resp.outputTokens)
    }

    @Test
    fun `call passes one-shot flags and the user prompt as stdin`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(0, successStdout))
        ClaudeMaxCliProvider.forTesting(runner).call(request("eat what?"), model = "")
        val args = runner.lastArgs!!
        assertTrue(args.containsAll(listOf("-p", "--output-format", "json")))
        assertEquals("eat what?", runner.lastStdin)
    }

    @Test
    fun `non-zero exit code throws LlmError and is not a silent empty success`() = runTest {
        val runner = FakeClaudeCliRunner(
            CliResult(exitCode = 1, stdout = "error: unknown option '--stream-json'"),
        )
        val provider = ClaudeMaxCliProvider.forTesting(runner)
        assertFailsWith<LlmError> { provider.call(request(), model = "") }
    }

    @Test
    fun `non-success result envelope throws LlmError`() = runTest {
        val runner = FakeClaudeCliRunner(
            CliResult(0, """[{"type":"result","subtype":"error_during_execution","is_error":true,"result":""}]"""),
        )
        assertFailsWith<LlmError> { ClaudeMaxCliProvider.forTesting(runner).call(request(), "") }
    }

    @Test
    fun `repeated failures open the circuit breaker`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(exitCode = 1, stdout = "boom"))
        val breaker = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 30_000)
        val provider = ClaudeMaxCliProvider.forTesting(runner, circuitBreaker = breaker)
        repeat(3) { runCatching { provider.call(request(), "") } }
        assertFailsWith<LlmError.ProviderUnavailable> { provider.call(request(), "") }
        assertEquals(3, runner.runCount)
    }

    @Test
    fun `timeout throws LlmError Timeout`() = runTest {
        val runner = FakeClaudeCliRunner(neverReturns = true)
        val provider = ClaudeMaxCliProvider.forTesting(runner, timeoutMs = 50)
        assertFailsWith<LlmError.Timeout> { provider.call(request(), "") }
    }

    @Test
    fun `call routes model and system prompt into args`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(0, successStdout))
        val req = request("q").copy(systemPrompt = "You are a dietician.")
        ClaudeMaxCliProvider.forTesting(runner).call(req, model = "sonnet")
        val args = runner.lastArgs!!
        assertTrue(args.containsAll(listOf("--model", "sonnet")))
        assertTrue(args.containsAll(listOf("--append-system-prompt", "You are a dietician.")))
    }
}
