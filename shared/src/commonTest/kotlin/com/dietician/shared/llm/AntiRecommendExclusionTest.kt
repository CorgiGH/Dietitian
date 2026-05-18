package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Plan-2 Task 27 — anti-recommend provider/model exclusion.
 *
 * Filter unit tests + Router integration tests:
 *   - subject-scoped exclusion removes only that subject's banned model.
 *   - global ("*") exclusion applies to every subject.
 *   - subject + global exclusions UNION (merged set).
 *   - empty post-filter chain → ProviderUnavailable + `llm_call_all_excluded` audit row.
 *   - default (EMPTY config) is a pass-through.
 */
class AntiRecommendExclusionTest {

    private val chain = listOf<LlmProvider>(
        LlmProvider.OpenRouter(ProviderId("openrouter"), "openai/gpt-4o"),
        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
        LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
    )

    @Test
    fun `default empty config is pass-through`() {
        val ex = AntiRecommendExclusion(AntiRecommendExclusionConfig.EMPTY)
        ex.filter(chain, "friend-vegan") shouldBe chain
    }

    @Test
    fun `subject-scoped exclusion removes only that model and only for that subject`() {
        val cfg = AntiRecommendExclusionConfig(
            exclusionsByScope = mapOf(
                "friend-vegan" to setOf(ProviderModelKey(ProviderId("openrouter"), "openai/gpt-4o")),
            ),
        )
        val ex = AntiRecommendExclusion(cfg)
        val veganFiltered = ex.filter(chain, "friend-vegan")
        veganFiltered shouldHaveSize 2
        veganFiltered.map { it.model } shouldContain "anthropic/claude-sonnet-4.5"
        veganFiltered.map { it.model } shouldContain "claude-3-5-sonnet-latest"

        // Other subject sees the full chain.
        ex.filter(chain, "victor") shouldBe chain
    }

    @Test
    fun `global star exclusion applies to every subject`() {
        val cfg = AntiRecommendExclusionConfig(
            exclusionsByScope = mapOf(
                "*" to setOf(ProviderModelKey(ProviderId("openrouter"), "openai/gpt-4o")),
            ),
        )
        val ex = AntiRecommendExclusion(cfg)
        ex.filter(chain, "anyone").map { it.model } shouldBe listOf(
            "anthropic/claude-sonnet-4.5",
            "claude-3-5-sonnet-latest",
        )
        ex.filter(chain, "victor").map { it.model } shouldBe listOf(
            "anthropic/claude-sonnet-4.5",
            "claude-3-5-sonnet-latest",
        )
    }

    @Test
    fun `subject and global exclusions are unioned`() {
        val cfg = AntiRecommendExclusionConfig(
            exclusionsByScope = mapOf(
                "*" to setOf(ProviderModelKey(ProviderId("openrouter"), "openai/gpt-4o")),
                "friend-vegan" to setOf(ProviderModelKey(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest")),
            ),
        )
        val ex = AntiRecommendExclusion(cfg)
        ex.filter(chain, "friend-vegan").map { it.model } shouldBe listOf("anthropic/claude-sonnet-4.5")
    }

    // ---------------------------------------------------------------------------
    // Router integration — middle entry excluded → only first + last tried
    // ---------------------------------------------------------------------------

    @Test
    fun `Router applies exclusion before chain iteration`() = runTest {
        val audit = InMemoryAuditLogSink()
        val budget = InMemoryBudgetLedger()
        val cfg = AntiRecommendExclusionConfig(
            exclusionsByScope = mapOf(
                "victor" to setOf(ProviderModelKey(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5")),
            ),
        )
        var openRouterCalls = 0
        var claudemaxCalls = 0
        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to listOf(
                        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                        LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ ->
                    openRouterCalls++
                    error("excluded — should never run")
                },
                ProviderId("claudemax-cli") to ProviderCallable { _, m ->
                    claudemaxCalls++
                    LlmResponse(
                        provider = ProviderId("claudemax-cli"),
                        model = m,
                        text = "ok",
                        inputTokens = 1,
                        outputTokens = 1,
                        costCents = 1,
                        finishReason = FinishReason.STOP,
                    )
                },
            ),
            cache = IdempotencyCache(),
            budget = budget,
            auditLog = audit,
            antiRecommend = AntiRecommendExclusion(cfg),
        )
        val resp = router.route(
            LlmRequest(
                subjectId = "victor",
                task = TaskType.TEXT,
                deviceClass = DeviceClass.VICTOR_DESKTOP,
                capability = Capability.NON_STREAMING,
                messages = listOf(LlmMessage(Role.USER, "hi")),
            ),
        )
        resp.text shouldBe "ok"
        openRouterCalls shouldBe 0
        claudemaxCalls shouldBe 1
    }

    @Test
    fun `Router throws ProviderUnavailable and writes llm_call_all_excluded when filter empties chain`() = runTest {
        val audit = InMemoryAuditLogSink()
        val cfg = AntiRecommendExclusionConfig(
            exclusionsByScope = mapOf(
                "*" to setOf(
                    ProviderModelKey(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                    ProviderModelKey(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
                ),
            ),
        )
        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to listOf(
                        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                        LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, _ -> error("never") },
                ProviderId("claudemax-cli") to ProviderCallable { _, _ -> error("never") },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
            antiRecommend = AntiRecommendExclusion(cfg),
        )
        shouldThrow<LlmError.ProviderUnavailable> {
            router.route(
                LlmRequest(
                    subjectId = "victor",
                    task = TaskType.TEXT,
                    deviceClass = DeviceClass.VICTOR_DESKTOP,
                    capability = Capability.NON_STREAMING,
                    messages = listOf(LlmMessage(Role.USER, "hi")),
                ),
            )
        }
        val rows = audit.snapshot()
        val excluded = rows.first { it.kind == "llm_call_all_excluded" }
        excluded.extra["raw_chain_size"] shouldBe "2"
        excluded.extra["device_class"] shouldBe "VICTOR_DESKTOP"
    }
}
