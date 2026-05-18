package com.dietician.shared.ui.screens

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FoodLog screen state holder.
 *
 * - [voiceToastVisible] flips true when the user taps VoiceRecordButton (RC1 toast
 *   fallback). After [TOAST_DURATION_MS] the host clears it (Compose `LaunchedEffect`
 *   `delay(...) + clearVoiceToast()`).
 * - [coachDisabledNoticeVisible] (RC9) ALL surfaces that suggest AI-driven foods show
 *   the coach-disabled notice when [coachDisabled] = true. FoodLog itself does NOT
 *   AI-suggest, but PhotoSuggestionCard (Batch D) and "Same as recent — AI ranking"
 *   (Batch D) both consult this flag.
 * - [manualQuery] mirrors the manual-entry field.
 *
 * Persistent state (e.g. SubjectStore.llm_coach_disabled) loads via [load].
 */
class FoodLogViewModel(
    private val coachDisabledProvider: () -> Boolean = { false },
) {
    private val _state = MutableStateFlow(FoodLogState())
    val state: StateFlow<FoodLogState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(coachDisabled = coachDisabledProvider())
    }

    fun onVoiceTap() {
        // RC1 — voice not yet available; show toast + focus manual field.
        _state.value = _state.value.copy(voiceToastVisible = true)
    }

    fun clearVoiceToast() {
        _state.value = _state.value.copy(voiceToastVisible = false)
    }

    fun onManualQueryChange(q: String) {
        _state.value = _state.value.copy(manualQuery = q)
    }

    fun showSameAsSheet() {
        _state.value = _state.value.copy(sameAsSheetVisible = true)
    }

    fun hideSameAsSheet() {
        _state.value = _state.value.copy(sameAsSheetVisible = false)
    }

    fun onBarcodeTap() {
        // Barcode scanner ships with Plan-6 — toast fallback for now (mirrors RC1 voice pattern).
        _state.value = _state.value.copy(barcodeToastVisible = true)
    }

    fun clearBarcodeToast() {
        _state.value = _state.value.copy(barcodeToastVisible = false)
    }

    companion object {
        const val TOAST_DURATION_MS: Long = 4_000L
    }
}

data class FoodLogState(
    val coachDisabled: Boolean = false,
    val voiceToastVisible: Boolean = false,
    val manualQuery: String = "",
    val sameAsSheetVisible: Boolean = false,
    val barcodeToastVisible: Boolean = false,
)
