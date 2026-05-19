package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow

/**
 * iter-11 — bridges a [CoachLlmGateway] (platform-keyed Coach surface) into the
 * existing [LlmStream] interface consumed by `UiModule.kt:89`.
 *
 * Extracts the latest USER message from the [LlmRequest] and forwards it to the
 * gateway. Locale comes from [localeProvider] (typically wired to SettingsStore).
 */
class CoachLlmGatewayLlmStream(
    private val gateway: CoachLlmGateway,
    private val localeProvider: () -> CoachLocale,
) : LlmStream {
    override fun streamRoute(request: LlmRequest): Flow<LlmChunk> {
        val userPrompt =
            request.messages.lastOrNull { it.role == Role.USER }?.content
                ?: error("CoachLlmGatewayLlmStream: no USER message in request")
        return gateway.streamCoachTurn(userPrompt, localeProvider())
    }
}
