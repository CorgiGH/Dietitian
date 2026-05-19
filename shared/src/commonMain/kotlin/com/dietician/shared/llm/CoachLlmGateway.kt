package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow

/**
 * iter-11 — platform-keyed strategy for Coach turn streaming.
 *
 * Desktop impl runs ClaudeMaxCliProvider locally + 2PC reserve/commit HTTP calls
 * around the subprocess. Android impl is a thin SSE consumer hitting
 * `/coach/stream` which internally handles 2PC server-side.
 *
 * Returns a Flow<LlmChunk> so it can adapt cleanly into the existing
 * LlmStream interface used by `UiModule.kt:89` via CoachLlmGatewayLlmStream.
 */
interface CoachLlmGateway {
    fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk>
}
