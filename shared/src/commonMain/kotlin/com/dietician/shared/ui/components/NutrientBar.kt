package com.dietician.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.Nutrient
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.LocalAppLocale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cronometer-style horizontal nutrient bar with target marker (Round-3 §1.13 #1 +
 * spec §6.4). Fill color is NEUTRAL primary (NEVER red/green per ED-safeguard).
 *
 * Layout:
 * ```
 * [Iron               ] [████████████░░░░░░░░] [12 mg / 8 mg]   above by 50%
 * ```
 * - Left: nutrient name (locale-resolved via [LocalAppLocale])
 * - Center: filled bar from 0 -> actual; target marker line at target/cap
 * - Right: numeric `actual unit / target unit`
 * - Below: [NutrientChip] with above/below text
 *
 * The bar caps at 100% of the visualization range (`displayCapMultiplier` ×
 * target, default 1.5×) — visual cap ONLY, no semantic ceiling (the chip below
 * shows the actual % delta).
 *
 * testTag selector: `nutrient-bar-{id}` per Batch B brief.
 */
@Composable
fun NutrientBar(
    nutrient: Nutrient,
    actual: Double,
    target: Double = nutrient.defaultRdaForVictor,
    displayCapMultiplier: Double = 1.5,
) {
    val locale = LocalAppLocale.current
    val name = if (locale == AppLocale.RO) nutrient.displayNameRo else nutrient.displayNameEn
    val displayCap = (target * displayCapMultiplier).coerceAtLeast(1.0)
    val fillFraction = (actual / displayCap).coerceIn(0.0, 1.0).toFloat()
    val isAbove = actual >= target

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .testTag("nutrient-bar-${nutrient.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, modifier = Modifier.width(120.dp))
            BarTrack(fillFraction = fillFraction, modifier = Modifier.weight(1f))
            Text(
                text = "${actual.toInt()}/${target.toInt()} ${nutrient.unit.display}",
                modifier = Modifier.padding(start = 8.dp).width(80.dp),
            )
        }
        val deltaPct = if (target > 0.0) (((actual - target) / target) * 100.0).toInt() else 0
        val label = when {
            target <= 0.0 -> "${actual.toInt()} ${nutrient.unit.display}"
            isAbove -> "above target by ${deltaPct}%"
            else -> "below target by ${-deltaPct}%"
        }
        NutrientChip(nutrientId = nutrient.id, label = label, isAbove = isAbove)
    }
}

@Composable
private fun BarTrack(fillFraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = fillFraction)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                ),
        )
    }
}

/**
 * Compute the fill fraction for the bar (visible-for-test, side-effect-free).
 * Used by the renderer above; exposed so unit tests can verify fraction math
 * without needing to mount the Compose tree.
 */
fun nutrientBarFillFraction(actual: Double, target: Double, displayCapMultiplier: Double = 1.5): Float {
    val cap = (target * displayCapMultiplier).coerceAtLeast(1.0)
    return (actual / cap).coerceIn(0.0, 1.0).toFloat()
}
