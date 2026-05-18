package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VisionTest {
    private val subject = "victor"

    private fun routerReturning(responseText: String): Pair<LlmRouter, InMemoryAuditLogSink> {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.VISION) to listOf(
                        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, model ->
                    LlmResponse(
                        provider = ProviderId("openrouter"),
                        model = model,
                        text = responseText,
                        inputTokens = 1500,
                        outputTokens = 200,
                        costCents = 8,
                        finishReason = FinishReason.STOP,
                    )
                },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        return router to audit
    }

    private val sampleReceiptJson = """
        {
          "store": "Mega Image",
          "date": "2026-05-18",
          "items": [
            {"name": "Paine integrala", "qty": 1, "unit_price_cents": 850, "total_cents": 850},
            {"name": "Lapte 1l", "qty": 2, "unit_price_cents": 720, "total_cents": 1440},
            {"name": "Garantie ambalaj", "qty": 1, "unit_price_cents": 50, "total_cents": 50}
          ],
          "total_cents": 2340,
          "currency": "RON"
        }
    """.trimIndent()

    @Test
    fun `parseReceipt extracts structured receipt from vision response`() = runTest {
        val (router, _) = routerReturning(sampleReceiptJson)
        val vision = Vision(router)
        val receipt = vision.parseReceipt(
            image = AttachmentRef.Bytes(byteArrayOf(1, 2, 3, 4)),
            hint = "Receipt from Mega Image",
            subjectId = subject,
        )
        receipt.store shouldBe "Mega Image"
        receipt.date shouldBe "2026-05-18"
        receipt.items.size shouldBe 3
        receipt.items[0].name shouldBe "Paine integrala"
        receipt.items[0].unitPriceCents shouldBe 850
        receipt.items[2].name shouldBe "Garantie ambalaj"
        receipt.totalCents shouldBe 2340
        receipt.currency shouldBe "RON"
    }

    @Test
    fun `parseReceipt strips markdown-fenced JSON`() = runTest {
        val fenced = "```json\n$sampleReceiptJson\n```"
        val (router, _) = routerReturning(fenced)
        val vision = Vision(router)
        val receipt = vision.parseReceipt(
            image = AttachmentRef.Bytes(byteArrayOf(9)),
            hint = "fenced output",
            subjectId = subject,
        )
        receipt.store shouldBe "Mega Image"
        receipt.items.size shouldBe 3
    }

    @Test
    fun `parseReceipt throws PermanentFailure on unparseable response`() = runTest {
        val (router, _) = routerReturning("Sorry, I cannot read this image clearly.")
        val vision = Vision(router)
        shouldThrow<LlmError.PermanentFailure> {
            vision.parseReceipt(AttachmentRef.Bytes(byteArrayOf(0)), "noisy photo", subject)
        }
    }

    @Test
    fun `parseReceipt routes via VISION task and writes llm_call audit row`() = runTest {
        val (router, audit) = routerReturning(sampleReceiptJson)
        val vision = Vision(router)
        vision.parseReceipt(
            image = AttachmentRef.Url("https://example.test/receipt.jpg"),
            hint = "from URL",
            subjectId = subject,
        )
        val row = audit.snapshot().first { it.kind == "llm_call" }
        row.extra["task"] shouldBe "VISION"
        row.extra["device_class"] shouldBe "VICTOR_DESKTOP"
    }

    @Test
    fun `parseReceiptJson handles ReceiptItem defaults`() {
        val (router, _) = routerReturning("{}")
        val vision = Vision(router)
        val json = """
            {"store":"X","date":"2026-01-01","items":[{"name":"a","unit_price_cents":100,"total_cents":100}],"total_cents":100}
        """.trimIndent()
        val r = vision.parseReceiptJson(json)
        // qty default = 1, currency default = "RON".
        r.items[0].qty shouldBe 1
        r.currency shouldBe "RON"
    }

    @Test
    fun `friend phone deviceClass routes via FRIEND chain`() = runTest {
        val audit = InMemoryAuditLogSink()
        val router = LlmRouter(
            config = RouterConfig(
                chains = mapOf(
                    ChainKey(DeviceClass.FRIEND_PHONE, TaskType.VISION) to listOf(
                        LlmProvider.OpenRouter(ProviderId("openrouter"), "google/gemini-2.5-flash"),
                    ),
                ),
            ),
            providers = mapOf(
                ProviderId("openrouter") to ProviderCallable { _, model ->
                    LlmResponse(
                        provider = ProviderId("openrouter"),
                        model = model,
                        text = sampleReceiptJson,
                        inputTokens = 800,
                        outputTokens = 200,
                        costCents = 1,
                        finishReason = FinishReason.STOP,
                    )
                },
            ),
            cache = IdempotencyCache(),
            budget = InMemoryBudgetLedger(),
            auditLog = audit,
        )
        val vision = Vision(router)
        vision.parseReceipt(
            image = AttachmentRef.FilePath("/tmp/receipt.jpg"),
            hint = "friend's receipt",
            subjectId = subject,
            deviceClass = DeviceClass.FRIEND_PHONE,
        )
        val row = audit.snapshot().first { it.kind == "llm_call" }
        row.extra["device_class"] shouldBe "FRIEND_PHONE"
        row.model!!.contains("gemini-2.5-flash") shouldBe true
    }
}
