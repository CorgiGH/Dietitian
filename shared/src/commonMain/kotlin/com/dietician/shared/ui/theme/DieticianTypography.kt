package com.dietician.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dietician.shared.generated.resources.Res
import dietician.shared.generated.resources.atkinson_hyperlegible_regular
import org.jetbrains.compose.resources.Font

/**
 * Typography for Dietician.
 *
 * Default = system font. Toggle "High legibility" in Settings → swaps to
 * Atkinson Hyperlegible (Braille Institute, SIL OFL 1.1). The runtime read
 * is via [LocalUseAccessibleTypography]; the swap happens centrally in
 * [DieticianTheme] via [dieticianTypography].
 *
 * Settings storage is via Plan-1 `cache_metadata` (later task wires this in).
 */
@Composable
internal fun dieticianTypography(useAccessibleTypography: Boolean = false): Typography {
    val family = if (useAccessibleTypography) AtkinsonHyperlegibleFamily() else FontFamily.Default
    val base = Typography()
    fun TextStyle.withFamily(): TextStyle = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily().copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.withFamily().copy(fontSize = 14.sp),
        bodySmall = base.bodySmall.withFamily().copy(fontSize = 12.sp),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily(),
    )
}

/**
 * Atkinson Hyperlegible font family. Composable wrapper required because
 * Compose MP [Font] resource-loading is a @Composable in this version.
 */
@Composable
internal fun AtkinsonHyperlegibleFamily(): FontFamily =
    FontFamily(
        Font(
            resource = Res.font.atkinson_hyperlegible_regular,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
        ),
    )

/**
 * CompositionLocal — true when user enabled "high legibility" toggle in
 * Settings. Read by components that want to opt into Atkinson Hyperlegible
 * weight beyond the global typography swap.
 */
val LocalUseAccessibleTypography = staticCompositionLocalOf { false }
