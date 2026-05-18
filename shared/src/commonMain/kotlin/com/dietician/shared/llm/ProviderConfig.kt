package com.dietician.shared.llm

/**
 * Per-provider runtime configuration.
 *
 * [apiKey] is null only for ClaudeMaxCli (subscription-bound CLI; no header auth).
 * [baseUrl] is the HTTP base path the provider's Ktor client targets — overridable for
 * tests (`http://localhost:<MockEngine>`) and self-hosted Ollama.
 */
data class ProviderConfig(
    val apiKey: String?,
    val baseUrl: String,
    val timeouts: Timeouts,
    val maxRetries: Int = 3,
)

/**
 * Per-call timeout budget. [connectMs] socket-handshake, [readMs] each chunk wait,
 * [callMs] total wall-clock. Defaults match the Plan-2 spec §A14 envelope.
 */
data class Timeouts(
    val connectMs: Long = 5_000,
    val readMs: Long = 30_000,
    val callMs: Long = 60_000,
)
