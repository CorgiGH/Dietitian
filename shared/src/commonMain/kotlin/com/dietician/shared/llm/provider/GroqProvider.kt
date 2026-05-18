package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderConfig
import com.dietician.shared.llm.ProviderId
import io.ktor.client.HttpClient

/**
 * Groq adapter — Plan-2 Task 13. Groq exposes the OpenAI-compat chat/completions surface so
 * implementation delegates to [OpenRouterProvider] with a different `baseUrl` + provider id.
 *
 * Vision attachments are emitted by the underlying [OpenRouterProvider] in the OpenAI-compat
 * shape (RC1 inheritance) — most Groq models do not support vision, so the Router (Task 9
 * routing-rules) excludes Groq from VISION chains. If a vision request reaches here anyway,
 * the upstream API returns a structured error mapped to [com.dietician.shared.llm.LlmError]
 * by the Router's error classifier (Batch C).
 */
class GroqProvider(
    client: HttpClient,
    config: ProviderConfig,
    private val providerId: ProviderId = ProviderId("groq"),
) {
    private val inner = OpenRouterProvider(
        client = client,
        config = config,
        providerId = providerId,
    )

    suspend fun call(request: LlmRequest, model: String): LlmResponse = inner.call(request, model)

    internal fun buildOpenRouterRequest(request: LlmRequest, model: String): OpenRouterRequest =
        inner.buildOpenRouterRequest(request, model)
}
