package com.dietician.shared.llm.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anthropic `/v1/messages` request envelope. Anthropic separates the `system` prompt from
 * the messages list and demands typed content blocks (text + image) on every message.
 */
@Serializable
data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<AnthropicRequestMessage>,
    val system: String? = null,
    val temperature: Double = 0.7,
    val stream: Boolean = false,
)

@Serializable
data class AnthropicRequestMessage(
    val role: String,
    val content: List<AnthropicRequestContentBlock>,
)

/**
 * RC1 (Council 1779062699): Anthropic vision shape — `Image` block with base64 source +
 * media_type. `Text` block carries the prompt. Sealed polymorphism with `type` as the
 * discriminator (default) — do NOT redeclare `type` as a property (conflicts with discriminator).
 */
@Serializable
sealed class AnthropicRequestContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicRequestContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(val source: AnthropicImageSource) : AnthropicRequestContentBlock()
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
