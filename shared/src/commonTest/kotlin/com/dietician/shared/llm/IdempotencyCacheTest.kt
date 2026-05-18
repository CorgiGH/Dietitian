package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IdempotencyCacheTest {
    private fun sample(text: String = "hi") = LlmResponse(
        provider = ProviderId("test"),
        model = "test-model",
        text = text,
        inputTokens = 1,
        outputTokens = 1,
        costCents = 0,
        finishReason = FinishReason.STOP,
    )

    private val key = IdempotencyCache.Key("victor", "hash-abc", "claude-sonnet-4.5")

    @Test
    fun `cached result returned without dispatch within TTL`() = runTest {
        var now = 0L
        val cache = IdempotencyCache(ttlMs = 5_000, clock = { now })
        var dispatches = 0

        cache.dedup(key) {
            dispatches++
            sample("first")
        }
        now = 100
        cache.dedup(key) {
            dispatches++
            sample("second")
        }
        dispatches shouldBe 1
    }

    @Test
    fun `cache miss after TTL re-dispatches`() = runTest {
        var now = 0L
        val cache = IdempotencyCache(ttlMs = 1_000, clock = { now })
        var dispatches = 0

        cache.dedup(key) {
            dispatches++
            sample("a")
        }
        now = 1_001
        cache.dedup(key) {
            dispatches++
            sample("b")
        }
        dispatches shouldBe 2
    }

    @Test
    fun `N=32 concurrent identical calls collapse to one dispatch`() = runTest {
        val cache = IdempotencyCache()
        val counterMutex = Mutex()
        var dispatches = 0
        val gate = CompletableDeferred<Unit>()

        coroutineScope {
            val jobs = (1..32).map {
                async {
                    cache.dedup(key) {
                        counterMutex.withLock { dispatches++ }
                        // Gate ensures all 32 coroutines reach the dedup call before any
                        // completes, exercising the in-flight coalescing path. Without it,
                        // the first call could finish before the others entered.
                        gate.await()
                        sample("once")
                    }
                }
            }
            // Give the launches a chance to register their dedup calls.
            launch {
                delay(50)
                gate.complete(Unit)
            }
            jobs.awaitAll()
        }

        dispatches shouldBe 1
    }

    @Test
    fun `concurrent awaiters all see the same response`() = runTest {
        val cache = IdempotencyCache()
        val gate = CompletableDeferred<Unit>()

        val results = coroutineScope {
            (1..8).map {
                async {
                    cache.dedup(key) {
                        gate.await()
                        sample("shared")
                    }
                }
            }.also {
                launch {
                    delay(20)
                    gate.complete(Unit)
                }
            }.awaitAll()
        }

        results.size shouldBe 8
        results.all { it.text == "shared" } shouldBe true
    }

    @Test
    fun `exception propagates to all coalesced awaiters and clears pending`() = runTest {
        val cache = IdempotencyCache()
        val gate = CompletableDeferred<Unit>()
        var dispatches = 0
        val counterMutex = Mutex()

        val failJobs = coroutineScope {
            (1..4).map {
                async {
                    runCatching {
                        cache.dedup(key) {
                            counterMutex.withLock { dispatches++ }
                            gate.await()
                            throw RuntimeException("network boom")
                        }
                    }
                }
            }.also {
                launch {
                    delay(20)
                    gate.complete(Unit)
                }
            }.awaitAll()
        }
        dispatches shouldBe 1
        failJobs.forEach { it.isFailure shouldBe true }

        // Subsequent call should dispatch fresh (pending entry was cleared on failure).
        val ok = cache.dedup(key) {
            dispatches++
            sample("ok")
        }
        ok.text shouldBe "ok"
        dispatches shouldBe 2
    }

    @Test
    fun `exception from compute is rethrown`() = runTest {
        val cache = IdempotencyCache()
        assertFailsWith<IllegalStateException> {
            cache.dedup(key) { throw IllegalStateException("nope") }
        }
    }

    @Test
    fun `distinct keys do not coalesce`() = runTest {
        val cache = IdempotencyCache()
        var dispatches = 0
        val k1 = IdempotencyCache.Key("victor", "h1", "m1")
        val k2 = IdempotencyCache.Key("victor", "h2", "m1")

        cache.dedup(k1) {
            dispatches++
            sample()
        }
        cache.dedup(k2) {
            dispatches++
            sample()
        }
        dispatches shouldBe 2
    }
}
