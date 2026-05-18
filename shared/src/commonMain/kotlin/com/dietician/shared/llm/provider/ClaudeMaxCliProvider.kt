package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse

/**
 * ClaudeMax CLI subprocess adapter — Plan-2 Tasks 14-18.
 *
 * Wraps the locally-installed `claude` CLI (bound to the user's Claude Max subscription) so
 * desktop runs can pull from Anthropic without per-call API spend. Android can't subprocess so
 * the actual class throws ProviderUnavailable.
 *
 * RC2 (Council 1779062699): canonical construction goes through the desktop actual's
 * companion-object factories (`forTesting` / `production`) — primary constructor is internal
 * on the desktop side. No reflection, no lateinit, no secondary-constructor swap.
 */
expect class ClaudeMaxCliProvider {
    suspend fun call(request: LlmRequest, model: String): LlmResponse

    fun isAvailable(): Boolean
}
