package com.dietician.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Neutral nutrient chip: text-only above/below target label. NO emoji, NO color
 * signal. Round-3 §3 ED-safeguard hard ban — no red/green pass/fail.
 *
 * Font weight is the only contrast — "above" bolds, "below" stays normal (or
 * vice versa, low-contrast). Background is the Material 3 `surfaceVariant` — same
 * neutral tone whether above or below.
 *
 * Usage:
 * ```kotlin
 * NutrientChip(nutrientId = "iron", label = "12 mg — above target by 8%")
 * ```
 *
 * testTag selector: `nutrient-chip-{id}` per Batch B brief.
 */
@Composable
fun NutrientChip(
    nutrientId: String,
    label: String,
    isAbove: Boolean = false,
) {
    Text(
        text = label,
        fontWeight = if (isAbove) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("nutrient-chip-$nutrientId"),
    )
}
