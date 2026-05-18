package com.dietician.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Placeholder typography. Task 2 replaces this with a full type scale +
 * Atkinson Hyperlegible toggle. Kept as a stub here so [DieticianTheme]
 * compiles after Task 1 lands.
 *
 * @param useAccessibleTypography when true, swap to Atkinson Hyperlegible at
 *   runtime (wired in Task 2).
 */
internal fun dieticianTypography(useAccessibleTypography: Boolean = false): Typography =
    Typography().also {
        @Suppress("UNUSED_EXPRESSION")
        useAccessibleTypography // referenced for Task 2 wiring; default Typography for now
    }

/**
 * CompositionLocal — true when user enabled "high legibility" toggle in
 * Settings. Read by components that want to opt into Atkinson Hyperlegible
 * weight beyond the global typography swap.
 */
val LocalUseAccessibleTypography = staticCompositionLocalOf { false }
