package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachLlmGatewayLlmStreamTest {
    @Test
    fun `streamRoute delegates to gateway with user message text`() =
        runTest {
            var capturedPrompt = ""
            var capturedLocale: CoachLocale? = null
            val gw =
                object : CoachLlmGateway {
                    override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> {
                        capturedPrompt = prompt
                        capturedLocale = locale
                        return flowOf(LlmChunk("ok", isDone = true))
                    }
                }
            val adapter = CoachLlmGatewayLlmStream(gateway = gw, localeProvider = { CoachLocale.RO })
            val req =
                LlmRequest(
                    subjectId = "s",
                    task = TaskType.TEXT,
                    deviceClass = DeviceClass.ANY,
                    capability = Capability.STREAMING,
                    messages = listOf(LlmMessage(Role.USER, "what to eat for protein")),
                )
            val out = adapter.streamRoute(req).toList()
            assertEquals("what to eat for protein", capturedPrompt)
            assertEquals(CoachLocale.RO, capturedLocale)
            assertEquals("ok", out.first().text)
        }
}
