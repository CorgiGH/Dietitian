@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.screens

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.InMemoryAuditLogSink
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.ProviderId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoachChatViewModelTest {

    private class FakeStream(
        private val chunks: List<LlmChunk>,
        private val perChunkDelayMs: Long = 0L,
    ) : LlmStream {
        override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = flow {
            for (c in chunks) {
                if (perChunkDelayMs > 0) delay(perChunkDelayMs)
                emit(c)
            }
        }
    }

    private fun finalResp(text: String) = LlmResponse(
        provider = ProviderId("anthropic"),
        model = "claude-sonnet-4.5",
        text = text,
        inputTokens = 100,
        outputTokens = 50,
        costCents = 42,
        finishReason = FinishReason.STOP,
    )

    @Test
    fun `initial state empty + not disabled + no streaming`() = runTest {
        val vm = CoachChatViewModel(
            stream = FakeStream(emptyList()),
            audit = InMemoryAuditLogSink(),
            coachDisabledProvider = { false },
            subjectIdProvider = { "victor" },
            coroutineScope = this,
        )
        assertFalse(vm.state.value.streaming)
        assertFalse(vm.state.value.coachDisabled)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `load picks up coachDisabled provider (RC9)`() = runTest {
        val vm = CoachChatViewModel(
            stream = FakeStream(emptyList()),
            audit = InMemoryAuditLogSink(),
            coachDisabledProvider = { true },
            subjectIdProvider = { "victor" },
            coroutineScope = this,
        )
        vm.load()
        assertTrue(vm.state.value.coachDisabled)
    }

    @Test
    fun `sendMessage adds user bubble + streams assistant + populates disclosure`() = runTest {
        val finalRespText = "Try 200g chicken"
        val final = finalResp(finalRespText)
        val chunks = listOf(
            LlmChunk(text = "Try "),
            LlmChunk(text = "200g chicken", tokenCount = 5),
            LlmChunk(text = "", isDone = true, finalResponse = final),
        )
        val audit = InMemoryAuditLogSink()
        val vm = CoachChatViewModel(
            stream = FakeStream(chunks),
            audit = audit,
            coachDisabledProvider = { false },
            subjectIdProvider = { "victor" },
            callUuidGen = { "uuid-1" },
            coroutineScope = this,
        )
        vm.onInputChange("what should I eat?")
        vm.sendMessage()
        advanceUntilIdle()
        val msgs = vm.state.value.messages
        // user bubble + assistant bubble
        assertEquals(2, msgs.size)
        assertEquals("what should I eat?", msgs[0].text)
        assertTrue(msgs[0].fromUser)
        assertFalse(msgs[1].fromUser)
        assertTrue(msgs[1].text.contains("200g"))
        val disclosure = msgs[1].disclosure
        assertNotNull(disclosure)
        assertEquals("anthropic/claude-sonnet-4.5", disclosure.model)
        assertEquals(100, disclosure.inputTokens)
        assertEquals(50, disclosure.outputTokens)
        assertEquals(42, disclosure.costCents)
        assertEquals("uuid-1", disclosure.callUuid)
        assertFalse(vm.state.value.streaming)
        assertEquals("", vm.state.value.input)
    }

    @Test
    fun `cancelStream emits coach_chat_cancelled audit row with partial_byte_count (RC12)`() = runTest {
        val final = finalResp("never reached")
        val chunks = listOf(
            LlmChunk(text = "Try "),
            LlmChunk(text = "200g chicken"),
            LlmChunk(text = " breast and"),
            LlmChunk(text = "", isDone = true, finalResponse = final),
        )
        val audit = InMemoryAuditLogSink()
        val vm = CoachChatViewModel(
            stream = FakeStream(chunks, perChunkDelayMs = 100L),
            audit = audit,
            coachDisabledProvider = { false },
            subjectIdProvider = { "victor" },
            callUuidGen = { "uuid-cancel" },
            coroutineScope = this,
        )
        vm.onInputChange("hello")
        val sendJob = launch { vm.sendMessage() }
        // Let some chunks land then cancel
        delay(150)
        vm.cancelStream()
        advanceUntilIdle()
        sendJob.join()

        val rows = audit.snapshot()
        val cancelledRow = rows.firstOrNull { it.kind == "coach_chat_cancelled" }
        assertNotNull(cancelledRow)
        assertEquals("victor", cancelledRow.subjectId)
        assertEquals("uuid-cancel", cancelledRow.requestId)
        val pbc = cancelledRow.extra["partial_byte_count"]
        assertNotNull(pbc)
        assertTrue(pbc.toInt() > 0)
        assertFalse(vm.state.value.streaming)
    }

    @Test
    fun `coach disabled blocks sendMessage`() = runTest {
        val audit = InMemoryAuditLogSink()
        val vm = CoachChatViewModel(
            stream = FakeStream(listOf(LlmChunk(text = "should not run", isDone = true, finalResponse = finalResp("x")))),
            audit = audit,
            coachDisabledProvider = { true },
            subjectIdProvider = { "victor" },
            coroutineScope = this,
        )
        vm.load()
        vm.onInputChange("hi")
        vm.sendMessage()
        advanceUntilIdle()
        // No user/assistant message added when coach disabled
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `JustTellMe bypass returns deterministic answer + emits bypass audit row`() = runTest {
        val audit = InMemoryAuditLogSink()
        val vm = CoachChatViewModel(
            stream = FakeStream(emptyList()),
            audit = audit,
            coachDisabledProvider = { false },
            subjectIdProvider = { "victor" },
            coroutineScope = this,
        )
        vm.justTellMe()
        advanceUntilIdle()
        val msgs = vm.state.value.messages
        // Only assistant bubble (no user input required)
        assertEquals(1, msgs.size)
        assertFalse(msgs[0].fromUser)
        assertTrue(msgs[0].text.lowercase().contains("chicken") || msgs[0].text.lowercase().contains("rice"))
        // Just-tell-me bypass marker on disclosure
        assertEquals("rule_based_bypass", msgs[0].disclosure?.model)
        // Audit row emitted
        val bypassRow = audit.snapshot().firstOrNull { it.kind == "just_tell_me_bypass" }
        assertNotNull(bypassRow)
    }

    @Test
    fun `openDisclosureAuditDeepLink reports call_uuid (RC7)`() = runTest {
        val vm = CoachChatViewModel(
            stream = FakeStream(emptyList()),
            audit = InMemoryAuditLogSink(),
            coachDisabledProvider = { false },
            subjectIdProvider = { "victor" },
            coroutineScope = this,
        )
        var openedUuid: String? = null
        vm.onOpenAuditRow = { openedUuid = it }
        vm.openAuditFor("call-xyz")
        assertEquals("call-xyz", openedUuid)
    }

    @Test
    fun `onInputChange updates input + sendMessage clears it`() = runTest {
        val final = finalResp("ok")
        val vm = CoachChatViewModel(
            stream = FakeStream(listOf(LlmChunk(text = "ok", isDone = true, finalResponse = final))),
            audit = InMemoryAuditLogSink(),
            coachDisabledProvider = { false },
            subjectIdProvider = { "victor" },
            coroutineScope = this,
        )
        vm.onInputChange("hello")
        assertEquals("hello", vm.state.value.input)
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals("", vm.state.value.input)
    }
}
