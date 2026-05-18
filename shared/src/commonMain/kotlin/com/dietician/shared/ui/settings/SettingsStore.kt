package com.dietician.shared.ui.settings

import com.dietician.shared.ui.i18n.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of user-controllable settings that ripple across the whole app shell.
 *
 * - [locale]: drives [com.dietician.shared.ui.i18n.DieticianLocaleProvider].
 * - [darkTheme]: drives [com.dietician.shared.ui.theme.DieticianTheme] color scheme.
 * - [useAccessibleTypography]: drives Atkinson Hyperlegible typography swap (RC5).
 * - [coachDisabled] (RC9): when true, every surface that suggests AI-driven foods
 *   shows the coach-disabled notice + Coach send/just-tell-me become no-ops.
 *
 * Persistence stub: in-memory only for now. Real impl (when Plan-1/3 ship the
 * subject_settings table) replaces [InMemorySettingsStore] with a SQLDelight
 * or HTTP-backed adapter via the Koin binding.
 */
data class SettingsState(
    val locale: AppLocale = AppLocale.EN,
    val darkTheme: Boolean = false,
    val useAccessibleTypography: Boolean = false,
    val coachDisabled: Boolean = false,
    val onboarded: Boolean = false,
    val aiLiteracyAckedVersion: String? = null,
)

/**
 * Single-writer-per-process settings store. Consumed by `DieticianApp` (to
 * propagate locale + theme + accessibility), Settings UI (to read/write
 * toggles), and CoachChatViewModel / FoodLogViewModel (to read coachDisabled
 * via the existing `coachDisabledProvider: () -> Boolean` indirection).
 */
interface SettingsStore {
    val state: StateFlow<SettingsState>

    fun setLocale(locale: AppLocale)
    fun setDarkTheme(enabled: Boolean)
    fun setUseAccessibleTypography(enabled: Boolean)
    fun setCoachDisabled(disabled: Boolean)
    fun markOnboarded()
    fun setAiLiteracyAckedVersion(version: String?)
}

class InMemorySettingsStore(initial: SettingsState = SettingsState()) : SettingsStore {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    override fun setLocale(locale: AppLocale) {
        _state.value = _state.value.copy(locale = locale)
    }

    override fun setDarkTheme(enabled: Boolean) {
        _state.value = _state.value.copy(darkTheme = enabled)
    }

    override fun setUseAccessibleTypography(enabled: Boolean) {
        _state.value = _state.value.copy(useAccessibleTypography = enabled)
    }

    override fun setCoachDisabled(disabled: Boolean) {
        _state.value = _state.value.copy(coachDisabled = disabled)
    }

    override fun markOnboarded() {
        _state.value = _state.value.copy(onboarded = true)
    }

    override fun setAiLiteracyAckedVersion(version: String?) {
        _state.value = _state.value.copy(aiLiteracyAckedVersion = version)
    }
}
