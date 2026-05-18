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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * OpenRouter chat-completions adapter — Plan-2 Task 10.
 *
 * RC1 (Council 1779062699): `request.attachments` is wired into per-message multimodal content
 * blocks using the OpenAI-compat `image_url` shape. When attachments are present on a USER
 * message we emit `[{type:"text",text:...}, {type:"image_url",image_url:{url:"data:<mime>;base64,<b64>"}}, ...]`
 * rather than discarding the attachments. MockEngine test asserts the encoded body.
 *
 * Cost finalize: provider does NOT trust upstream usage.cost (when present) — instead, we
 * recompute via [ModelPriceLookup] using integer cents-per-million-tokens so that the audit row
 * + Plan-3 `BudgetLedger.consume_or_fail` decrement use the same canonical value.
 */
class OpenRouterProvider(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val providerId: ProviderId = ProviderId("openrouter"),
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun call(request: LlmRequest, model: String): LlmResponse {
        val body = buildOpenRouterRequest(request, model)
        val response: OpenRouterResponse = client.post("${config.baseUrl}/chat/completions") {
            header("Authorization", "Bearer ${config.apiKey ?: ""}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        return toLlmResponse(response, model)
    }

    internal fun buildOpenRouterRequest(request: LlmRequest, model: String): OpenRouterRequest {
        val messages = mutableListOf<OpenRouterMessage>()
        request.systemPrompt?.let { sys ->
            messages.add(OpenRouterMessage(role = "system", content = JsonPrimitive(sys)))
        }
        request.messages.forEach { msg ->
            val content: JsonElement = if (msg.role == Role.USER && request.attachments.isNotEmpty()) {
                val parts: List<OpenRouterContentPart> = buildList {
                    add(OpenRouterContentPart.Text(msg.content))
                    request.attachments.forEach { att ->
                        add(
                            OpenRouterContentPart.ImageUrl(
                                image_url = ImageUrlValue(
                                    url = "data:${att.mimeType};base64,${AttachmentEncoding.base64(att)}",
                                ),
                            ),
                        )
                    }
                }
                json.encodeToJsonElement(parts)
            } else {
                JsonPrimitive(msg.content)
            }
            messages.add(OpenRouterMessage(role = msg.role.name.lowercase(), content = content))
        }
        return OpenRouterRequest(
            model = model,
            messages = messages,
            max_tokens = request.maxOutputTokens,
            temperature = request.temperature,
            stream = false,
        )
    }

    private fun toLlmResponse(resp: OpenRouterResponse, model: String): LlmResponse {
        val choice = resp.choices.firstOrNull()
            ?: error("OpenRouter response missing choices for model=$model")
        val price = ModelPriceLookup.lookup(providerId, model)
        val cost = price?.computeCostCents(resp.usage.prompt_tokens, resp.usage.completion_tokens) ?: 0
        return LlmResponse(
            provider = providerId,
            model = resp.model.ifBlank { model },
            text = choice.message.content,
            inputTokens = resp.usage.prompt_tokens,
            outputTokens = resp.usage.completion_tokens,
            costCents = cost,
            finishReason = mapFinishReason(choice.finish_reason),
        )
    }

    private fun mapFinishReason(raw: String?): FinishReason = when (raw) {
        "stop", "end_turn", null -> FinishReason.STOP
        "length", "max_tokens" -> FinishReason.MAX_TOKENS
        "content_filter" -> FinishReason.CONTENT_FILTER
        "tool_use", "tool_calls" -> FinishReason.TOOL_USE
        else -> FinishReason.STOP
    }
}
