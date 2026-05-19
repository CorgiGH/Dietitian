package com.dietician.shared.ui.settings

import com.dietician.shared.ui.i18n.AppLocale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Platform-specific JSON-file persistence for [SettingsState]. Survives app
 * restarts — onboarded flag, locale, theme, accessible-typography, coach-disabled,
 * AI literacy ack-version all reload on next boot.
 *
 * Desktop: `<userhome>/.dietician/settings.json` (Win: `%APPDATA%/Dietician/`).
 * Android: `context.filesDir/settings.json`.
 *
 * Format: single-record JSON object matching [PersistedSettings]. Forwards-
 * compatible: unknown fields are ignored (kotlinx-serialization `ignoreUnknownKeys`).
 */
expect class SettingsPersistence() {
    fun load(): SettingsState?
    fun save(state: SettingsState)
}

/**
 * Wire shape on disk. `locale` stored as the language tag (en/ro) since AppLocale
 * is a sealed-ish enum + kotlinx-serialization sealed-class wiring is heavier than
 * the round-trip via [AppLocale.fromTag].
 */
@Serializable
internal data class PersistedSettings(
    val localeTag: String = "en",
    val darkTheme: Boolean = false,
    val useAccessibleTypography: Boolean = false,
    val coachDisabled: Boolean = false,
    val onboarded: Boolean = false,
    val aiLiteracyAckedVersion: String? = null,
)

internal val SettingsJson: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

internal fun PersistedSettings.toState(): SettingsState = SettingsState(
    locale = AppLocale.fromTag(localeTag),
    darkTheme = darkTheme,
    useAccessibleTypography = useAccessibleTypography,
    coachDisabled = coachDisabled,
    onboarded = onboarded,
    aiLiteracyAckedVersion = aiLiteracyAckedVersion,
)

internal fun SettingsState.toPersisted(): PersistedSettings = PersistedSettings(
    localeTag = locale.code,
    darkTheme = darkTheme,
    useAccessibleTypography = useAccessibleTypography,
    coachDisabled = coachDisabled,
    onboarded = onboarded,
    aiLiteracyAckedVersion = aiLiteracyAckedVersion,
)
