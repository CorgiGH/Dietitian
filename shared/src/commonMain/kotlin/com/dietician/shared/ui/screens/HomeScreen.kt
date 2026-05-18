package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.PlannedCutToggle
import com.dietician.shared.ui.components.SubjectCard
import com.dietician.shared.ui.components.TodayNutrientsCard
import com.dietician.shared.ui.i18n.strings

/**
 * Home landing screen — top-to-bottom layout per Batch B brief:
 * 1. SubjectCard — name + truncated id + Settings button
 * 2. AdaptiveExpenditurePreview — Bayesian rolling 7-day TDEE placeholder
 *    (real chart in Batch D Task 19)
 * 3. TodayNutrientsCard — kcal/protein/carbs/fat NEUTRAL chips (NO red/green)
 * 4. PlannedCutToggle (RC14) — 7-day window switch + countdown
 * 5. "Log a meal" big CTA → navigates to FoodLog
 *
 * testTag selectors per Batch B brief: home-subject-card, home-expenditure-preview,
 * home-today-nutrients, home-planned-cut-toggle, home-planned-cut-days-remaining,
 * home-log-meal-cta.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogMeal: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val plannedCut by viewModel.plannedCut.collectAsState()
    val s = strings()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .testTag("home-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubjectCard(
            displayName = state.displayName.ifBlank { "—" },
            subjectId = state.subjectId.ifBlank { "————" },
            onOpenSettings = onOpenSettings,
        )
        AdaptiveExpenditurePreview()
        TodayNutrientsCard(state.nutrients)
        PlannedCutToggle(
            state = plannedCut,
            onToggle = viewModel::togglePlannedCut,
        )
        Button(
            onClick = onLogMeal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("home-log-meal-cta"),
        ) {
            Text(s.home_log_meal_cta)
        }
    }
}

/**
 * Empty-state placeholder for the MacroFactor-style Bayesian rolling 7-day TDEE chart
 * shipping in Batch D Task 19. Reads i18n copy so the surface looks like a user-facing
 * empty state (not a TODO marker). Same testTag so Compose UI tests keep matching.
 */
@Composable
private fun AdaptiveExpenditurePreview() {
    val s = strings()
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("home-expenditure-preview")) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = s.expenditure_preview_empty_title)
            Text(text = s.expenditure_preview_empty_body)
        }
    }
}
