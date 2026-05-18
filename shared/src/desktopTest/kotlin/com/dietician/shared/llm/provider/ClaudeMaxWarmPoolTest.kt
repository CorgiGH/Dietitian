package com.dietician.shared.llm.provider

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ClaudeMaxWarmPoolTest {
    private val cannedOk = """
        {"type":"message_start","message":{"id":"m","model":"x","usage":{"input_tokens":1}}}
        {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}
        {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}
        {"type":"message_stop"}
    """.trimIndent()

    @Test
    fun `warmUpOnInit=true spawns pool size on construction`() = runTest {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk) }
        val pool = ClaudeMaxWarmPool(spawner, size = 3, warmUpOnInit = true)
        spawner.spawnCount.get() shouldBe 3
        pool.healthyCount() shouldBe 3
    }

    @Test
    fun `warmUpOnInit=false defers spawn until first acquire`() = runTest {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk) }
        val pool = ClaudeMaxWarmPool(spawner, size = 2, warmUpOnInit = false)
        spawner.spawnCount.get() shouldBe 0
        val proc = pool.acquire()
        spawner.spawnCount.get() shouldBe 1
        pool.release(proc)
    }

    @Test
    fun `release returns healthy process to pool for reuse`() = runTest {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk) }
        val pool = ClaudeMaxWarmPool(spawner, size = 1, warmUpOnInit = true)
        val a = pool.acquire()
        pool.release(a)
        val b = pool.acquire()
        // Same instance — no new spawn.
        spawner.spawnCount.get() shouldBe 1
        b shouldBe a
        pool.release(b)
    }

    @Test
    fun `markSick evicts process and next acquire returns healthy replacement`() = kotlinx.coroutines.runBlocking {
        val spawner = FakeProcessSpawner { FakeSpawnedProcess(cannedOk) }
        val pool = ClaudeMaxWarmPool(spawner, size = 1, warmUpOnInit = true)
        val sick = pool.acquire()
        pool.markSick(sick)
        pool.release(sick)
        sick.isAlive shouldBe false
        // Refill is async on Dispatchers.IO — give it a beat to land before re-acquire.
        // (acquire() will lazily spawn if the refill hasn't landed yet, which still satisfies
        // the invariant: caller always gets a healthy proc after markSick.)
        delay(100)
        val next = pool.acquire()
        next.isAlive shouldBe true
        // Total spawns: 1 (initial) + at least 1 more (refill OR lazy on acquire).
        (spawner.spawnCount.get() >= 2) shouldBe true
        pool.release(next)
    }
}
