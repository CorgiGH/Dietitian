package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Today's macro summary for Home — kcal + protein + carbs + fat with NEUTRAL chips
 * ("above/below target by N"). NEVER red/green per Round-3 §3 ED-safeguard.
 *
 * Full 84-nutrient bars live in MealDetail (Batch D Task 16). Home shows the top-4
 * macros only — the user's daily mental-model anchor.
 *
 * testTag selectors per Batch B brief: `home-today-nutrients`. Inner per-macro chip
 * tags follow `home-nutrient-chip-{macro}` for click-smoke addressing.
 */
@Composable
fun TodayNutrientsCard(state: TodayNutrientsState) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("home-today-nutrients")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Today's nutrients")
            MacroRow("kcal", state.kcal, state.kcalTarget)
            MacroRow("protein", state.proteinG, state.proteinTargetG, unit = "g")
            MacroRow("carbs", state.carbsG, state.carbsTargetG, unit = "g")
            MacroRow("fat", state.fatG, state.fatTargetG, unit = "g")
        }
    }
}

@Composable
private fun MacroRow(name: String, actual: Double, target: Double, unit: String = "") {
    val label = formatMacroLabel(name, actual, target, unit)
    Text(text = label, modifier = Modifier.testTag("home-nutrient-chip-$name"))
}

/** Visible-for-test — formats a single macro line with neutral chip semantics. */
fun formatMacroLabel(name: String, actual: Double, target: Double, unit: String): String {
    val delta = actual - target
    val pct = if (target > 0.0) ((delta / target) * 100.0).toInt() else 0
    return when {
        target <= 0.0 -> "$name ${actual.toInt()}$unit"
        delta >= 0.0 -> "$name ${actual.toInt()}$unit — above target by ${pct}%"
        else -> "$name ${actual.toInt()}$unit — below target by ${-pct}%"
    }
}

/** Today's macro state. Sourced from Plan-1 ledger by HomeViewModel. */
data class TodayNutrientsState(
    val kcal: Double = 0.0,
    val kcalTarget: Double = 0.0,
    val proteinG: Double = 0.0,
    val proteinTargetG: Double = 0.0,
    val carbsG: Double = 0.0,
    val carbsTargetG: Double = 0.0,
    val fatG: Double = 0.0,
    val fatTargetG: Double = 0.0,
)
