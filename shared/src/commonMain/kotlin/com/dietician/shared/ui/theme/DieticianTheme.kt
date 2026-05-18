package com.dietician.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Dietician theme entry point. Wraps Material 3 [MaterialTheme] with project
 * tokens. Respects [darkTheme] flag (default from system).
 *
 * Accessibility-toggle hook: typography is sourced from [DieticianTypographyProvider]
 * via [LocalUseAccessibleTypography]; default = system font. See [AccessibilityToggle].
 */
@Composable
fun DieticianTheme(
    darkTheme: Boolean = false,
    useAccessibleTypography: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DieticianDarkColorScheme else DieticianLightColorScheme
    val typography = dieticianTypography(useAccessibleTypography = useAccessibleTypography)
    CompositionLocalProvider(LocalUseAccessibleTypography provides useAccessibleTypography) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = DieticianShapes,
            content = content,
        )
    }
}
