package com.dietician.shared.llm.provider

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the output of `claude -p --output-format json`.
 *
 * The CLI emits a JSON ARRAY of event objects. The terminal element has
 * `"type":"result"` and carries the answer + usage:
 *
 *     {"type":"result","subtype":"success","is_error":false,"result":"<text>",
 *      "usage":{"input_tokens":N,"output_tokens":M},"stop_reason":"end_turn", ...}
 *
 * An earlier `"type":"assistant"` element carries `message.model`.
 *
 * A run that did not produce a successful `result` (is_error true, subtype not
 * "success", or no result element at all, or output that is not JSON) is a
 * FAILURE — [parse] throws [LlmError] so the circuit-breaker and audit trail see
 * it. It must never be laundered into an empty successful response (council
 * 1779276774 — error-swallowing was the original empty-Coach-reply bug).
 *
 * costCents is fixed at 0: ClaudeMax runs on the Max-20x subscription, not the
 * metered API. `total_cost_usd` in the envelope is notional and is not charged;
 * OpenRouter spend is tracked separately by the budget ledger.
 */
class ClaudeMaxJsonParser(
    private val providerId: ProviderId = ProviderId("claudemax-cli"),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(stdout: String, requestedModel: String): LlmResponse {
        val root = runCatching { json.parseToJsonElement(stdout) }.getOrNull()
            ?: throw LlmError.TransientFailure(
                IllegalStateException("claude CLI output was not valid JSON: ${stdout.take(200)}"),
            )
        val events: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(root)
            else -> throw LlmError.TransientFailure(
                IllegalStateException("unexpected claude CLI JSON shape"),
            )
        }
        val result = events.lastOrNull { it.typeField() == "result" }
            ?: throw LlmError.TransientFailure(
                IllegalStateException("claude CLI produced no result event"),
            )

        val isError = result["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
        val subtype = result["subtype"]?.jsonPrimitive?.contentOrNull
        if (isError || subtype != "success") {
            throw LlmError.TransientFailure(
                IllegalStateException("claude CLI result not success (is_error=$isError subtype=$subtype)"),
            )
        }

        val text = result["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val usage = result["usage"]?.jsonObject
        val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
        val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
        val model = events.firstOrNull { it.typeField() == "assistant" }
            ?.get("message")?.jsonObject?.get("model")?.jsonPrimitive?.contentOrNull
            ?: requestedModel.ifBlank { "claudemax-cli" }
        val finish = when (result["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "end_turn", "stop_sequence", null -> FinishReason.STOP
            "max_tokens" -> FinishReason.MAX_TOKENS
            "tool_use" -> FinishReason.TOOL_USE
            else -> FinishReason.STOP
        }
        return LlmResponse(
            provider = providerId,
            model = model,
            text = text,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            costCents = 0,
            finishReason = finish,
        )
    }

    private fun JsonObject.typeField(): String? = this["type"]?.jsonPrimitive?.contentOrNull
}
