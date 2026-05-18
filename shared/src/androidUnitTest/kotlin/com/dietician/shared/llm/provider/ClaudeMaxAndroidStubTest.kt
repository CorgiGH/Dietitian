package com.dietician.shared.llm.provider

import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/**
 * Android stub: ClaudeMax CLI subprocess is impossible on Android (sandbox forbids fork+exec).
 * Router skips this provider when `isAvailable()` returns false.
 */
class ClaudeMaxAndroidStubTest {
    @Test
    fun `isAvailable returns false on Android`() {
        val provider = ClaudeMaxCliProvider()
        assertFalse(provider.isAvailable())
    }

    @Test
    fun `call throws ProviderUnavailable on Android`() = runBlocking {
        val provider = ClaudeMaxCliProvider()
        try {
            provider.call(
                LlmRequest(
                    subjectId = "victor",
                    task = TaskType.TEXT,
                    deviceClass = DeviceClass.FRIEND_PHONE,
                    capability = Capability.NON_STREAMING,
                    messages = listOf(LlmMessage(Role.USER, "hi")),
                ),
                "anthropic/claude-sonnet-4.5",
            )
            fail("expected ProviderUnavailable")
        } catch (e: LlmError.ProviderUnavailable) {
            assertEquals(ProviderId("claudemax-cli"), e.provider)
        }
    }
}
