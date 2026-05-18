package com.dietician.shared.llm.provider

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Anthropic `/v1/messages` request envelope. Anthropic separates the `system` prompt from
 * the messages list and demands typed content blocks (text + image) on every message.
 *
 * Plan-2 Task 25: [system] is typed as [JsonElement] so it can carry either a string
 * (non-cached path) OR an array of [AnthropicSystemBlock] objects (cache_control path).
 * Anthropic accepts both shapes; the discriminant lives in the provider serializer.
 */
@Serializable
data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<AnthropicRequestMessage>,
    val system: JsonElement? = null,
    val temperature: Double = 0.7,
    val stream: Boolean = false,
)

/**
 * System-block with optional `cache_control` for prompt caching (Plan-2 Task 25).
 *
 * When [cache_control] is non-null, Anthropic caches the prefix up to and including this
 * block. Subsequent requests sharing the same prefix get charged at the discounted
 * cache-read rate (~90% off input tokens). Minimum cacheable size: 1024 tokens for
 * Sonnet, 2048 for Haiku — caller MUST gate cache_control assignment on long-enough
 * payloads.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AnthropicSystemBlock(
    val type: String = "text",
    val text: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cache_control: AnthropicCacheControl? = null,
)

/**
 * Anthropic cache-control marker. Only "ephemeral" is supported by the API as of
 * 2026-05. 5-minute TTL window.
 */
@Serializable
data class AnthropicCacheControl(val type: String = "ephemeral")

@Serializable
data class AnthropicRequestMessage(
    val role: String,
    val content: List<AnthropicRequestContentBlock>,
)

/**
 * RC1 (Council 1779062699): Anthropic vision shape — `Image` block with base64 source +
 * media_type. `Text` block carries the prompt. Sealed polymorphism with `type` as the
 * discriminator (default) — do NOT redeclare `type` as a property (conflicts with discriminator).
 *
 * Plan-2 Task 25: optional `cache_control` field on each block enables Anthropic prompt
 * caching when the AnthropicProvider determines the payload exceeds the minimum cacheable
 * size (1024 tokens Sonnet / 2048 Haiku) and the LlmRequest opts in via
 * [com.dietician.shared.llm.CacheControl.EPHEMERAL].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed class AnthropicRequestContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val cache_control: AnthropicCacheControl? = null,
    ) : AnthropicRequestContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(
        val source: AnthropicImageSource,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val cache_control: AnthropicCacheControl? = null,
    ) : AnthropicRequestContentBlock()
}

@Serializable
data class AnthropicImageSource(
    val type: String = "base64",
    val media_type: String,
    val data: String,
)

@Serializable
data class AnthropicResponse(
    val id: String = "",
    val model: String = "",
    val role: String = "assistant",
    val content: List<AnthropicResponseContentBlock> = emptyList(),
    val stop_reason: String? = null,
    val usage: AnthropicResponseUsage = AnthropicResponseUsage(),
)

/**
 * Response content blocks — Anthropic returns `text` blocks (and `tool_use` for tool calls,
 * unused in Plan-2 batch B). We treat unknown variants as empty text.
 */
@Serializable
data class AnthropicResponseContentBlock(
    val type: String = "text",
    val text: String = "",
)

/**
 * Includes prompt-caching observability fields per Plan-2 RC10 (cache_read_input_tokens +
 * cache_creation_input_tokens). Defaults to 0 when the API omits them (non-cached requests).
 */
@Serializable
data class AnthropicResponseUsage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0,
    val cache_read_input_tokens: Int = 0,
    val cache_creation_input_tokens: Int = 0,
)
