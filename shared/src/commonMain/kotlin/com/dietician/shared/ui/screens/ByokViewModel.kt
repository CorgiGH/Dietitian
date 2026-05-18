package com.dietician.shared.ui.screens

import com.dietician.shared.llm.AuditEntry
import com.dietician.shared.llm.AuditLogSink
import com.dietician.shared.ui.data.ByokRepository
import com.dietician.shared.ui.data.ByokSaveOutcome
import com.dietician.shared.ui.data.DieticianClipboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BYOK (Bring Your Own Key) screen state holder. RC13 (Council 1779120600)
 * clipboard-clear-on-paste enforced here:
 *
 *   1. TextField onValueChange fires with the new value
 *   2. ViewModel.onKeyValueChange compares old vs new length delta
 *   3. If delta > [PASTE_DELTA_THRESHOLD] chars AND clipboard content is non-empty
 *      → it's a paste event
 *   4. We clear the clipboard (`DieticianClipboardManager.clear()`) + emit
 *      audit row `subject_credential_paste_detected` + surface a snackbar
 *      via [ByokState.pasteToastVisible]
 *
 * The heuristic is acceptable: a user typing 20 chars at once is implausible,
 * and a non-empty clipboard at that moment is the strong evidence.
 */
class ByokViewModel(
    private val repo: ByokRepository,
    private val auditSink: AuditLogSink,
    private val clipboard: DieticianClipboardManager,
    private val nowMs: () -> Long = { 0L },
    private val coroutineScope: CoroutineScope = MainScope(),
) {
    private val _state = MutableStateFlow(ByokState())
    val state: StateFlow<ByokState> = _state.asStateFlow()

    fun selectProvider(provider: String) {
        _state.value = _state.value.copy(provider = provider)
    }

    /**
     * Called from TextField.onValueChange. Detects paste events by comparing
     * old vs new length + the active clipboard contents.
     */
    fun onKeyValueChange(newValue: String) {
        val previous = _state.value.keyValue
        val delta = newValue.length - previous.length
        val clipContents = clipboard.get()
        val likelyPaste = delta >= PASTE_DELTA_THRESHOLD &&
            !clipContents.isNullOrEmpty() &&
            newValue.contains(clipContents)
        _state.value = _state.value.copy(keyValue = newValue)
        if (likelyPaste) {
            clipboard.clear()
            _state.value = _state.value.copy(pasteToastVisible = true)
            coroutineScope.launch {
                auditSink.write(
                    AuditEntry(
                        kind = "subject_credential_paste_detected",
                        extra = mapOf(
                            "provider" to _state.value.provider,
                            "occurred_at_ms" to nowMs().toString(),
                        ),
                    ),
                )
            }
        }
    }

    fun dismissPasteToast() {
        _state.value = _state.value.copy(pasteToastVisible = false)
    }

    fun save() {
        val key = _state.value.keyValue
        val provider = _state.value.provider
        if (key.isBlank() || provider.isBlank()) return
        _state.value = _state.value.copy(saving = true, errorToast = null)
        coroutineScope.launch {
            _state.value = when (val outcome = repo.save(provider, key)) {
                ByokSaveOutcome.Ok -> _state.value.copy(
                    saving = false,
                    saved = true,
                    keyValue = "",
                )
                is ByokSaveOutcome.Failed -> _state.value.copy(
                    saving = false,
                    errorToast = "BYOK save failed: ${outcome.reason}",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorToast = null)
    }

    fun clearSaved() {
        _state.value = _state.value.copy(saved = false)
    }

    companion object {
        /** Length-delta threshold above which we treat onValueChange as a paste. */
        const val PASTE_DELTA_THRESHOLD: Int = 20
    }
}

data class ByokState(
    val provider: String = "",
    val keyValue: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val pasteToastVisible: Boolean = false,
    val errorToast: String? = null,
)

/** Supported BYOK providers (UI dropdown). */
object ByokProviders {
    const val OPENROUTER: String = "openrouter"
    const val ANTHROPIC: String = "anthropic"
    const val GEMINI: String = "gemini"
    const val GROQ: String = "groq"

    val ALL: List<String> = listOf(OPENROUTER, ANTHROPIC, GEMINI, GROQ)
}
