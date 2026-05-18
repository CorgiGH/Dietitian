package com.dietician.shared.llm.provider

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ModelPriceLookup
import com.dietician.shared.llm.ProviderId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Anthropic stream-json output from `claude --bare -p --stream-json` — Plan-2 Task 15.
 *
 * Anthropic streaming envelope (per docs.anthropic.com/en/api/messages-streaming):
 *
 *     event: message_start
 *     data: {"type":"message_start","message":{"id":"...","model":"...","usage":{"input_tokens":N}}}
 *
 *     event: content_block_start
 *     data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
 *
 *     event: content_block_delta
 *     data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello"}}
 *
 *     event: content_block_stop
 *     data: {"type":"content_block_stop","index":0}
 *
 *     event: message_delta
 *     data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":M}}
 *
 *     event: message_stop
 *     data: {"type":"message_stop"}
 *
 * The CLI may also emit `--bare` lines as raw JSON without `event:` prefix (single-shot mode).
 * We tolerate both: a line prefixed `data: ` is unwrapped, otherwise the line is parsed as
 * raw JSON if it starts with `{`.
 */
class ClaudeMaxStreamParser(
    private val providerId: ProviderId = ProviderId("claudemax-cli"),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Parse the full stream-json output [stream] (already-joined lines) and produce a
     * synthesized [LlmResponse] for the request that produced it. [requestedModel] is the
     * model the caller asked for — used as a fallback when the stream omits the model id.
     */
    fun parse(stream: String, requestedModel: String): LlmResponse {
        val state = ParseState()
        stream.lineSequence().forEach { rawLine ->
            val line = rawLine.removePrefix("data:").trim()
            if (line.isEmpty() || !line.startsWith("{")) return@forEach
            val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
            applyEvent(obj, state)
        }
        val text = state.textBuilder.toString()
        val finish = mapStopReason(state.stopReason)
        val cost = ModelPriceLookup.lookup(providerId, requestedModel)
            ?.computeCostCents(state.inputTokens, state.outputTokens) ?: 0
        return LlmResponse(
            provider = providerId,
            model = state.model ?: requestedModel,
            text = text,
            inputTokens = state.inputTokens,
            outputTokens = state.outputTokens,
            costCents = cost,
            finishReason = finish,
        )
    }

    private fun applyEvent(obj: JsonObject, state: ParseState) {
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "message_start" -> {
                val msg = obj["message"]?.jsonObject ?: return
                state.model = msg["model"]?.jsonPrimitive?.contentOrNull ?: state.model
                msg["usage"]?.jsonObject?.let { u ->
                    state.inputTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull ?: state.inputTokens
                }
            }
            "content_block_delta" -> {
                val delta = obj["delta"]?.jsonObject ?: return
                if (delta["type"]?.jsonPrimitive?.contentOrNull == "text_delta") {
                    delta["text"]?.jsonPrimitive?.contentOrNull?.let { state.textBuilder.append(it) }
                }
            }
            "message_delta" -> {
                obj["delta"]?.jsonObject?.let { d ->
                    state.stopReason = d["stop_reason"]?.jsonPrimitive?.contentOrNull ?: state.stopReason
                }
                obj["usage"]?.jsonObject?.let { u ->
                    state.outputTokens = u["output_tokens"]?.jsonPrimitive?.intOrNull ?: state.outputTokens
                }
            }
            "message_stop" -> {
                // sentinel only
            }
            else -> Unit
        }
    }

    private fun mapStopReason(raw: String?): FinishReason = when (raw) {
        "end_turn", "stop_sequence", null -> FinishReason.STOP
        "max_tokens" -> FinishReason.MAX_TOKENS
        "tool_use" -> FinishReason.TOOL_USE
        else -> FinishReason.STOP
    }

    private class ParseState {
        var model: String? = null
        val textBuilder = StringBuilder()
        var inputTokens: Int = 0
        var outputTokens: Int = 0
        var stopReason: String? = null
    }
}
