package com.dietician.shared.llm

import kotlinx.serialization.Serializable

/**
 * Failover chain definition. The Router walks [chains] in array order until one provider
 * returns a non-failover [LlmError] (PermanentFailure / ContentFiltered) or a successful
 * [LlmResponse]. RateLimit / Timeout / Transient / ProviderUnavailable failures advance to
 * the next entry.
 *
 * Council 1779062699 RC3: VICTOR_DESKTOP_TEXT injects Gemini-2.5-Pro as middle slot,
 * giving sonnet-fallback diversity without single-vendor concentration risk.
 * Council 1779062699 RC4: VICTOR_DESKTOP_MODERATION + FRIEND_MODERATION lead with Groq
 * (14400 req/day free tier) and fall back to OpenRouter Claude 3.5 Haiku on outage.
 */
data class RouterConfig(val chains: Map<ChainKey, List<LlmProvider>>)

@Serializable
data class ChainKey(val deviceClass: DeviceClass, val task: TaskType)

object DefaultRouterConfig {
    val VICTOR_DESKTOP_TEXT: List<LlmProvider> = listOf(
        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-sonnet-4.5"),
        LlmProvider.OpenRouter(ProviderId("openrouter"), "google/gemini-2.5-pro"),
        LlmProvider.ClaudeMaxCli(ProviderId("claudemax-cli"), "claude-3-5-sonnet-latest"),
    )

    val VICTOR_DESKTOP_MODERATION: List<LlmProvider> = listOf(
        LlmProvider.Groq(ProviderId("groq"), "llama-3.3-70b-versatile"),
        LlmProvider.OpenRouter(ProviderId("openrouter"), "anthropic/claude-3.5-haiku"),
    )

    /** RC4: friend moderation reuses VICTOR_DESKTOP_MODERATION verbatim. */
    val FRIEND_MODERATION: List<LlmProvider> = VICTOR_DESKTOP_MODERATION

    val FRIEND_PHONE_TEXT: List<LlmProvider> = listOf(
        LlmProvider.OpenRouter(ProviderId("openrouter"), "meta-llama/llama-3.3-70b-instruct"),
        LlmProvider.OpenRouter(ProviderId("openrouter"), "google/gemini-2.5-flash"),
    )

    val SERVER_EMBEDDING: List<LlmProvider> = listOf(
        LlmProvider.OpenRouter(ProviderId("openrouter"), "voyage/voyage-4-lite"),
    )

    /** Council-baked default routing table. Loaded by Router at construction. */
    val default: RouterConfig = RouterConfig(
        mapOf(
            ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.TEXT) to VICTOR_DESKTOP_TEXT,
            ChainKey(DeviceClass.VICTOR_DESKTOP, TaskType.MODERATION) to VICTOR_DESKTOP_MODERATION,
            ChainKey(DeviceClass.FRIEND_PHONE, TaskType.MODERATION) to FRIEND_MODERATION,
            ChainKey(DeviceClass.FRIEND_PHONE, TaskType.TEXT) to FRIEND_PHONE_TEXT,
            ChainKey(DeviceClass.SERVER, TaskType.EMBEDDING) to SERVER_EMBEDDING,
        ),
    )
}
