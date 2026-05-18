package com.dietician.shared.llm.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenRouter chat/completions request envelope. OpenAI-compatible shape — Groq uses the same
 * via [GroqProvider] which delegates here with a different baseUrl.
 *
 * `content` is a [JsonElement] so it can be either a plain string (text-only path) or a JSON
 * array of typed content parts (RC1 vision path).
 */
@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.7,
    val stream: Boolean = false,
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: JsonElement,
)

/**
 * RC1 (Council 1779062699): sealed polymorphic content-part — `type` discriminator is the
 * upstream JSON shape. OpenRouter accepts a heterogeneous content array on user messages so
 * `Text` + `ImageUrl` interleave in the same list when vision attachments are inlined.
 *
 * The kotlinx-serialization class discriminator field is `type` (the default). `@SerialName`
 * on each subclass sets the discriminator value. The discriminator is injected by the encoder
 * — we do NOT declare a `type` property explicitly (that would conflict with the discriminator
 * and throw IllegalStateException at encode time).
 */
@Serializable
sealed class OpenRouterContentPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : OpenRouterContentPart()

    @Serializable
    @SerialName("image_url")
    data class ImageUrl(val image_url: ImageUrlValue) : OpenRouterContentPart()
}

@Serializable
data class ImageUrlValue(val url: String)

@Serializable
data class OpenRouterResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: OpenRouterUsage = OpenRouterUsage(),
)

@Serializable
data class OpenRouterChoice(
    val index: Int = 0,
    val message: OpenRouterResponseMessage,
    val finish_reason: String? = null,
)

@Serializable
data class OpenRouterResponseMessage(val role: String, val content: String)

@Serializable
data class OpenRouterUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0,
)
