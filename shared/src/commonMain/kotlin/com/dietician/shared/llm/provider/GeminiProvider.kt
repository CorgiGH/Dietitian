package com.dietician.shared.llm.provider

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ModelPriceLookup
import com.dietician.shared.llm.ProviderConfig
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Role
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Gemini direct-API adapter — Plan-2 Task 12.
 *
 * RC1 (Council 1779062699): vision attachments → `inline_data` parts with `mime_type` + base64.
 *
 * Gemini's auth model is `?key=<APIKEY>` query parameter rather than a bearer header.
 */
class GeminiProvider(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val providerId: ProviderId = ProviderId("gemini"),
) {
    suspend fun call(request: LlmRequest, model: String): LlmResponse {
        val body = buildGeminiRequest(request)
        val response: GeminiResponse = client.post("${config.baseUrl}/v1beta/models/$model:generateContent") {
            parameter("key", config.apiKey ?: "")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        return toLlmResponse(response, model)
    }

    internal fun buildGeminiRequest(request: LlmRequest): GeminiRequest {
        val contents = request.messages.filter { it.role != Role.SYSTEM }.map { msg ->
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "model"
                Role.SYSTEM -> "user" // unreachable — filtered above
            }
            val isUserWithAttachments = msg.role == Role.USER && request.attachments.isNotEmpty()
            val parts: List<GeminiPart> = if (isUserWithAttachments) {
                buildList {
                    request.attachments.forEach { att ->
                        add(
                            GeminiPart(
                                inline_data = GeminiInlineData(
                                    mime_type = att.mimeType,
                                    data = AttachmentEncoding.base64(att),
                                ),
                            ),
                        )
                    }
                    add(GeminiPart(text = msg.content))
                }
            } else {
                listOf(GeminiPart(text = msg.content))
            }
            GeminiContent(role = role, parts = parts)
        }
        val systemFromMessages = request.messages.filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val systemText = request.systemPrompt ?: systemFromMessages
        val systemInstruction = systemText?.let {
            GeminiContent(role = "system", parts = listOf(GeminiPart(text = it)))
        }
        return GeminiRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = GeminiGenerationConfig(
                temperature = request.temperature,
                maxOutputTokens = request.maxOutputTokens,
            ),
        )
    }

    private fun toLlmResponse(resp: GeminiResponse, model: String): LlmResponse {
        val candidate = resp.candidates.firstOrNull()
        val text = candidate?.content?.parts?.mapNotNull { it.text }?.joinToString("") ?: ""
        val price = ModelPriceLookup.lookup(providerId, model)
        val cost = price?.computeCostCents(
            resp.usageMetadata.promptTokenCount,
            resp.usageMetadata.candidatesTokenCount,
        ) ?: 0
        return LlmResponse(
            provider = providerId,
            model = resp.modelVersion.ifBlank { model },
            text = text,
            inputTokens = resp.usageMetadata.promptTokenCount,
            outputTokens = resp.usageMetadata.candidatesTokenCount,
            costCents = cost,
            finishReason = mapFinishReason(candidate?.finishReason),
        )
    }

    private fun mapFinishReason(raw: String?): FinishReason = when (raw) {
        "STOP", null -> FinishReason.STOP
        "MAX_TOKENS" -> FinishReason.MAX_TOKENS
        "SAFETY", "RECITATION", "BLOCKLIST" -> FinishReason.CONTENT_FILTER
        else -> FinishReason.STOP
    }
}
