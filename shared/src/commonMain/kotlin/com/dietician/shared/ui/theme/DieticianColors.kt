/*
 * Dietician design tokens — Material 3 ColorScheme.
 *
 * Council 1779120600 R3 ruling: NO red-green pass/fail axis for nutritional choices.
 * Macro/calorie under-target must NOT render red; over-target NOT green.
 * Use neutral chip + textual label ("below target by N" / "above target by N").
 *
 * Palette:
 * - primary: warm RO-amber (dietary-warm, not appetite-suppressing).
 * - secondary: sage-green NEUTRAL (advisory tone, not "success-green").
 * - background: light/dark variants.
 * - error: reserved for actual errors (network failure, validation), NOT
 *          nutritional over/under-target. Code referencing this colour for
 *          nutrition is a smell — see [SemanticUsage].
 */

package com.dietician.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Brand tokens ---

/** Warm RO-amber — primary. Dietary-warm, comfort-food register. */
internal val DieticianAmber500 = Color(0xFFE8C078)
internal val DieticianAmber700 = Color(0xFFB78A3D)
internal val DieticianAmber200 = Color(0xFFF4DDB3)

/** Sage-green NEUTRAL — secondary. Advisory tone, NOT pass-fail success-green. */
internal val DieticianSage500 = Color(0xFF8FA68E)
internal val DieticianSage700 = Color(0xFF607D5A)
internal val DieticianSage200 = Color(0xFFC4D2C2)

/** Neutral chip surfaces for above/below-target labels. NO red, NO green. */
internal val DieticianNeutral100 = Color(0xFFF4F2EE)
internal val DieticianNeutral300 = Color(0xFFCFCAC1)
internal val DieticianNeutral500 = Color(0xFF8C8579)
internal val DieticianNeutral700 = Color(0xFF4D483E)
internal val DieticianNeutral900 = Color(0xFF221F1A)

/** Background surfaces. */
internal val DieticianBackgroundLight = Color(0xFFFDFBF7)
internal val DieticianSurfaceLight = Color(0xFFFFFFFF)
internal val DieticianBackgroundDark = Color(0xFF181612)
internal val DieticianSurfaceDark = Color(0xFF24211C)

/** Error — RESERVED FOR ACTUAL ERRORS (network/validation), NOT nutrition. */
internal val DieticianErrorRed = Color(0xFFB3261E)
internal val DieticianErrorRedDark = Color(0xFFE5837C)

/**
 * Light ColorScheme. Material 3 token mapping.
 *
 * Banned semantics: this scheme MUST NOT be wired to express
 * "macro under-target" or "macro over-target" via primary/secondary/error.
 * Those are neutral-chip + textual labels only.
 */
val DieticianLightColorScheme: ColorScheme = lightColorScheme(
    primary = DieticianAmber700,
    onPrimary = Color.White,
    primaryContainer = DieticianAmber200,
    onPrimaryContainer = DieticianNeutral900,
    secondary = DieticianSage700,
    onSecondary = Color.White,
    secondaryContainer = DieticianSage200,
    onSecondaryContainer = DieticianNeutral900,
    tertiary = DieticianNeutral700,
    onTertiary = Color.White,
    background = DieticianBackgroundLight,
    onBackground = DieticianNeutral900,
    surface = DieticianSurfaceLight,
    onSurface = DieticianNeutral900,
    surfaceVariant = DieticianNeutral100,
    onSurfaceVariant = DieticianNeutral700,
    outline = DieticianNeutral300,
    error = DieticianErrorRed,
    onError = Color.White,
)

/** Dark ColorScheme. */
val DieticianDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DieticianAmber500,
    onPrimary = DieticianNeutral900,
    primaryContainer = DieticianAmber700,
    onPrimaryContainer = Color.White,
    secondary = DieticianSage500,
    onSecondary = DieticianNeutral900,
    secondaryContainer = DieticianSage700,
    onSecondaryContainer = Color.White,
    tertiary = DieticianNeutral300,
    onTertiary = DieticianNeutral900,
    background = DieticianBackgroundDark,
    onBackground = Color.White,
    surface = DieticianSurfaceDark,
    onSurface = Color.White,
    surfaceVariant = DieticianNeutral700,
    onSurfaceVariant = DieticianNeutral100,
    outline = DieticianNeutral500,
    error = DieticianErrorRedDark,
    onError = DieticianNeutral900,
)

/**
 * Neutral chip colour for above/below-target labels. Use with a text label
 * ("above target by N kcal" / "below target by N g protein"). Never red/green.
 */
object NeutralChip {
    val backgroundLight: Color get() = DieticianNeutral100
    val backgroundDark: Color get() = DieticianNeutral700
    val foregroundLight: Color get() = DieticianNeutral700
    val foregroundDark: Color get() = DieticianNeutral100
}
