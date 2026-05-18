package com.dietician.shared.llm.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini `generateContent` request envelope. Gemini uses `contents[]` + `parts[]` rather than
 * Anthropic's `messages[]`. Each part is either a `text` field OR an `inline_data` field
 * (never both) — modeled as nullable fields rather than sealed polymorphism because Gemini's
 * JSON schema is field-presence-based (no discriminator).
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>,
)

/**
 * RC1 (Council 1779062699): Gemini vision part. Exactly one of [text] or [inline_data] is
 * non-null on any given part; the encoder + Gemini API both rely on field-presence to discriminate.
 */
@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inline_data: GeminiInlineData? = null,
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mime_type: String,
    val data: String,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata = GeminiUsageMetadata(),
    val modelVersion: String = "",
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
)
