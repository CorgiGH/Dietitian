package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow

/**
 * Streaming surface — Plan-2 Task 21.
 *
 * Provider-side: each Batch B provider that supports SSE exposes its own per-class
 * `stream(request, model): Flow<LlmChunk>`. The Router wraps these into
 * [StreamProviderCallable] functional references for dispatch by [LlmRouterStream].
 *
 * Failover semantics (MVP per plan):
 *   - If the chosen provider's stream throws BEFORE emitting any chunk (handshake error,
 *     immediate 4xx, etc), the Router falls back to the next chain entry.
 *   - If the stream throws MID-STREAM (after one or more chunks have already been emitted
 *     to the caller), the failure propagates to the caller as-is. Falling back here would
 *     re-render text the user has already seen which is worse than surfacing the error.
 *
 * Cancellation: the caller closes the collected Flow → coroutine cancellation propagates
 * to Ktor `httpResponse.cancel()` via `withContext { ... }` semantics. Provider impls MUST
 * NOT swallow CancellationException — let it bubble.
 *
 * Audit semantics:
 *   - `llm_call_streaming_start` row written when the first chunk emits.
 *   - `llm_call` row written when the stream completes successfully (isDone=true) with
 *     the same shape as non-streaming Router (provider, model, tokens, cost).
 *   - `llm_call_failed_transient` on pre-chunk failover.
 *   - `llm_call_failed_mid_stream` if mid-stream error → caller-visible.
 */
interface LlmStream {
    fun streamRoute(request: LlmRequest): Flow<LlmChunk>
}

/**
 * Streaming chunk. [finalResponse] is populated ONLY on the last chunk (`isDone=true`) and
 * carries the realized token counts + cost from the provider's terminal usage block. Until
 * then, [tokenCount] is the running token count emitted by the provider when available
 * (Anthropic SSE emits cumulative; OpenAI-compat omits until final delta — fall back to
 * 0).
 */
data class LlmChunk(
    val text: String,
    val tokenCount: Int = 0,
    val isDone: Boolean = false,
    val finalResponse: LlmResponse? = null,
)

/**
 * Functional interface wrapping a provider's per-class `stream(request, model)`. Symmetric
 * to [ProviderCallable] in the non-streaming Router. Each Batch B provider that supports
 * streaming registers a StreamProviderCallable in the Router's stream-dispatch table.
 */
fun interface StreamProviderCallable {
    fun stream(request: LlmRequest, model: String): Flow<LlmChunk>
}
