package com.dietician.shared.llm

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class RouterConfigTest {
    @Test
    fun `VICTOR_DESKTOP_TEXT shape is openrouter-sonnet -- openrouter-gemini-2-5-pro -- claudemax-cli`() {
        val chain = DefaultRouterConfig.VICTOR_DESKTOP_TEXT
        chain.size shouldBe 3

        val first = chain[0]
        first.shouldBeInstanceOf<LlmProvider.OpenRouter>()
        first.id.raw shouldBe "openrouter"
        first.model shouldBe "anthropic/claude-sonnet-4.5"

        val second = chain[1]
        second.shouldBeInstanceOf<LlmProvider.OpenRouter>()
        second.model shouldBe "google/gemini-2.5-pro"

        val third = chain[2]
        third.shouldBeInstanceOf<LlmProvider.ClaudeMaxCli>()
        third.id.raw shouldBe "claudemax-cli"
    }

    @Test
    fun `VICTOR_DESKTOP_MODERATION starts with groq llama-3-3-70b`() {
        val chain = DefaultRouterConfig.VICTOR_DESKTOP_MODERATION
        chain.shouldNotBeEmpty()
        val first = chain[0]
        first.shouldBeInstanceOf<LlmProvider.Groq>()
        first.model shouldBe "llama-3.3-70b-versatile"
    }

    @Test
    fun `FRIEND_MODERATION shares VICTOR_DESKTOP_MODERATION chain identity`() {
        // RC4 explicit: friend moderation reuses VICTOR_DESKTOP_MODERATION verbatim.
        DefaultRouterConfig.FRIEND_MODERATION shouldBe DefaultRouterConfig.VICTOR_DESKTOP_MODERATION
    }

    @Test
    fun `FRIEND_PHONE_TEXT first slot is openrouter llama-3-3-70b-instruct`() {
        val first = DefaultRouterConfig.FRIEND_PHONE_TEXT[0]
        first.shouldBeInstanceOf<LlmProvider.OpenRouter>()
        first.model shouldBe "meta-llama/llama-3.3-70b-instruct"
    }

    @Test
    fun `SERVER_EMBEDDING uses voyage-4-lite via openrouter`() {
        val chain = DefaultRouterConfig.SERVER_EMBEDDING
        chain.size shouldBe 1
        chain[0].model shouldBe "voyage/voyage-4-lite"
    }

    @Test
    fun `all default chains are non-empty`() {
        DefaultRouterConfig.default.chains.values.forEach { it.shouldNotBeEmpty() }
    }

    @Test
    fun `default config keys cover all council-named routes`() {
        val keys = DefaultRouterConfig.default.chains.keys
        ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) shouldBeIn keys
        ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.MODERATION) shouldBeIn keys
        ChainKey(DeviceClass.FRIEND_PHONE, TaskType.TEXT) shouldBeIn keys
        ChainKey(DeviceClass.FRIEND_PHONE, TaskType.MODERATION) shouldBeIn keys
        ChainKey(DeviceClass.SERVER, TaskType.EMBEDDING) shouldBeIn keys
    }

    @Test
    fun `every model in council chains has a price entry`() {
        DefaultRouterConfig.default.chains.values.flatten().forEach { provider ->
            // ClaudeMaxCli has no price entry (subscription bundle — no per-token cost).
            if (provider is LlmProvider.ClaudeMaxCli) return@forEach
            val maybe = ModelPriceLookup.lookup(provider.id, provider.model)
            (maybe != null) shouldBe true
        }
    }
}
