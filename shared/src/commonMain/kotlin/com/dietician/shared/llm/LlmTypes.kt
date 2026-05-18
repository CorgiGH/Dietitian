package com.dietician.shared.llm

/**
 * Plan-2 type surface for LLM Router I/O.
 *
 * RC8 (Council 1779062699): [TaskType] + [DeviceClass] + [Capability] are closed enums so
 * routing-rules `when` blocks are exhaustive — adding a new variant fails compilation at
 * every dispatch site rather than silently falling through.
 *
 * RC1 vision wiring: [LlmAttachment] is fully shaped now; Batch B (Tasks 10-12) wires the
 * per-provider serialization of [AttachmentRef] into upstream HTTP bodies.
 */
data class LlmRequest(
    val subjectId: String,
    val task: TaskType,
    val deviceClass: DeviceClass,
    val capability: Capability,
    val messages: List<LlmMessage>,
    val attachments: List<LlmAttachment> = emptyList(),
    val maxOutputTokens: Int = 4096,
    val temperature: Double = 0.7,
    val systemPrompt: String? = null,
    val cacheControl: CacheControl = CacheControl.NONE,
)

data class LlmMessage(val role: Role, val content: String)

enum class Role { USER, ASSISTANT, SYSTEM }

enum class TaskType { TEXT, MODERATION, VISION, EMBEDDING }

enum class DeviceClass { VICTOR_DESKTOP, FRIEND_PHONE, SERVER, ANY }

enum class Capability { STREAMING, NON_STREAMING, JSON_MODE, TOOL_USE }

enum class CacheControl { NONE, EPHEMERAL }

data class LlmAttachment(val mimeType: String, val ref: AttachmentRef)

/**
 * Vision/audio/file attachment payload. Three carrier forms:
 *   - [Bytes] inline base64 transport (small images)
 *   - [FilePath] desktop local file (Plan-2 scratchpad / OCR pipeline)
 *   - [Url] hosted URL (Gemini files API, OpenRouter image_url)
 */
sealed interface AttachmentRef {
    @Suppress("ArrayInDataClass")
    data class Bytes(val data: ByteArray) : AttachmentRef

    data class FilePath(val path: String) : AttachmentRef

    data class Url(val url: String) : AttachmentRef
}

/**
 * Provider response after Router's post-processing (price math + cost finalize).
 *
 * [cacheReadTokens] + [cacheWriteTokens] cover Anthropic prompt-caching (RC2 batch).
 */
data class LlmResponse(
    val provider: ProviderId,
    val model: String,
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val costCents: Int,
    val finishReason: FinishReason,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0,
)

enum class FinishReason { STOP, MAX_TOKENS, CONTENT_FILTER, TOOL_USE, ERROR }

/**
 * Closed error hierarchy.
 *
 * Router-side classifier (Batch B) maps HTTP/provider errors onto these. Audit-log
 * encodes the variant name in `extra.error_kind`. RateLimit + Timeout + Transient drive
 * the failover chain to the next provider; Permanent + ContentFiltered + ProviderUnavailable
 * surface to the caller after exhausting retries on the SAME provider.
 *
 * Resilience4j-driven decisions read the variant via `is` checks — keeping this hierarchy
 * sealed lets the policy table be a `when` block.
 */
sealed class LlmError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RateLimitExceeded(val retryAfterMs: Long?) : LlmError("Rate limit hit")

    class BudgetExhausted(val provider: ProviderId) : LlmError("Budget exhausted for $provider")

    class Timeout(val phase: String) : LlmError("Timeout in $phase")

    class TransientFailure(cause: Throwable) : LlmError("Transient: ${cause.message}", cause)

    class PermanentFailure(cause: Throwable) : LlmError("Permanent: ${cause.message}", cause)

    class ContentFiltered(val reason: String) : LlmError("Content filtered: $reason")

    class ProviderUnavailable(val provider: ProviderId) : LlmError("Provider unavailable: $provider")
}
