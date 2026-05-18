package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ModelPriceLookupTest {
    @Test
    fun `lookup returns price for known model`() {
        val p = ModelPriceLookup.lookup(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5")
        p shouldBe Price(inputPerMTok = 300, outputPerMTok = 1500)
    }

    @Test
    fun `lookup returns null for unknown model`() {
        ModelPriceLookup.lookup(ProviderId("openrouter"), "nonexistent/model") shouldBe null
    }

    @Test
    fun `all required council-chain models are present`() {
        listOf(
            "anthropic/claude-sonnet-4.5",
            "anthropic/claude-3.5-haiku",
            "google/gemini-2.5-pro",
            "google/gemini-2.5-flash",
            "meta-llama/llama-3.3-70b-instruct",
            "groq/llama-3.3-70b-versatile",
            "voyage/voyage-4-lite",
        ).forEach {
            ModelPriceLookup.lookup(ProviderId("openrouter"), it) shouldBe ModelPriceLookup.all()[it]
            ModelPriceLookup.all()[it] shouldBe ModelPriceLookup.all()[it]
            (ModelPriceLookup.all()[it] != null) shouldBe true
        }
    }

    @Test
    fun `computeCostCents truncates per integer math contract`() {
        // 1_000_000 input * 300 cents/M = 300 cents exactly
        Price(300, 1500).computeCostCents(1_000_000, 0) shouldBe 300
        // 500k input * 300 = 150
        Price(300, 1500).computeCostCents(500_000, 0) shouldBe 150
        // 100 input + 100 output * voyage(2,0) → both sub-cent → 0
        Price(2, 0).computeCostCents(100, 100) shouldBe 0
    }

    @Test
    fun `computeCostCents handles large counts without overflow`() {
        // 100M input * 300 = 30000 (well under Int.MAX_VALUE)
        Price(300, 1500).computeCostCents(100_000_000, 0) shouldBe 30_000
    }

    @Test
    fun `computeCostCents rejects negative inputs`() {
        shouldThrow<IllegalArgumentException> { Price(300, 1500).computeCostCents(-1, 0) }
        shouldThrow<IllegalArgumentException> { Price(300, 1500).computeCostCents(0, -1) }
    }

    @Test
    fun `groq llama-3-3-70b cheapest moderation`() {
        // Council RC4 — Groq is moderation primary; price must be < OpenRouter haiku
        val groq = ModelPriceLookup.lookup(ProviderId("groq"), "groq/llama-3.3-70b-versatile")!!
        val haiku = ModelPriceLookup.lookup(ProviderId("openrouter"), "anthropic/claude-3.5-haiku")!!
        // groq.inputPerMTok < haiku.inputPerMTok asserted
        (groq.inputPerMTok < haiku.inputPerMTok) shouldBe true
        (groq.outputPerMTok < haiku.outputPerMTok) shouldBe true
    }
}
