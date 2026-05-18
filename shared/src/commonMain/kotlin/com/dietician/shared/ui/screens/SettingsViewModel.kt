package com.dietician.shared.ui.screens

import com.dietician.shared.Dietician
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.settings.SettingsState
import com.dietician.shared.ui.settings.SettingsStore
import kotlinx.coroutines.flow.StateFlow

/**
 * SettingsScreen state holder. Thin pass-through over [SettingsStore] —
 * the store is the single source of truth so any other consumer
 * ([com.dietician.shared.ui.nav.DieticianApp] for theme + locale,
 * [FoodLogViewModel] / [CoachChatViewModel] for coach-disabled) sees
 * mutations immediately via the same `StateFlow`.
 */
class SettingsViewModel(
    private val store: SettingsStore,
) {
    val state: StateFlow<SettingsState> = store.state

    /** App version + spec-date metadata for the About row. */
    val versionLabel: String = Dietician.VERSION
    val specDateLabel: String = Dietician.SPEC_DATE

    fun setLocale(locale: AppLocale) = store.setLocale(locale)
    fun setDarkTheme(enabled: Boolean) = store.setDarkTheme(enabled)
    fun setUseAccessibleTypography(enabled: Boolean) =
        store.setUseAccessibleTypography(enabled)
    fun setCoachDisabled(disabled: Boolean) = store.setCoachDisabled(disabled)
}
