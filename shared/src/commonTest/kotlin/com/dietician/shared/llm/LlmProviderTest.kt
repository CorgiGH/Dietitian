package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LlmProviderTest {
    @Test
    fun `ProviderId accepts lowercase alphanumeric with hyphen`() {
        ProviderId("openrouter").raw shouldBe "openrouter"
        ProviderId("claudemax-cli").raw shouldBe "claudemax-cli"
        ProviderId("groq").raw shouldBe "groq"
        ProviderId("a1").raw shouldBe "a1"
    }

    @Test
    fun `ProviderId rejects uppercase`() {
        shouldThrow<IllegalArgumentException> { ProviderId("OpenRouter") }
    }

    @Test
    fun `ProviderId rejects leading digit`() {
        shouldThrow<IllegalArgumentException> { ProviderId("1openrouter") }
    }

    @Test
    fun `ProviderId rejects empty`() {
        shouldThrow<IllegalArgumentException> { ProviderId("") }
    }

    @Test
    fun `ProviderId rejects underscore or dot`() {
        shouldThrow<IllegalArgumentException> { ProviderId("open_router") }
        shouldThrow<IllegalArgumentException> { ProviderId("open.router") }
    }

    @Test
    fun `LlmProvider variants carry id and model`() {
        val openrouter = LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5")
        openrouter.id.raw shouldBe "openrouter"
        openrouter.model shouldBe "anthropic/claude-sonnet-4.5"

        val ollama = LlmProvider.Ollama(ProviderId("ollama"), "llama3.1:8b", "http://localhost:11434")
        ollama.endpoint shouldBe "http://localhost:11434"

        val cli = LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest")
        cli.id.raw shouldBe "claudemax-cli"
    }

    @Test
    fun `ProviderConfig allows null apiKey for ClaudeMaxCli`() {
        val cfg = ProviderConfig(apiKey = null, baseUrl = "n/a", timeouts = Timeouts())
        cfg.apiKey shouldBe null
        cfg.maxRetries shouldBe 3
    }

    @Test
    fun `Timeouts defaults match spec envelope`() {
        val t = Timeouts()
        t.connectMs shouldBe 5_000L
        t.readMs shouldBe 30_000L
        t.callMs shouldBe 60_000L
    }
}
