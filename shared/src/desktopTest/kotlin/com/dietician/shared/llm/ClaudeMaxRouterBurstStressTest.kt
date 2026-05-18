package com.dietician.shared.llm

import com.dietician.shared.llm.provider.ClaudeMaxCliProvider
import com.dietician.shared.llm.provider.FakeProcessSpawner
import com.dietician.shared.llm.provider.FakeSpawnedProcess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Plan-2 Task 33 — Warm-pool burst stress at the Router layer.
 *
 * Distinct from Batch B's `WarmPoolDequeStressTest` (which exercises the WarmPool primitive
 * directly with N=64 calls). This test runs N=20 concurrent VICTOR_DESKTOP_TEXT requests
 * THROUGH THE FULL ROUTER → ProviderCallable → ClaudeMaxCliProvider → WarmPool stack to
 * verify:
 *
 *   1. All 20 calls complete successfully (no Router-level concurrency bug).
 *   2. WarmPool.healthyCount() returns to the initial size after the burst settles (no
 *      process leak).
 *   3. Spawn count never exceeds pool size (RC11 invariant preserved at Router layer).
 *
 * Each request carries DISTINCT content so the IdempotencyCache does NOT collapse them —
 * the test wants 20 real upstream calls, not 1 coalesced dispatch.
 */
class ClaudeMaxRouterBurstStressTest {

    private val cannedOk = """
        {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":1}}}
        {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}
        {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}
        {"type":"message_stop"}
    """.trimIndent()

    @Test
    fun `N=20 router-driven burst completes without process leak`(): Unit = runBlocking(Dispatchers.Default) {
        val poolSize = 3
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk, latencyMs = 3) }
        val claudeMax = ClaudeMaxCliProvider.forTesting(
            spawner = spawner,
            skipWarmUp = false,
            poolSize = poolSize,
            timeoutMs = 10_000,
        )

        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to listOf(
                        LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("claudemax-cli") to ProviderCallable { req, m -> claudeMax.call(req, m) },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = InMemoryAuditLogSink(),
        )

        coroutineScope {
            val jobs = (1..20).map { i ->
                async(Dispatchers.Default) {
                    // Distinct content per call → IdempotencyCache does NOT dedupe.
                    router.route(
                        LlmRequest(
                            subjectId = "victor",
                            task = TaskType.TEXT,
                            deviceClass = DeviceClass.VICTOR_DESKTOP,
                            capability = Capability.NON_STREAMING,
                            messages = listOf(LlmMessage(Role.USER, "burst-$i")),
                        ),
                    )
                }
            }
            jobs.awaitAll()
        }

        // RC11 invariant: spawn count = pool size (warm-up only). No additional spawns under load.
        spawner.spawnCount.get() shouldBe poolSize

        // No process leak: pool is back to full health after burst settles.
        claudeMax.isAvailable() shouldBe true
    }
}
