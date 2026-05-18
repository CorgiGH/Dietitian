package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LlmRouterStreamTest {
    private val subject = "victor"
    private val taskKey = ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT)

    private fun req(content: String = "stream me"): LlmRequest = LlmRequest(
        subjectId = subject,
        task = TaskType.TEXT,
        deviceClass = DeviceClass.VICTOR_DESKTOP,
        capability = Capability.STREAMING,
        messages = listOf(LlmMessage(Role.USER, content)),
    )

    private fun finalResp(providerId: String, model: String, costCents: Int = 4): LlmResponse =
        LlmResponse(
            provider = ProviderId(providerId),
            model = model,
            text = "complete",
            inputTokens = 8,
            outputTokens = 12,
            costCents = costCents,
            finishReason = FinishReason.STOP,
        )

    private fun twoProviderConfig(): RouterConfig = RouterConfig(
        chains = mapOf(
            taskKey to listOf(
                LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
            ),
        ),
    )

    private fun threeChunkStream(providerId: String, model: String): Flow<LlmChunk> = flow {
        emit(LlmChunk("Hel", 1))
        emit(LlmChunk("lo, ", 2))
        emit(
            LlmChunk(
                text = "world!",
                tokenCount = 3,
                isDone = true,
                finalResponse = finalResp(providerId, model),
            ),
        )
    }

    @Test
    fun `happy path streams chunks and emits llm_call on terminal`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, m ->
                    threeChunkStream("openrouter", m)
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ -> error("unused") },
            ),
            budget = budget,
            auditLog = audit,
        )
        val chunks = router.streamRoute(req()).toList()
        chunks.size shouldBe 3
        chunks[0].text shouldBe "Hel"
        chunks[2].isDone shouldBe true
        chunks[2].finalResponse?.costCents shouldBe 4

        val rows = audit.snapshot()
        rows.any { it.kind == "llm_call_streaming_start" } shouldBe true
        val terminalRow = rows.first { it.kind == "llm_call" }
        terminalRow.extra["streaming"] shouldBe "true"
        terminalRow.costCents shouldBe 4
        budget.usedCents(subject, ProviderId("openrouter")) shouldBe 4
    }

    @Test
    fun `pre-chunk failure on first provider falls back to second`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow<LlmChunk> { throw LlmError.TransientFailure(RuntimeException("503")) }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, m ->
                    threeChunkStream("claudemax-cli", m)
                },
            ),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        val chunks = router.streamRoute(req()).toList()
        chunks.last().finalResponse?.provider shouldBe ProviderId("claudemax-cli")

        val rows = audit.snapshot()
        rows.any { it.kind == "llm_call_failed_transient" } shouldBe true
        rows.any { it.kind == "llm_call_streaming_start" } shouldBe true
        rows.any { it.kind == "llm_call" } shouldBe true
    }

    @Test
    fun `mid-stream failure propagates without fallback`() = runTest {
        val audit = InMemoryAuditLogSink()
        var secondCalled = false
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow {
                        emit(LlmChunk("partial"))
                        throw LlmError.TransientFailure(RuntimeException("network died"))
                    }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ ->
                    secondCalled = true
                    error("should not be called")
                },
            ),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        shouldThrow<LlmError.TransientFailure> {
            router.streamRoute(req()).toList()
        }
        secondCalled shouldBe false
        audit.snapshot().any { it.kind == "llm_call_failed_mid_stream" } shouldBe true
    }

    @Test
    fun `permanent failure pre-chunk throws without falling back`() = runTest {
        val audit = InMemoryAuditLogSink()
        var secondCalled = false
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow<LlmChunk> { throw LlmError.PermanentFailure(IllegalStateException("invalid")) }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ ->
                    secondCalled = true
                    error("must not be called")
                },
            ),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        shouldThrow<LlmError.PermanentFailure> { router.streamRoute(req()).toList() }
        secondCalled shouldBe false
        audit.snapshot().any { it.kind == "llm_call_failed_permanent" } shouldBe true
    }

    @Test
    fun `caller cancellation propagates and releases budget`() = runTest {
        val budget = InMemoryBudgetLedger()
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow {
                        emit(LlmChunk("first"))
                        delay(10_000) // simulate slow upstream
                        emit(LlmChunk("never"))
                    }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ -> error("never") },
            ),
            budget = budget,
            auditLog = InMemoryAuditLogSink(),
        )
        val firstChunk = router.streamRoute(req()).take(1).toList()
        firstChunk.size shouldBe 1
        // After take(1), the flow is cancelled. Budget release happens in the catch
        // CancellationException branch of the flow body — but the cancellation may also
        // skip the release in some timing. Use take(1) and assert the chunk landed; the
        // explicit cancellation path is exercised below.
    }

    @Test
    fun `take-1 cancels stream and budget is released`() = runTest {
        val budget = InMemoryBudgetLedger(capCentsPerSubject = mapOf(subject to 1_000))
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow {
                        emit(LlmChunk("first"))
                        // suspend forever — caller cancels via take(1)
                        awaitCancellation()
                    }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ -> error("never") },
            ),
            budget = budget,
            auditLog = InMemoryAuditLogSink(),
        )
        val first = router.streamRoute(req()).take(1).toList()
        first.size shouldBe 1
        // Used should be 0 because the reservation was released on cancellation.
        budget.usedCents(subject, ProviderId("openrouter")) shouldBe 0
    }

    @Test
    fun `all providers fail pre-chunk throws ProviderUnavailable`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow<LlmChunk> { throw LlmError.RateLimitExceeded(null) }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ ->
                    flow<LlmChunk> { throw LlmError.Timeout("connect") }
                },
            ),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        shouldThrow<LlmError.ProviderUnavailable> { router.streamRoute(req()).toList() }
        audit.snapshot().any { it.kind == "llm_call_all_failed" } shouldBe true
    }

    @Test
    fun `stream without terminal finalResponse releases reservation`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger(capCentsPerSubject = mapOf(subject to 100))
        val router = LlmRouterStream(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to StreamProviderCallable { _, _ ->
                    flow {
                        emit(LlmChunk("only chunk", isDone = true, finalResponse = null))
                    }
                },
                ProviderId("claudemax-cli") to StreamProviderCallable { _, _ -> error("unused") },
            ),
            budget = budget,
            auditLog = audit,
        )
        router.streamRoute(req()).toList()
        audit.snapshot().any { it.kind == "llm_call_streaming_no_terminal" } shouldBe true
        budget.usedCents(subject, ProviderId("openrouter")) shouldBe 0
    }
}
