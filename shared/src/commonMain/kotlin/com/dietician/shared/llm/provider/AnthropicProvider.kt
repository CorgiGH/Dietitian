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

/**
 * Anthropic direct-API adapter — Plan-2 Task 11.
 *
 * RC1 (Council 1779062699): vision attachments emitted as `image` content blocks with
 * base64-encoded source + media_type. MockEngine test asserts the encoded body.
 *
 * RC10 (Council 1779062699): response `usage.cache_read_input_tokens` +
 * `usage.cache_creation_input_tokens` are surfaced into [LlmResponse.cacheReadTokens] +
 * [LlmResponse.cacheWriteTokens] so the audit row + Plan-2 Task 26 observability dashboard see
 * cache effectiveness. Plan-2 Task 25 wires `cache_control: ephemeral` system-prompt blocks.
 *
 * Cost finalize: same as OpenRouter — recompute via [ModelPriceLookup] so the audit row and
 * budget ledger agree.
 */
class AnthropicProvider(
    private val client: HttpClient,
    private val config: ProviderConfig,
    private val providerId: ProviderId = ProviderId("anthropic"),
    private val apiVersion: String = "2023-06-01",
) {
    suspend fun call(request: LlmRequest, model: String): LlmResponse {
        val body = buildAnthropicRequest(request, model)
        val response: AnthropicResponse = client.post("${config.baseUrl}/v1/messages") {
            header("x-api-key", config.apiKey ?: "")
            header("anthropic-version", apiVersion)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
        return toLlmResponse(response, model)
    }

    internal fun buildAnthropicRequest(request: LlmRequest, model: String): AnthropicRequest {
        val messages = request.messages.filter { it.role != Role.SYSTEM }.map { msg ->
            val isUserWithAttachments = msg.role == Role.USER && request.attachments.isNotEmpty()
            val blocks: List<AnthropicRequestContentBlock> = if (isUserWithAttachments) {
                buildList {
                    // Anthropic convention: images first, then text. The order matches their
                    // doc examples; LLM-side observed empirically to anchor on the first block.
                    request.attachments.forEach { att ->
                        add(
                            AnthropicRequestContentBlock.Image(
                                source = AnthropicImageSource(
                                    media_type = att.mimeType,
                                    data = AttachmentEncoding.base64(att),
                                ),
                            ),
                        )
                    }
                    add(AnthropicRequestContentBlock.Text(msg.content))
                }
            } else {
                listOf(AnthropicRequestContentBlock.Text(msg.content))
            }
            AnthropicRequestMessage(role = msg.role.name.lowercase(), content = blocks)
        }
        // Combine SYSTEM messages into Anthropic's top-level `system` field (Anthropic does NOT
        // accept role=system inside the messages array).
        val systemFromMessages = request.messages.filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val system = request.systemPrompt ?: systemFromMessages
        return AnthropicRequest(
            model = model,
            max_tokens = request.maxOutputTokens,
            messages = messages,
            system = system,
            temperature = request.temperature,
            stream = false,
        )
    }

    private fun toLlmResponse(resp: AnthropicResponse, model: String): LlmResponse {
        val text = resp.content.filter { it.type == "text" }.joinToString("") { it.text }
        val price = ModelPriceLookup.lookup(providerId, model)
        val cost = price?.computeCostCents(resp.usage.input_tokens, resp.usage.output_tokens) ?: 0
        return LlmResponse(
            provider = providerId,
            model = resp.model.ifBlank { model },
            text = text,
            inputTokens = resp.usage.input_tokens,
            outputTokens = resp.usage.output_tokens,
            costCents = cost,
            finishReason = mapStopReason(resp.stop_reason),
            cacheReadTokens = resp.usage.cache_read_input_tokens,
            cacheWriteTokens = resp.usage.cache_creation_input_tokens,
        )
    }

    private fun mapStopReason(raw: String?): FinishReason = when (raw) {
        "end_turn", "stop_sequence", null -> FinishReason.STOP
        "max_tokens" -> FinishReason.MAX_TOKENS
        "tool_use" -> FinishReason.TOOL_USE
        else -> FinishReason.STOP
    }
}
