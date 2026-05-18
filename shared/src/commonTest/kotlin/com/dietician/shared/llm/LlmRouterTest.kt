package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

/**
 * Plan-2 Task 19 — LlmRouter happy path + chain fallback + budget + permanent failures.
 *
 * Task 20 extends this file with idempotency-dedup concurrency, all-failed exhaustion,
 * non-LlmError → TransientFailure wrap, and per-attempt audit-row presence.
 */
class LlmRouterTest {
    private val subject = "victor"
    private val taskKey = ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT)

    private fun req(content: String = "hello", task: TaskType = TaskType.TEXT, device: DeviceClass = DeviceClass.VICTOR_DESKTOP): LlmRequest =
        LlmRequest(
            subjectId = subject,
            task = task,
            deviceClass = device,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, content)),
        )

    private fun resp(text: String, providerId: String, model: String, inT: Int = 10, outT: Int = 5, costCents: Int = 1): LlmResponse =
        LlmResponse(
            provider = ProviderId(providerId),
            model = model,
            text = text,
            inputTokens = inT,
            outputTokens = outT,
            costCents = costCents,
            finishReason = FinishReason.STOP,
        )

    /** Chain with two providers — first openrouter, then claudemax. */
    private fun twoProviderConfig(): RouterConfig = RouterConfig(
        chains = mapOf(
            taskKey to listOf(
                LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
            ),
        ),
    )

    @Test
    fun `happy path returns first provider response and writes llm_call audit row`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        var openRouterCalls = 0
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, model ->
                    openRouterCalls++
                    resp("first", "openrouter", model, inT = 12, outT = 4, costCents = 5)
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("should not be called") },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        val result = router.route(req("hello"))

        openRouterCalls shouldBe 1
        result.text shouldBe "first"
        result.provider.raw shouldBe "openrouter"

        val rows = audit.snapshot()
        rows.count { it.kind == "llm_call" } shouldBe 1
        val row = rows.first { it.kind == "llm_call" }
        row.model shouldBe "openrouter/anthropic/claude-sonnet-4.5"
        row.inputTokens shouldBe 12
        row.outputTokens shouldBe 4
        row.costCents shouldBe 5
        row.extra["device_class"] shouldBe "VICTOR_DESKTOP"
        row.extra["task"] shouldBe "TEXT"
    }

    @Test
    fun `budget finalize uses realized cost not estimate`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger(capCentsPerSubject = mapOf(subject to 10_000))
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, m ->
                    resp("ok", "openrouter", m, inT = 100, outT = 50, costCents = 17)
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("unused") },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        router.route(req())

        // Final ledger = realized 17 cents (estimate was reconciled via finalize delta).
        budget.usedCents(subject, ProviderId("openrouter")) shouldBe 17
    }

    @Test
    fun `transient failure on first provider falls back to second`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.TransientFailure(RuntimeException("upstream 503"))
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, m ->
                    resp("fallback", "claudemax-cli", m)
                },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        val result = router.route(req())

        result.text shouldBe "fallback"
        result.provider.raw shouldBe "claudemax-cli"

        val rows = audit.snapshot()
        rows.count { it.kind == "llm_call_failed_transient" } shouldBe 1
        rows.count { it.kind == "llm_call" } shouldBe 1
        rows.first { it.kind == "llm_call_failed_transient" }.extra["provider"] shouldBe "openrouter"

        // First provider reservation must be released (no cap configured = used stays at 0 net).
        budget.usedCents(subject, ProviderId("openrouter")) shouldBe 0
    }

    @Test
    fun `budget exhausted on first provider skips to second`() = runTest {
        val audit = InMemoryAuditLogSink()
        // Cap is PER SUBJECT but `used` is PER (subject, provider). Pre-fill the openrouter
        // used so its next reserve(estimate ~ 6 cents) breaches cap, while claudemax-cli still
        // has its own used=0 and the cap headroom for its small estimate.
        val budget = InMemoryBudgetLedger(capCentsPerSubject = mapOf(subject to 100))
        budget.reserve(subject, ProviderId("openrouter"), 1, 99) // open's used = 99, cap = 100

        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ -> error("must not be called") },
                ProviderId("claudemax-cli") to ProviderCallable { _, m ->
                    resp("fallback", "claudemax-cli", m)
                },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        val result = router.route(req())

        result.provider.raw shouldBe "claudemax-cli"
        val rows = audit.snapshot()
        rows.any { it.kind == "llm_call_failed_transient" && it.extra["error_kind"] == "budget_exhausted" } shouldBe true
    }

    @Test
    fun `permanent failure throws immediately and does not fall back`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        var secondCalled = false
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.PermanentFailure(RuntimeException("invalid model"))
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ ->
                    secondCalled = true
                    error("should not be called")
                },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        shouldThrow<LlmError.PermanentFailure> { router.route(req()) }
        secondCalled shouldBe false

        val rows = audit.snapshot()
        rows.count { it.kind == "llm_call_failed_permanent" } shouldBe 1
        rows.first { it.kind == "llm_call_failed_permanent" }.extra["provider"] shouldBe "openrouter"
    }

    @Test
    fun `content filtered throws immediately and does not fall back`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.ContentFiltered("safety policy")
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("should not be called") },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        shouldThrow<LlmError.ContentFiltered> { router.route(req()) }
        val rows = audit.snapshot()
        rows.any { it.kind == "llm_call_failed_permanent" && it.extra["error_kind"] == "ContentFiltered" } shouldBe true
    }

    @Test
    fun `all providers fail throws ProviderUnavailable and writes all_failed row`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.RateLimitExceeded(retryAfterMs = 1_000)
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ ->
                    throw LlmError.Timeout(phase = "read")
                },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )

        shouldThrow<LlmError.ProviderUnavailable> { router.route(req()) }

        val rows = audit.snapshot()
        rows.count { it.kind == "llm_call_failed_transient" } shouldBe 2
        val allFailed = rows.first { it.kind == "llm_call_all_failed" }
        allFailed.extra["errors_count"] shouldBe "2"
    }

    @Test
    fun `empty chain throws IllegalArgumentException`() = runTest {
        val emptyCfg = RouterConfig(
            chains = mapOf(
                ChainKey(DeviceClass.SERVER, TaskType.VISION) to emptyList(),
            ),
        )
        val router = LlmRouter(
            config = emptyCfg,
            providers = emptyMap(),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = InMemoryAuditLogSink(),
        )
        shouldThrow<IllegalArgumentException> {
            router.route(req(task = TaskType.VISION, device = DeviceClass.SERVER))
        }
    }

    @Test
    fun `audit-row model field stitches providerId and model`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, m -> resp("x", "openrouter", m) },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("unused") },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        router.route(req())
        val row = audit.snapshot().first { it.kind == "llm_call" }
        row.model!! shouldContain "openrouter/"
        row.model!! shouldContain "claude-sonnet-4.5"
    }

    @Test
    fun `hashPrompt differs across distinct prompts`() {
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = emptyMap(),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = InMemoryAuditLogSink(),
        )
        val h1 = router.hashPrompt(req("alpha"))
        val h2 = router.hashPrompt(req("beta"))
        (h1 != h2) shouldBe true
    }

    @Test
    fun `hashPrompt is stable across calls for identical request`() {
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = emptyMap(),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = InMemoryAuditLogSink(),
        )
        router.hashPrompt(req("same")) shouldBe router.hashPrompt(req("same"))
    }

    @Test
    fun `unknown providerId in chain is skipped`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                // openrouter NOT registered — chain falls through to claudemax-cli.
                ProviderId("claudemax-cli") to ProviderCallable { _, m -> resp("ok", "claudemax-cli", m) },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )
        val result = router.route(req())
        result.provider.raw shouldBe "claudemax-cli"

        val rows = audit.snapshot()
        val callableMissing = rows.firstOrNull {
            it.kind == "llm_call_failed_transient" && it.extra["error_kind"] == "callable_missing"
        }
        (callableMissing != null) shouldBe true
        callableMissing!!.extra["provider"] shouldBe "openrouter"
    }

    // ---------------------------------------------------------------------------
    // Task 20 — edge paths: dedup concurrency, all-failed via budget, non-LlmError
    // ---------------------------------------------------------------------------

    @Test
    fun `Task20 N=16 concurrent identical requests collapse to single dispatch via IdempotencyCache`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val cache = IdempotencyCache()
        val counterMutex = Mutex()
        var dispatches = 0
        val gate = CompletableDeferred<Unit>()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, m ->
                    counterMutex.withLock { dispatches++ }
                    gate.await()
                    resp("shared", "openrouter", m)
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("unused") },
            ),
            cache = cache,
            budget = budget,
            auditLog = audit,
        )
        val results = coroutineScope {
            val jobs = (1..16).map {
                async { router.route(req("dedup-content")) }
            }
            launch {
                delay(50)
                gate.complete(Unit)
            }
            jobs.awaitAll()
        }
        dispatches shouldBe 1
        results.size shouldBe 16
        results.all { it.text == "shared" } shouldBe true
        // Exactly one llm_call audit row even though 16 callers hit route().
        audit.snapshot().count { it.kind == "llm_call" } shouldBe 1
    }

    @Test
    fun `Task20 budget cap blocks all chain entries leading to ProviderUnavailable`() = runTest {
        val audit = InMemoryAuditLogSink()
        // Pre-saturate BOTH provider ledgers so reserve() throws for each chain entry.
        val budget = InMemoryBudgetLedger(capCentsPerSubject = mapOf(subject to 10))
        budget.reserve(subject, ProviderId("openrouter"), 1, 10)
        budget.reserve(subject, ProviderId("claudemax-cli"), 1, 10)

        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ -> error("never called") },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("never called") },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )
        shouldThrow<LlmError.ProviderUnavailable> { router.route(req()) }
        val rows = audit.snapshot()
        val transientRows = rows.filter { it.kind == "llm_call_failed_transient" }
        transientRows.size shouldBe 2
        transientRows.all { it.extra["error_kind"] == "budget_exhausted" } shouldBe true
        rows.any { it.kind == "llm_call_all_failed" } shouldBe true
    }

    @Test
    fun `Task20 ProviderCallable throwing plain RuntimeException is wrapped as TransientFailure and chain continues`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw RuntimeException("socket reset")
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, m ->
                    resp("recovered", "claudemax-cli", m)
                },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
        )
        val result = router.route(req())
        result.text shouldBe "recovered"
        val rows = audit.snapshot()
        val transient = rows.first { it.kind == "llm_call_failed_transient" }
        transient.extra["error_kind"] shouldBe "TransientFailure"
        transient.extra["error"] shouldBe "socket reset"
        // Reservation must be released so the openrouter ledger is back to 0.
        budget.usedCents(subject, ProviderId("openrouter")) shouldBe 0
    }

    @Test
    fun `Task20 audit row written for every attempt success-or-failure`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.TransientFailure(RuntimeException("upstream"))
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, m ->
                    resp("ok", "claudemax-cli", m)
                },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        router.route(req())
        val rows = audit.snapshot()
        // First attempt → llm_call_failed_transient (openrouter)
        // Second attempt → llm_call (claudemax-cli)
        rows.size shouldBe 2
        rows[0].kind shouldBe "llm_call_failed_transient"
        rows[0].extra["provider"] shouldBe "openrouter"
        rows[1].kind shouldBe "llm_call"
    }

    @Test
    fun `Task20 dedup awaiters all see exception when sole dispatch throws`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val cache = IdempotencyCache()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.PermanentFailure(IllegalStateException("invalid input"))
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("never") },
            ),
            cache = cache,
            budget = budget,
            auditLog = audit,
        )

        val results = coroutineScope {
            (1..4).map {
                async { runCatching { router.route(req()) } }
            }.awaitAll()
        }
        results.size shouldBe 4
        results.all { it.isFailure } shouldBe true
        // PermanentFailure is the canonical underlying error; awaiters share it.
        results.all { it.exceptionOrNull() is LlmError.PermanentFailure } shouldBe true
    }

    @Test
    fun `Task20 audit row count for happy path = exactly one llm_call`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, m -> resp("ok", "openrouter", m) },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("unused") },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        router.route(req())
        val rows = audit.snapshot()
        rows.size shouldBe 1
        rows[0].kind shouldBe "llm_call"
    }

    @Test
    fun `error kinds list captures both fallback rungs`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = twoProviderConfig(),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    throw LlmError.RateLimitExceeded(retryAfterMs = null)
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ ->
                    throw LlmError.Timeout(phase = "connect")
                },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        shouldThrow<LlmError.ProviderUnavailable> { router.route(req()) }
        val kinds = audit.snapshot()
            .filter { it.kind == "llm_call_failed_transient" }
            .map { it.extra["error_kind"]!! }
        kinds shouldContain "RateLimitExceeded"
        kinds shouldContain "Timeout"
    }
}
