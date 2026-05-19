package com.dietician.shared.llm

import com.dietician.shared.llm.net.CoachHttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * iter-11 — Android Coach gateway. Pure SSE consumer hitting `/coach/stream`;
 * server handles reserve+commit internally. No ClaudeMax on Android — the
 * server-side LlmStream binding handles provider routing (OpenRouter→Groq
 * per spec §7 iter-11 amendment).
 */
class AndroidCoachLlmGateway(
    private val http: CoachHttpClient,
    private val uuid: () -> String = { UUID.randomUUID().toString() },
) : CoachLlmGateway {
    override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> {
        val key = uuid()
        return http.stream(idempotencyKey = key, prompt = prompt, locale = locale.wire())
            .map { LlmChunk(text = it, isDone = false) }
    }
}
