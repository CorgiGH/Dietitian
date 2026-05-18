package com.dietician.shared.llm

import com.dietician.shared.llm.provider.AnthropicProvider
import com.dietician.shared.llm.provider.GeminiProvider
import com.dietician.shared.llm.provider.GroqProvider
import com.dietician.shared.llm.provider.OpenRouterProvider
import io.ktor.client.HttpClient

/**
 * Plan-2 Task 29 — per-subject provider builder using BYOK.
 *
 * Resolves the subject's BYOK key via [SubjectCredentialStore]; falls back to the
 * operator-provided default in [defaults] when the subject has no per-provider entry. The
 * resulting [ProviderConfig] is fed into a fresh provider instance per request — providers
 * are cheap stateless wrappers over the shared [HttpClient].
 *
 * Concurrency: stateless. The same [ProviderFactory] singleton serves arbitrary parallel
 * route() calls.
 *
 * Security: the decrypted key never lives in a `data class` field on this object — it
 * passes through method-local scope into the [ProviderConfig] held by the per-call
 * provider instance, then released for GC after the HTTP exchange.
 */
class ProviderFactory(
    private val baseClient: HttpClient,
    private val credentialStore: SubjectCredentialStore,
    private val defaults: ProviderConfigDefaults,
) {
    suspend fun openRouterFor(subjectId: String): OpenRouterProvider {
        val key = credentialStore.getKey(subjectId, ProviderId("openrouter")) ?: defaults.openRouterKey
        return OpenRouterProvider(
            client = baseClient,
            config = ProviderConfig(
                apiKey = key,
                baseUrl = defaults.openRouterBaseUrl,
                timeouts = defaults.timeouts,
            ),
        )
    }

    suspend fun anthropicFor(subjectId: String): AnthropicProvider {
        val key = credentialStore.getKey(subjectId, ProviderId("anthropic")) ?: defaults.anthropicKey
        return AnthropicProvider(
            client = baseClient,
            config = ProviderConfig(
                apiKey = key,
                baseUrl = defaults.anthropicBaseUrl,
                timeouts = defaults.timeouts,
            ),
        )
    }

    suspend fun geminiFor(subjectId: String): GeminiProvider {
        val key = credentialStore.getKey(subjectId, ProviderId("gemini")) ?: defaults.geminiKey
        return GeminiProvider(
            client = baseClient,
            config = ProviderConfig(
                apiKey = key,
                baseUrl = defaults.geminiBaseUrl,
                timeouts = defaults.timeouts,
            ),
        )
    }

    suspend fun groqFor(subjectId: String): GroqProvider {
        val key = credentialStore.getKey(subjectId, ProviderId("groq")) ?: defaults.groqKey
        return GroqProvider(
            client = baseClient,
            config = ProviderConfig(
                apiKey = key,
                baseUrl = defaults.groqBaseUrl,
                timeouts = defaults.timeouts,
            ),
        )
    }
}

/**
 * Operator-provided default keys + URLs per provider. Server-side production reads keys
 * from env vars at startup (OPENROUTER_API_KEY, ANTHROPIC_API_KEY, GEMINI_API_KEY,
 * GROQ_API_KEY) and fails fast if a key is missing for any provider in the default chain.
 *
 * Tests pass arbitrary stub values + MockEngine base URLs.
 */
data class ProviderConfigDefaults(
    val openRouterKey: String?,
    val openRouterBaseUrl: String = "https://openrouter.ai/api/v1",
    val anthropicKey: String?,
    // AnthropicProvider appends "/v1/messages" — base URL excludes the version segment.
    val anthropicBaseUrl: String = "https://api.anthropic.com",
    val geminiKey: String?,
    // GeminiProvider appends "/v1beta/models/<model>:generateContent" — base URL excludes the version segment.
    val geminiBaseUrl: String = "https://generativelanguage.googleapis.com",
    val groqKey: String?,
    val groqBaseUrl: String = "https://api.groq.com/openai/v1",
    val timeouts: Timeouts = Timeouts(),
)
