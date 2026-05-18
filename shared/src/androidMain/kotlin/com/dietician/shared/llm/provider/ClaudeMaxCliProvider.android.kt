package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderId

/**
 * Android stub — subprocess execution is not permitted on Android. The Router (Plan-2 Batch C)
 * checks `isAvailable()` and skips ClaudeMax in the chain on Android devices.
 */
actual class ClaudeMaxCliProvider {
    actual suspend fun call(request: LlmRequest, model: String): LlmResponse =
        throw LlmError.ProviderUnavailable(ProviderId("claudemax-cli"))

    actual fun isAvailable(): Boolean = false
}
