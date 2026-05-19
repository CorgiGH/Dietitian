package com.dietician.shared.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachLlmGatewayContractTest {
    @Test
    fun `CoachLocale exposes EN and RO with lowercase wire form`() {
        assertEquals(setOf(CoachLocale.EN, CoachLocale.RO), CoachLocale.entries.toSet())
        assertEquals("en", CoachLocale.EN.wire())
        assertEquals("ro", CoachLocale.RO.wire())
    }

    @Test
    fun `CoachLlmGateway implementation can stream chunks`() =
        runTest {
            val gw =
                object : CoachLlmGateway {
                    override fun streamCoachTurn(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
                        flowOf(LlmChunk("hi", isDone = true))
                }
            val out = gw.streamCoachTurn("x", CoachLocale.EN).toList()
            assertEquals(1, out.size)
            assertEquals("hi", out.first().text)
        }
}
