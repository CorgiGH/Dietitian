@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.InMemoryAuditLogSink
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.ui.data.AuditListOutcome
import com.dietician.shared.ui.data.AuditRepository
import com.dietician.shared.ui.data.AuditRow
import com.dietician.shared.ui.data.ConsentListOutcome
import com.dietician.shared.ui.data.ConsentOutcome
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.screens.AuditLogScreen
import com.dietician.shared.ui.screens.AuditLogViewModel
import com.dietician.shared.ui.screens.CoachChatScreen
import com.dietician.shared.ui.screens.CoachChatViewModel
import com.dietician.shared.ui.theme.DieticianTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plan-4-5 Task 33 — CoachChat send → disclosure pane → audit deep-link (RC7).
 *
 * Flow:
 *   1. CoachChat paints with input bar
 *   2. User types message + taps Send
 *   3. Stub LlmStream emits chunks + terminal LlmResponse → bubble gets disclosure
 *   4. User taps "Open audit row" on disclosure pane → host navigates to AuditLog
 *   5. AuditLog opens with callUuidFilter set → filtered list shows only that row
 */
class CoachChatAuditIntegrationTest {

    @get:Rule val composeRule = createComposeRule()

    private class StaticStream(private val callUuid: String) : LlmStream {
        override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = flow {
            emit(LlmChunk(text = "Hello"))
            emit(LlmChunk(text = " Victor"))
            emit(
                LlmChunk(
                    text = "",
                    isDone = true,
                    finalResponse = LlmResponse(
                        provider = ProviderId("anthropic"),
                        model = "claude-3-5-sonnet",
                        text = "Hello Victor",
                        inputTokens = 12,
                        outputTokens = 5,
                        costCents = 1,
                        finishReason = FinishReason.STOP,
                    ),
                ),
            )
        }
    }

    private class StubAuditRepo(private val rows: List<AuditRow>) : AuditRepository {
        var lastCallUuidFilter: String? = null
        override suspend fun listJson(callUuidFilter: String?, kindFilter: String?): AuditListOutcome {
            lastCallUuidFilter = callUuidFilter
            val filtered = rows.filter {
                (callUuidFilter == null || it.callUuid == callUuidFilter) &&
                    (kindFilter == null || it.kind == kindFilter)
            }
            return AuditListOutcome.Rows(filtered)
        }
        override suspend fun exportPdf() = throw UnsupportedOperationException()
        override suspend fun exportDsarZip() = throw UnsupportedOperationException()
        override suspend fun updateConsent(scope: String, granted: Boolean): ConsentOutcome = ConsentOutcome.Ok
        override suspend fun listConsents(): ConsentListOutcome = ConsentListOutcome.Rows(emptyList())
    }

    @Test
    fun `CoachChat send → disclosure pane → tap opens AuditLog filtered by call_uuid`() = runTest {
        val callUuid = "test-call-uuid-1"
        val coachScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val auditScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val sink = InMemoryAuditLogSink()
        val auditRows = listOf(
            AuditRow(id = "row-1", occurredAtMs = 1L, kind = "llm_call", callUuid = callUuid, model = "anthropic/claude-3-5-sonnet"),
            AuditRow(id = "row-2", occurredAtMs = 2L, kind = "llm_call", callUuid = "other-uuid", model = "anthropic/claude-3-5-sonnet"),
        )
        val auditRepo = StubAuditRepo(auditRows)

        val coachVm = CoachChatViewModel(
            stream = StaticStream(callUuid),
            audit = sink,
            subjectIdProvider = { "00000000-victor" },
            callUuidGen = { callUuid },
            coroutineScope = coachScope,
        )

        var route by mutableStateOf("coach")
        var capturedCallUuid: String? = null
        val auditVm = AuditLogViewModel(
            repo = auditRepo,
            saveFile = { _, _, _ -> null },
            coroutineScope = auditScope,
        )

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    when (route) {
                        "coach" -> CoachChatScreen(
                            viewModel = coachVm,
                            onOpenAuditRow = { uuid ->
                                capturedCallUuid = uuid
                                auditVm.setCallUuidFilter(uuid)
                                route = "audit"
                            },
                        )
                        "audit" -> AuditLogScreen(viewModel = auditVm)
                    }
                }
            }
        }

        // Type + send.
        composeRule.onNodeWithTag("coach-input").performTextInput("hi")
        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("coach-send").performClick()
        advanceUntilIdle()
        composeRule.waitForIdle()

        // Stream completes — disclosure pane should be present.
        composeRule.onNodeWithTag("coach-disclosure-$callUuid").assertIsDisplayed()
        composeRule.onNodeWithTag("coach-disclosure-open-audit-$callUuid").performClick()

        assertEquals(callUuid, capturedCallUuid)

        // Audit screen should be rendered with filter applied.
        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audit-log-screen").assertIsDisplayed()
        assertEquals(callUuid, auditRepo.lastCallUuidFilter)
    }

    @Test
    fun `CoachChat Just-Tell-Me bypass emits audit row + does not call stream (Art 14)`() = runTest {
        val sink = InMemoryAuditLogSink()
        val streamCallsForBypass = object : LlmStream {
            var calls = 0
            override fun streamRoute(request: LlmRequest): Flow<LlmChunk> {
                calls += 1
                return flow {}
            }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val vm = CoachChatViewModel(
            stream = streamCallsForBypass,
            audit = sink,
            subjectIdProvider = { "00000000-victor" },
            coroutineScope = scope,
        )

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    CoachChatScreen(viewModel = vm)
                }
            }
        }

        composeRule.onNodeWithTag("coach-just-tell-me-button").performClick()
        advanceUntilIdle()
        composeRule.waitForIdle()

        // Bypass message present, stream untouched.
        assertEquals(0, streamCallsForBypass.calls)
        val rows = sink.snapshot()
        assertEquals(1, rows.size)
        assertEquals("just_tell_me_bypass", rows.first().kind)
    }
}
