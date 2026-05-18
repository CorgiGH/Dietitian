package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class LlmTypesTest {
    @Test
    fun `LlmRequest defaults match spec`() {
        val req = LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, "hi")),
        )
        req.attachments shouldBe emptyList()
        req.maxOutputTokens shouldBe 4096
        req.temperature shouldBe 0.7
        req.systemPrompt shouldBe null
        req.cacheControl shouldBe CacheControl.NONE
    }

    @Test
    fun `AttachmentRef variants are exhaustive`() {
        val bytes = AttachmentRef.Bytes(byteArrayOf(1, 2, 3))
        val filePath = AttachmentRef.FilePath("/tmp/x.png")
        val url = AttachmentRef.Url("https://example.com/x.png")
        bytes.shouldBeInstanceOf<AttachmentRef.Bytes>()
        filePath.shouldBeInstanceOf<AttachmentRef.FilePath>()
        url.shouldBeInstanceOf<AttachmentRef.Url>()
    }

    @Test
    fun `LlmResponse cache token defaults zero`() {
        val r = LlmResponse(
            provider = ProviderId("openrouter"),
            model = "anthropic/claude-sonnet-4.5",
            text = "ok",
            inputTokens = 10,
            outputTokens = 5,
            costCents = 2,
            finishReason = FinishReason.STOP,
        )
        r.cacheReadTokens shouldBe 0
        r.cacheWriteTokens shouldBe 0
    }

    @Test
    fun `LlmError hierarchy is sealed and carries data`() {
        val rate = LlmError.RateLimitExceeded(retryAfterMs = 250)
        rate.retryAfterMs shouldBe 250L

        val budget = LlmError.BudgetExhausted(ProviderId("openrouter"))
        budget.provider.raw shouldBe "openrouter"

        val timeout = LlmError.Timeout(phase = "connect")
        timeout.phase shouldBe "connect"

        val transient = LlmError.TransientFailure(RuntimeException("boom"))
        transient.cause?.message shouldBe "boom"

        val filtered = LlmError.ContentFiltered("safety:violence")
        filtered.reason shouldBe "safety:violence"

        val unavail = LlmError.ProviderUnavailable(ProviderId("groq"))
        unavail.provider.raw shouldBe "groq"
    }

    @Test
    fun `LlmError is-checks drive policy table exhaustively`() {
        val errs: List<LlmError> = listOf(
            LlmError.RateLimitExceeded(null),
            LlmError.BudgetExhausted(ProviderId("openrouter")),
            LlmError.Timeout("call"),
            LlmError.TransientFailure(RuntimeException("net")),
            LlmError.PermanentFailure(RuntimeException("400")),
            LlmError.ContentFiltered("safety"),
            LlmError.ProviderUnavailable(ProviderId("groq")),
        )
        val classified = errs.map {
            when (it) {
                is LlmError.RateLimitExceeded -> "retry-later"
                is LlmError.BudgetExhausted -> "stop"
                is LlmError.Timeout -> "failover"
                is LlmError.TransientFailure -> "failover"
                is LlmError.PermanentFailure -> "stop"
                is LlmError.ContentFiltered -> "stop"
                is LlmError.ProviderUnavailable -> "failover"
            }
        }
        classified shouldBe listOf("retry-later", "stop", "failover", "failover", "stop", "stop", "failover")
    }
}
