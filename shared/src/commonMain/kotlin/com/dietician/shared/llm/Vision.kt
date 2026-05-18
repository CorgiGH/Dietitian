package com.dietician.shared.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Receipt-vision shortcut — Plan-2 Task 24.
 *
 * Wraps [LlmRouter] for the very common "extract receipt items from a photo" call. Routes
 * via [TaskType.VISION] on the VICTOR_DESKTOP chain — Batch B's vision-capable providers
 * (Anthropic + OpenRouter + Gemini) serialize the attached image into their respective
 * image-content shapes (RC1).
 *
 * Defaults to VICTOR_DESKTOP because ClaudeMax CLI is the cheapest vision provider for the
 * user. Callers (Android friend phone) override via [deviceClass] which routes to the
 * FRIEND_PHONE chain.
 *
 * RON / Romanian product naming is baked into the system prompt — Mega Image / Kaufland /
 * Lidl receipts are the canonical input. "Garantie ambalaj" (deposit) lines appear as
 * separate items, with negative `total_cents` if the deposit is returned mid-receipt.
 */
class Vision(private val router: LlmRouter) {

    suspend fun parseReceipt(
        image: AttachmentRef,
        hint: String,
        subjectId: String,
        deviceClass: DeviceClass = DeviceClass.VICTOR_DESKTOP,
    ): ParsedReceipt {
        val request = LlmRequest(
            subjectId = subjectId,
            task = TaskType.VISION,
            deviceClass = deviceClass,
            capability = Capability.JSON_MODE,
            systemPrompt = RECEIPT_PROMPT,
            messages = listOf(
                LlmMessage(Role.USER, "Hint: $hint\n\nExtract receipt items."),
            ),
            attachments = listOf(LlmAttachment("image/jpeg", image)),
            maxOutputTokens = 2048,
            temperature = 0.0,
        )
        val response = router.route(request)
        return parseReceiptJson(response.text)
    }

    /**
     * Strip optional markdown fence (same as moderator) before deserializing the JSON.
     * Receipt JSON is load-bearing for downstream pantry tracking — unparseable output
     * throws [LlmError.PermanentFailure] rather than silently shipping an empty receipt.
     */
    internal fun parseReceiptJson(raw: String): ParsedReceipt {
        val trimmed = raw.trim()
        val unfenced = if (trimmed.startsWith("```")) {
            val firstNewline = trimmed.indexOf('\n')
            if (firstNewline < 0) trimmed.trim('`').trim()
            else trimmed.substring(firstNewline + 1).trimEnd('`').trim()
        } else trimmed
        return try {
            JSON.decodeFromString(ParsedReceipt.serializer(), unfenced)
        } catch (t: Throwable) {
            throw LlmError.PermanentFailure(
                IllegalStateException("vision returned unparseable receipt JSON: '$raw'", t),
            )
        }
    }

    companion object {
        const val RECEIPT_PROMPT: String =
            "Extract receipt items as strict JSON: " +
                "{\"store\": \"...\", \"date\": \"ISO-8601\", \"items\": " +
                "[{\"name\": \"...\", \"qty\": 1, \"unit_price_cents\": ..., \"total_cents\": ...}], " +
                "\"total_cents\": ..., \"currency\": \"RON\"}. " +
                "Use Romanian product naming as printed. RON for currency. " +
                "Items include garantie ambalaj as separate items."

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

@Serializable
data class ParsedReceipt(
    val store: String,
    val date: String,
    val items: List<ReceiptItem>,
    @SerialName("total_cents") val totalCents: Int,
    val currency: String = "RON",
)

@Serializable
data class ReceiptItem(
    val name: String,
    val qty: Int = 1,
    @SerialName("unit_price_cents") val unitPriceCents: Int,
    @SerialName("total_cents") val totalCents: Int,
)
