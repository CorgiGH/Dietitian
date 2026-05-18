package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test

/**
 * Plan-2 Task 32 — Concurrent dedup stress, RC7 PROMOTED to mandate #9 full strength.
 *
 * The Batch C smoke test already verified N=16 → 1 dispatch in commonTest. This stress test
 * verifies cold-cache concurrent N=64 → exactly 1 upstream dispatch under real-thread
 * scheduling via `Dispatchers.Default` (not the runTest single-dispatcher).
 *
 * Why desktopTest: `runBlocking` + `Dispatchers.Default` gives us actual JVM thread
 * scheduling — the runTest virtual scheduler would serialize the dedup race in a way that
 * masks any latent race condition in IdempotencyCache.dedup.
 */
class RouterConcurrentDedupStressTest {

    @Test
    fun `N=64 concurrent identical cold-cache requests collapse to exactly one dispatch`(): Unit = runBlocking(Dispatchers.Default) {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val cache = IdempotencyCache()
        val counterMutex = Mutex()
        var dispatches = 0
        // Gate the single dispatch so all 64 callers stack up on cache.dedup() before any
        // upstream call completes — this is the cold-cache race we want to catch.
        val gate = CompletableDeferred<Unit>()

        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to listOf(
                        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, m ->
                    counterMutex.withLock { dispatches++ }
                    gate.await()
                    LlmResponse(
                        provider = ProviderId("openrouter"),
                        model = m,
                        text = "shared",
                        inputTokens = 1,
                        outputTokens = 1,
                        costCents = 1,
                        finishReason = FinishReason.STOP,
                    )
                },
            ),
            cache = cache,
            budget = budget,
            auditLog = audit,
        )

        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "stress")),
        )

        val results = coroutineScope {
            val jobs = (1..64).map {
                async(Dispatchers.Default) { router.route(req) }
            }
            // Give the 64 launches a moment to enter dedup() before we release the gate.
            launch {
                delay(100)
                gate.complete(Unit)
            }
            jobs.awaitAll()
        }

        dispatches shouldBe 1
        results.size shouldBe 64
        results.all { it.text == "shared" } shouldBe true
        // Exactly one llm_call audit row — the coalesced dispatch.
        audit.snapshot().count { it.kind == "llm_call" } shouldBe 1
    }
}
