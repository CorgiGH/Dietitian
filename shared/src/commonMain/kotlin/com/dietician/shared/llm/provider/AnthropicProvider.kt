package com.dietician.shared.llm.provider

import com.dietician.shared.llm.CacheControl
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

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
    private val json = Json { encodeDefaults = false }

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
        val applyCacheControl = request.cacheControl == CacheControl.EPHEMERAL &&
            shouldEnableCaching(request, model)

        val messageList = request.messages.filter { it.role != Role.SYSTEM }
        // Identify the LAST USER message index so we can stamp cache_control on its last
        // content block when caching is enabled. (Anthropic's caching guide: place
        // cache_control on the LAST block of the cached prefix.)
        val lastUserMessageIndex = messageList.indexOfLast { it.role == Role.USER }

        val messages = messageList.mapIndexed { idx, msg ->
            val isUserWithAttachments = msg.role == Role.USER && request.attachments.isNotEmpty()
            val isLastUserMessage = idx == lastUserMessageIndex && applyCacheControl
            val blocks: List<AnthropicRequestContentBlock> = if (isUserWithAttachments) {
                buildList {
                    // Anthropic convention: images first, then text.
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
                    add(
                        AnthropicRequestContentBlock.Text(
                            text = msg.content,
                            cache_control = if (isLastUserMessage) AnthropicCacheControl() else null,
                        ),
                    )
                }
            } else {
                listOf(
                    AnthropicRequestContentBlock.Text(
                        text = msg.content,
                        cache_control = if (isLastUserMessage) AnthropicCacheControl() else null,
                    ),
                )
            }
            AnthropicRequestMessage(role = msg.role.name.lowercase(), content = blocks)
        }

        // Combine SYSTEM messages into Anthropic's top-level `system` field.
        val systemFromMessages = request.messages.filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val systemText = request.systemPrompt ?: systemFromMessages

        val systemElement = when {
            systemText.isNullOrBlank() -> null
            applyCacheControl -> json.encodeToJsonElement(
                listOf(
                    AnthropicSystemBlock(
                        text = systemText,
                        cache_control = AnthropicCacheControl(),
                    ),
                ),
            )
            else -> JsonPrimitive(systemText)
        }

        return AnthropicRequest(
            model = model,
            max_tokens = request.maxOutputTokens,
            messages = messages,
            system = systemElement,
            temperature = request.temperature,
            stream = false,
        )
    }

    /**
     * Plan-2 Task 25 known risk: Anthropic prompt caching has a minimum cacheable size
     * (1024 tokens Sonnet / 2048 tokens Haiku). Below that, marking `cache_control` is
     * pure noise + no discount. We use the char/4 estimate to gate.
     */
    internal fun shouldEnableCaching(request: LlmRequest, model: String): Boolean {
        val minTokens = if ("haiku" in model.lowercase()) 2048 else 1024
        var chars = 0
        chars += request.systemPrompt?.length ?: 0
        request.messages.forEach { chars += it.content.length }
        val estimateTokens = chars / 4
        return estimateTokens >= minTokens
    }

    private fun toLlmResponse(resp: AnthropicResponse, model: String): LlmResponse {
        val text = resp.content.filter { it.type == "text" }.joinToString("") { it.text }
        val price = ModelPriceLookup.lookup(providerId, model)
        // Plan-2 Task 25+26: Anthropic reports input_tokens NET of cache reads/writes. So
        // uncached = input_tokens; cached_read + cached_write are SEPARATE counters.
        // (Verified against Anthropic API docs as of 2026-05.)
        val cost = price?.computeCostCentsWithCache(
            uncachedInputTokens = resp.usage.input_tokens,
            cacheReadTokens = resp.usage.cache_read_input_tokens,
            cacheWriteTokens = resp.usage.cache_creation_input_tokens,
            outputTokens = resp.usage.output_tokens,
        ) ?: 0
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
