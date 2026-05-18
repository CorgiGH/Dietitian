package com.dietician.shared.llm

import com.dietician.shared.llm.provider.AnthropicProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Plan-2 Task 26 (RC10 test name) — end-to-end plumbing for Anthropic prompt caching.
 *
 * Asserts the three-stage round trip:
 *   1. Request body carries `cache_control: ephemeral` on the system block + last user
 *      block when CacheControl.EPHEMERAL is requested AND payload exceeds minimum.
 *   2. Response `usage.cache_read_input_tokens` + `usage.cache_creation_input_tokens`
 *      flow into [LlmResponse.cacheReadTokens] + [LlmResponse.cacheWriteTokens].
 *   3. Cost math uses [Price.computeCostCentsWithCache] — cache reads cost ~10% of base
 *      input rate, cache writes ~125%. Both deltas are visible in [LlmResponse.costCents].
 */
class PromptCacheDtoPlumbingTest {

    private val cfg = ProviderConfig(
        apiKey = "sk-ant-test",
        baseUrl = "https://api.anthropic.test",
        timeouts = Timeouts(),
    )

    private fun mockClient(
        captureBody: (String) -> Unit = {},
        respondJson: String,
    ): HttpClient {
        val engine = MockEngine { req ->
            captureBody(req.body.toByteArray().decodeToString())
            respond(
                content = respondJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) { install(ContentNegotiation) { json() } }
    }

    private fun longContent(): String =
        // ~6000 chars / 4 ≈ 1500 tokens, above Sonnet 1024 minimum.
        "Recipe context: " + "garbanzo tagine ".repeat(400)

    @Test
    fun `request body includes cache_control on system block when EPHEMERAL`() = runTest {
        var body = ""
        val provider = AnthropicProvider(
            client = mockClient({ body = it }, sonnetCachedResponse(read = 0, write = 1500)),
            config = cfg,
        )
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, longContent())),
            systemPrompt = "you are a dietician " + "with romanian context ".repeat(80),
            cacheControl = CacheControl.EPHEMERAL,
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        body shouldContain "\"cache_control\":{\"type\":\"ephemeral\"}"
        // System block emitted as ARRAY (not plain string) on cache path.
        body shouldContain "\"system\":["
    }

    @Test
    fun `response cache_read_input_tokens populates LlmResponse cacheReadTokens`() = runTest {
        val provider = AnthropicProvider(
            client = mockClient(respondJson = sonnetCachedResponse(read = 1500, write = 0)),
            config = cfg,
        )
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, longContent())),
            systemPrompt = "long system " + "ctx ".repeat(200),
            cacheControl = CacheControl.EPHEMERAL,
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.cacheReadTokens shouldBe 1500
        resp.cacheWriteTokens shouldBe 0
    }

    @Test
    fun `response cache_creation_input_tokens populates LlmResponse cacheWriteTokens`() = runTest {
        val provider = AnthropicProvider(
            client = mockClient(respondJson = sonnetCachedResponse(read = 0, write = 2000)),
            config = cfg,
        )
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, longContent())),
            systemPrompt = "first time " + "preamble ".repeat(200),
            cacheControl = CacheControl.EPHEMERAL,
        )
        val resp = provider.call(req, "anthropic/claude-sonnet-4.5")
        resp.cacheReadTokens shouldBe 0
        resp.cacheWriteTokens shouldBe 2000
    }

    @Test
    fun `cost discount cache reads at 10 percent of base input rate`() {
        // claude-sonnet-4.5 input rate = 300 cents/Mtok.
        val price = ModelPriceLookup.lookup(ProviderId("anthropic"), "anthropic/claude-sonnet-4.5")!!
        // 1_000_000 cache-read tokens at 10% of 300 = 30 cents/Mtok → 30 cents.
        val cost = price.computeCostCentsWithCache(
            uncachedInputTokens = 0,
            cacheReadTokens = 1_000_000,
            cacheWriteTokens = 0,
            outputTokens = 0,
        )
        cost shouldBe 30
    }

    @Test
    fun `cost premium cache writes at 125 percent of base input rate`() {
        val price = ModelPriceLookup.lookup(ProviderId("anthropic"), "anthropic/claude-sonnet-4.5")!!
        // 1_000_000 cache-write tokens at 125% of 300 = 375 cents.
        val cost = price.computeCostCentsWithCache(
            uncachedInputTokens = 0,
            cacheReadTokens = 0,
            cacheWriteTokens = 1_000_000,
            outputTokens = 0,
        )
        cost shouldBe 375
    }

    @Test
    fun `mixed cached + uncached + output sums correctly`() {
        val price = ModelPriceLookup.lookup(ProviderId("anthropic"), "anthropic/claude-sonnet-4.5")!!
        // 100k uncached input @ 300 cents/Mtok = 30 cents
        // 1M cache read @ 30 cents/Mtok = 30 cents
        // 100k cache write @ 375 cents/Mtok = 37 cents (truncated from 37.5)
        // 50k output @ 1500 cents/Mtok = 75 cents
        // Total = 30 + 30 + 37 + 75 = 172 cents.
        val cost = price.computeCostCentsWithCache(
            uncachedInputTokens = 100_000,
            cacheReadTokens = 1_000_000,
            cacheWriteTokens = 100_000,
            outputTokens = 50_000,
        )
        cost shouldBe 172
    }

    @Test
    fun `cache reads cost less than equivalent uncached input tokens (90 percent discount)`() {
        val price = ModelPriceLookup.lookup(ProviderId("anthropic"), "anthropic/claude-sonnet-4.5")!!
        val uncachedCost = price.computeCostCents(inputTokens = 1_000_000, outputTokens = 0)
        val cachedCost = price.computeCostCentsWithCache(
            uncachedInputTokens = 0,
            cacheReadTokens = 1_000_000,
            cacheWriteTokens = 0,
            outputTokens = 0,
        )
        uncachedCost shouldBe 300
        cachedCost shouldBe 30
        // Discount = 90% (10% paid).
        (cachedCost * 10) shouldBe uncachedCost
    }

    @Test
    fun `cache_control NOT emitted when payload below sonnet 1024-token minimum`() = runTest {
        var body = ""
        val provider = AnthropicProvider(
            client = mockClient({ body = it }, sonnetCachedResponse(read = 0, write = 0)),
            config = cfg,
        )
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "short payload")),
            systemPrompt = "you are nice",
            cacheControl = CacheControl.EPHEMERAL,
        )
        provider.call(req, "anthropic/claude-sonnet-4.5")
        body shouldNotContain "\"type\":\"ephemeral\""
    }

    @Test
    fun `end-to-end Router-level cost reflects cache discount when LlmResponse has cache read tokens`() = runTest {
        // Construct a fake Router with a ProviderCallable that returns an LlmResponse
        // whose costCents was already computed via computeCostCentsWithCache. The Router
        // is a passthrough for costCents — Plan-2's design folds cache math into the
        // provider, not the Router. This test pins that contract.
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to listOf(
                        LlmProvider.Anthropic(ProviderId("anthropic"), "anthropic/claude-sonnet-4.5"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("anthropic") to ProviderCallable { _, m ->
                    LlmResponse(
                        provider = ProviderId("anthropic"),
                        model = m,
                        text = "ok",
                        inputTokens = 100,
                        outputTokens = 50,
                        // Pre-computed: cache-read discount applied.
                        costCents = 8,
                        finishReason = FinishReason.STOP,
                        cacheReadTokens = 500_000,
                        cacheWriteTokens = 100_000,
                    )
                },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to 1_000)),
            auditLog = audit,
        )
        val resp = router.route(
            LlmRequest(
                subjectId = "victor",
                task = TaskType.TEXT,
                deviceClass = DeviceClass.VICTOR_DESKTOP,
                capability = Capability.NON_STREAMING,
                messages = listOf(LlmMessage(Role.USER, "hi")),
                cacheControl = CacheControl.EPHEMERAL,
            ),
        )
        resp.costCents shouldBe 8
        val row = audit.snapshot().first { it.kind == "llm_call" }
        row.costCents shouldBe 8
        row.extra["cache_read_tokens"] shouldBe "500000"
        row.extra["cache_write_tokens"] shouldBe "100000"
    }

    /**
     * Synthetic Anthropic JSON response with adjustable cache-read + cache-create counters.
     */
    private fun sonnetCachedResponse(read: Int, write: Int): String = """
        {
          "id":"msg_x",
          "model":"anthropic/claude-sonnet-4.5",
          "role":"assistant",
          "content":[{"type":"text","text":"ok"}],
          "stop_reason":"end_turn",
          "usage":{
            "input_tokens":50,
            "output_tokens":10,
            "cache_read_input_tokens":$read,
            "cache_creation_input_tokens":$write
          }
        }
    """.trimIndent()
}
