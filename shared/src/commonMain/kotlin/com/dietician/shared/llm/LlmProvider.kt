package com.dietician.shared.llm

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Closed sealed surface of provider variants understood by the Plan-2 LLM router.
 *
 * Council 1779038746-D2: `:shared:llm` ships as a package inside `:shared` (not a separate
 * Gradle subproject) so KMP target wiring stays single-source-of-truth.
 *
 * Each variant carries the provider-stable [id] (used in audit + routing chain configs) and
 * the model identifier as exchanged with the upstream API. Ollama is parameterized by
 * endpoint URL since users self-host on arbitrary ports. ClaudeMaxCli has no API key — it
 * shells out to the locally-installed `claude` CLI bound to the user's subscription.
 */
sealed interface LlmProvider {
    val id: ProviderId
    val model: String

    data class OpenRouter(override val id: ProviderId, override val model: String) : LlmProvider

    data class Anthropic(override val id: ProviderId, override val model: String) : LlmProvider

    data class Gemini(override val id: ProviderId, override val model: String) : LlmProvider

    data class Groq(override val id: ProviderId, override val model: String) : LlmProvider

    data class Ollama(
        override val id: ProviderId,
        override val model: String,
        val endpoint: String,
    ) : LlmProvider

    data class ClaudeMaxCli(override val id: ProviderId, override val model: String) : LlmProvider
}

/**
 * Stable provider identifier — must match `[a-z][a-z0-9-]*` so it can be embedded in audit
 * rows, log lines, and JSON config keys without escaping. Validation runs at construction.
 */
@Serializable
@JvmInline
value class ProviderId(val raw: String) {
    init {
        require(raw.matches(Regex("[a-z][a-z0-9-]*"))) { "invalid provider id: $raw" }
    }
}
