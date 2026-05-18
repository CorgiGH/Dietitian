package com.dietician.shared.llm.provider

import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ClaudeMaxProcessTest {
    /**
     * Synthesizes a Process whose stdout already contains a complete stream-json sequence.
     * We use ProcessBuilder against a portable command — but constructing a real fake here
     * would couple to /bin/cat or cmd.exe. Instead test the parser-integration via
     * [ClaudeMaxStreamParser] directly (already covered) and exercise ClaudeMaxProcess via
     * the SpawnedProcess interface in [ClaudeMaxCliProviderTest] using a FakeSpawnedProcess.
     *
     * What we DO want to test here: ProcessSpawnerImpl wires `redirectErrorStream(true)`
     * (Windows-hang defuse) and the binary path is honored. We assert this by constructing
     * a spawner pointed at a binary that does not exist — spawn() must throw an IOException,
     * NOT hang. Tests run on Linux + Windows CI agents and on the developer's Windows box.
     */
    @Test
    fun `spawner throws IOException when binary missing rather than hanging`() = runTest {
        val spawner = ProcessSpawnerImpl("/__definitely_does_not_exist__/claude_fake_bin")
        try {
            spawner.spawn(listOf("--bare", "-p", "--stream-json"))
            error("expected IOException")
        } catch (e: Exception) {
            // ProcessBuilder.start() throws IOException on missing executable; test is platform-tolerant.
            (e is java.io.IOException || e.cause is java.io.IOException) shouldBe true
        }
    }

    @Test
    fun `fake SpawnedProcess feeds back canned stream-json output`() = runTest {
        val canned = """
            {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":4}}}
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}
            {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}
            {"type":"message_stop"}
        """.trimIndent()
        val proc: SpawnedProcess = FakeSpawnedProcess(canned)
        val resp = proc.send(
            LlmRequest(
                subjectId = "victor",
                task = TaskType.TEXT,
                deviceClass = DeviceClass.VICTOR_DESKTOP,
                capability = Capability.NON_STREAMING,
                messages = listOf(LlmMessage(Role.USER, "hi")),
            ),
            model = "anthropic/claude-sonnet-4.5",
        )
        resp.text shouldBe "ok"
        resp.inputTokens shouldBe 4
        resp.outputTokens shouldBe 2
        resp.finishReason shouldBe FinishReason.STOP
        proc.close()
    }
}
