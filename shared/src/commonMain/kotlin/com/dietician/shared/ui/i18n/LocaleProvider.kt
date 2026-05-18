package com.dietician.shared.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal carrier for the active app locale.
 *
 * Default = EN (overridden by [DieticianLocaleProvider] at the theme root
 * based on user preference / system locale).
 *
 * Read in @Composable functions via `LocalAppLocale.current.strings`.
 */
val LocalAppLocale: ProvidableCompositionLocal<AppLocale> = staticCompositionLocalOf { AppLocale.EN }

/**
 * Provider Composable that scopes its [content] to the given [locale].
 * Settings screen (later task) reads from Plan-1 `cache_metadata` and passes
 * the persisted choice in; default = system locale, falls back to EN.
 */
@Composable
fun DieticianLocaleProvider(
    locale: AppLocale,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppLocale provides locale, content = content)
}

/**
 * Resolve the current locale's [Strings] bundle inside a @Composable.
 *
 * Usage:
 * ```
 * val s = strings()
 * Text(s.bigorexia_strength_focus)
 * ```
 */
@Composable
fun strings(): Strings = LocalAppLocale.current.strings
