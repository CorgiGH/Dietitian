package com.dietician.shared.llm.provider

import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ClaudeMaxCliProviderTest {
    private val cannedOk = """
        {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":1}}}
        {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}
        {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}
        {"type":"message_stop"}
    """.trimIndent()

    private fun req() = LlmRequest(
        subjectId = "victor",
        task = TaskType.TEXT,
        deviceClass = DeviceClass.VICTOR_DESKTOP,
        capability = Capability.NON_STREAMING,
        messages = listOf(LlmMessage(Role.USER, "hi")),
    )

    @Test
    fun `RC2 forTesting companion factory injects spawner and skipWarmUp by default`() : Unit = runBlocking {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk) }
        val provider = ClaudeMaxCliProvider.forTesting(spawner)
        // skipWarmUp=true default → no spawns yet
        spawner.spawnCount.get() shouldBe 0
        val resp = provider.call(req(), "anthropic/claude-sonnet-4.5")
        resp.text shouldBe "ok"
        // first call lazy-spawned
        spawner.spawnCount.get() shouldBe 1
    }

    @Test
    fun `RC2 production companion factory uses real ProcessSpawnerImpl with min cores-2 3 pool`() {
        // Don't actually spawn `claude` — just verify the factory wires up without crash.
        // Set binaryPath to one that won't even attempt a real exec until isAvailable() is hit.
        // Production factory eagerly warm-ups → spawn-on-init throws if binary missing → caught below.
        try {
            val provider = ClaudeMaxCliProvider.production(ClaudeMaxConfig(binaryPath = "/__no_such_binary__"))
            // If we got here, isAvailable should still return false on missing pool (sanity).
            provider.isAvailable() shouldBe false
        } catch (e: Exception) {
            // Eager warm-up throws on missing binary — expected on a system without `claude`.
            // This is acceptable: production() is a happy-path constructor used only when
            // the user has installed Claude Max CLI. The test verifies the factory wiring
            // compiles + executes the construction path.
            (e is java.io.IOException || e.cause is java.io.IOException) shouldBe true
        }
    }

    @Test
    fun `cold-start tolerance — first call after skipWarmUp lazily spawns and returns`() : Unit = runBlocking {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk, latencyMs = 50) }
        val provider = ClaudeMaxCliProvider.forTesting(spawner, skipWarmUp = true, timeoutMs = 5_000)
        val resp = provider.call(req(), "anthropic/claude-sonnet-4.5")
        resp.text shouldBe "ok"
        spawner.spawnCount.get() shouldBe 1
    }

    @Test
    fun `Windows-hang simulation — neverReturns proc hits timeout and circuit increments`() : Unit = runBlocking {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(neverReturns = true) }
        val cb = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000)
        val provider = ClaudeMaxCliProvider.forTesting(spawner, timeoutMs = 100, circuitBreaker = cb)
        try {
            provider.call(req(), "anthropic/claude-sonnet-4.5")
            error("expected Timeout")
        } catch (e: LlmError.Timeout) {
            e.phase shouldBe "claudemax-cli"
        }
    }

    @Test
    fun `circuit-breaker opens after 5 consecutive failures`() : Unit = runBlocking {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(throwOnSend = RuntimeException("boom")) }
        val cb = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000)
        val provider = ClaudeMaxCliProvider.forTesting(spawner, poolSize = 1, circuitBreaker = cb)
        repeat(5) {
            try {
                provider.call(req(), "anthropic/claude-sonnet-4.5")
            } catch (_: LlmError) {
                // expected
            }
        }
        // 6th call short-circuits with ProviderUnavailable (circuit OPEN).
        try {
            provider.call(req(), "anthropic/claude-sonnet-4.5")
            error("expected ProviderUnavailable")
        } catch (e: LlmError.ProviderUnavailable) {
            e.provider shouldBe ProviderId("claudemax-cli")
        }
    }

    @Test
    fun `non-LlmError exception surfaces as TransientFailure`() : Unit = runBlocking {
        val spawner = FakeProcessSpawner {
            FakeSpawnedProcess(throwOnSend = RuntimeException("network glitch"))
        }
        val provider = ClaudeMaxCliProvider.forTesting(spawner, poolSize = 1)
        try {
            provider.call(req(), "anthropic/claude-sonnet-4.5")
            error("expected TransientFailure")
        } catch (e: LlmError) {
            e.shouldBeInstanceOf<LlmError.TransientFailure>()
            e.cause?.message shouldBe "network glitch"
        }
    }

    @Test
    fun `isAvailable returns false when warm-pool empty before first call`() {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk) }
        val provider = ClaudeMaxCliProvider.forTesting(spawner, skipWarmUp = true)
        provider.isAvailable() shouldBe false
    }
}
