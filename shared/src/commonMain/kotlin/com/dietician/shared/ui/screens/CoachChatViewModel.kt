package com.dietician.shared.ui.screens

import com.dietician.shared.llm.AuditEntry
import com.dietician.shared.llm.AuditLogSink
import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * CoachChat screen state holder. Wraps Plan-2 [LlmStream] (`LlmRouterStream`)
 * with three RC additions on top of the router's baseline audit semantics:
 *
 * RC7 — per-call disclosure deep-link. Each assistant bubble carries a
 * [DisclosureInfo] populated from the terminal [LlmChunk.finalResponse] +
 * [LlmRequest.requestId]-equivalent we synthesize as [callUuidGen]. The host
 * UI surfaces an "Open audit row" link that calls [openAuditFor] which fires
 * [onOpenAuditRow] — the nav layer routes to AuditLogScreen with the call_uuid
 * pre-filtered.
 *
 * RC9 — coach-disabled gate. When [coachDisabledProvider] returns true, both
 * [sendMessage] and [justTellMe] are no-ops + the screen renders
 * [CoachDisabledNotice] instead of the input bar. The provider reads
 * `SubjectStore.llm_coach_disabled` (Plan-3 settings) — wired by Batch E.
 *
 * RC12 — cancellation audit. [LlmRouterStream] today releases budget + propagates
 * `CancellationException` WITHOUT writing a row, by design ("caller intent, not
 * a router-side failure"). RC12 mandates a `coach_chat_cancelled` row with the
 * partial byte count, so we wrap the stream-collect at the screen layer here:
 *
 *   - Accumulate received chunk bytes into [streamingBytes]
 *   - In `onCompletion { cause -> ... }` write the `coach_chat_cancelled` row
 *     when [cause] is a [CancellationException]
 *
 * This is intentional layering: the router stays "single source of truth" for
 * llm_call audit rows; this VM owns "coach feature" rows (cancel + bypass).
 *
 * Just-tell-me (Art 14 + spec §): bypasses [LlmStream] entirely + returns a
 * deterministic rule-based answer keyed off the user's pantry. First-ship is
 * a static plausible answer (200g chicken + 200g rice + 100g broccoli);
 * Plan-3 Task 39 + Plan-7 corpus replace this with a real local-corpus lookup.
 * A `just_tell_me_bypass` audit row is written so usage stats are observable.
 */
class CoachChatViewModel(
    private val stream: LlmStream,
    private val audit: AuditLogSink,
    private val coachDisabledProvider: () -> Boolean = { false },
    private val subjectIdProvider: () -> String,
    private val callUuidGen: () -> String = { defaultUuid() },
    private val systemPrompt: String? = null,
    private val coroutineScope: CoroutineScope = MainScope(),
) {
    private val _state = MutableStateFlow(CoachChatState())
    val state: StateFlow<CoachChatState> = _state.asStateFlow()

    /** RC7 deep-link callback — host nav layer wires this to AuditLog route. */
    var onOpenAuditRow: (String) -> Unit = {}

    private var streamJob: Job? = null
    private var streamingCallUuid: String? = null
    private var streamingBytes: Int = 0

    fun load() {
        _state.value = _state.value.copy(coachDisabled = coachDisabledProvider())
    }

    fun onInputChange(s: String) {
        _state.value = _state.value.copy(input = s)
    }

    fun sendMessage() {
        if (_state.value.coachDisabled) return
        val input = _state.value.input.ifBlank { return }
        val callUuid = callUuidGen()
        streamingCallUuid = callUuid
        streamingBytes = 0

        val userMsg = ChatMessage(
            id = "user-${nowMs()}",
            fromUser = true,
            text = input,
            disclosure = null,
        )
        val assistantId = "assistant-$callUuid"
        val assistantStub = ChatMessage(
            id = assistantId,
            fromUser = false,
            text = "",
            disclosure = null,
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg + assistantStub,
            input = "",
            streaming = true,
        )

        val request = LlmRequest(
            subjectId = subjectIdProvider(),
            task = TaskType.TEXT,
            deviceClass = DeviceClass.ANY,
            capability = Capability.STREAMING,
            messages = listOf(LlmMessage(Role.USER, input)),
            systemPrompt = systemPrompt,
        )

        streamJob = coroutineScope.launch {
            val accum = StringBuilder()
            var terminalDisclosure: DisclosureInfo? = null
            var wasCancelled = false
            try {
                stream.streamRoute(request)
                    .onCompletion { cause ->
                        if (cause is CancellationException) {
                            wasCancelled = true
                            audit.write(
                                AuditEntry(
                                    subjectId = subjectIdProvider(),
                                    kind = "coach_chat_cancelled",
                                    requestId = callUuid,
                                    extra = mapOf(
                                        "partial_byte_count" to streamingBytes.toString(),
                                    ),
                                ),
                            )
                        }
                    }
                    .collect { chunk ->
                        accum.append(chunk.text)
                        streamingBytes += chunk.text.encodeToByteArray().size
                        // Update streaming bubble text live
                        val cur = _state.value
                        val updated = cur.messages.map { m ->
                            if (m.id == assistantId) m.copy(text = accum.toString()) else m
                        }
                        _state.value = cur.copy(messages = updated)
                        if (chunk.isDone && chunk.finalResponse != null) {
                            val r = chunk.finalResponse
                            terminalDisclosure = DisclosureInfo(
                                model = "${r.provider.raw}/${r.model}",
                                inputTokens = r.inputTokens,
                                outputTokens = r.outputTokens,
                                costCents = r.costCents,
                                timestampMs = nowMs(),
                                callUuid = callUuid,
                            )
                        }
                    }
            } catch (ce: CancellationException) {
                // onCompletion already wrote the cancel row above; swallow.
                throw ce
            } catch (t: Throwable) {
                // Surface error in the assistant bubble instead of crashing.
                val cur = _state.value
                val errored = cur.messages.map { m ->
                    if (m.id == assistantId) m.copy(text = "Coach error: ${t.message ?: "unknown"}") else m
                }
                _state.value = cur.copy(messages = errored, streaming = false)
                return@launch
            }
            // Stream completed normally — settle disclosure.
            val cur = _state.value
            val settled = cur.messages.map { m ->
                if (m.id == assistantId) m.copy(disclosure = terminalDisclosure) else m
            }
            _state.value = cur.copy(messages = settled, streaming = false)
        }
    }

    /** RC12 — user cancels mid-stream. Triggers the cancellation audit row. */
    fun cancelStream() {
        streamJob?.cancel(CancellationException("user_cancel"))
        _state.value = _state.value.copy(streaming = false)
    }

    /**
     * Art 14 "Just tell me" bypass. Returns a static plausible answer for
     * first-ship; Plan-3 Task 39 wires a real local-corpus rule-based answerer.
     * Always emits a `just_tell_me_bypass` audit row for observability.
     */
    fun justTellMe() {
        if (_state.value.coachDisabled) return
        val callUuid = callUuidGen()
        val text = "Try: 200g chicken breast + 200g rice + 100g broccoli"
        val msg = ChatMessage(
            id = "assistant-bypass-$callUuid",
            fromUser = false,
            text = text,
            disclosure = DisclosureInfo(
                model = "rule_based_bypass",
                inputTokens = 0,
                outputTokens = 0,
                costCents = 0,
                timestampMs = nowMs(),
                callUuid = callUuid,
            ),
            bypassBanner = true,
        )
        _state.value = _state.value.copy(messages = _state.value.messages + msg)
        coroutineScope.launch {
            audit.write(
                AuditEntry(
                    subjectId = subjectIdProvider(),
                    kind = "just_tell_me_bypass",
                    requestId = callUuid,
                    extra = mapOf("answer_kind" to "static_first_ship"),
                ),
            )
        }
    }

    /** RC7 — deep-link to AuditLogScreen filtered by call_uuid. */
    fun openAuditFor(callUuid: String) {
        onOpenAuditRow(callUuid)
    }

    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    companion object {
        private fun defaultUuid(): String {
            // commonMain has no java.util.UUID — use kotlinx-datetime + random suffix.
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val r = (0..1_000_000_000).random()
            return "$now-$r"
        }
    }
}

/**
 * UI-projection of a CoachChat message. Disclosure is null while the assistant
 * bubble is mid-stream; populated when the terminal chunk lands.
 */
data class ChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val disclosure: DisclosureInfo? = null,
    val bypassBanner: Boolean = false,
)

/**
 * Art 13 per-call disclosure payload. UI surfaces these fields in a collapsed
 * "Disclosure" footer below each assistant bubble; tapping the call_uuid opens
 * AuditLogScreen filtered by that row (RC7).
 */
data class DisclosureInfo(
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val costCents: Int,
    val timestampMs: Long,
    val callUuid: String,
)

data class CoachChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val streaming: Boolean = false,
    val coachDisabled: Boolean = false,
)
