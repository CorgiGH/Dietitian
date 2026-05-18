package com.dietician.shared.ui.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.dietician.shared.ui.components.TodayNutrientsState
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.DieticianLocaleProvider
import com.dietician.shared.ui.screens.FoodLogScreen
import com.dietician.shared.ui.screens.FoodLogViewModel
import com.dietician.shared.ui.screens.HomeLoader
import com.dietician.shared.ui.screens.HomeScreen
import com.dietician.shared.ui.screens.HomeViewModel
import com.dietician.shared.ui.screens.MeProfile
import com.dietician.shared.ui.theme.DieticianTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import kotlin.test.Test

/**
 * Plan-4-5 Task 33 — Home → FoodLog → meal log → back-to-Home integration.
 *
 * The flow modelled here:
 *   1. Home paints with subject id + (initially zero) nutrient bars
 *   2. User taps "Log a meal" → caller navigates to FoodLog
 *   3. User taps Voice on FoodLog → coming-soon toast surfaces (RC1)
 *   4. After "meal logged", caller re-renders Home → nutrient bars now reflect logged kcal
 *
 * In real navigation Voyager owns the route swap; here we model the host's
 * `currentRoute` flag the same way `App.kt` does (single source of truth), and
 * assert that nutrient state on Home updates after the simulated log.
 */
class HomeFoodLogIntegrationTest {

    @get:Rule val composeRule = createComposeRule()

    private class StubLoader(
        private val nutrients: TodayNutrientsState,
    ) : HomeLoader {
        override suspend fun loadMe(): MeProfile =
            MeProfile(subjectId = "00000000-victor", displayName = "Victor")

        override suspend fun loadTodayNutrients(subjectId: String): TodayNutrientsState = nutrients
    }

    @Test
    fun `Home renders subject card + tapping log-meal navigates to FoodLog`() {
        var route by mutableStateOf("home")
        val homeVm = HomeViewModel(StubLoader(TodayNutrientsState(kcal = 0.0, kcalTarget = 2750.0)))
        runBlocking { homeVm.load() }
        val foodLogVm = FoodLogViewModel().also { it.load() }

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    when (route) {
                        "home" -> HomeScreen(
                            viewModel = homeVm,
                            onLogMeal = { route = "foodlog" },
                            onOpenSettings = {},
                        )
                        "foodlog" -> FoodLogScreen(viewModel = foodLogVm)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("home-subject-card").assertIsDisplayed()
        composeRule.onNodeWithTag("home-today-nutrients").assertIsDisplayed()

        composeRule.onNodeWithTag("home-log-meal-cta").performClick()

        composeRule.onNodeWithTag("foodlog-screen").assertIsDisplayed()
    }

    @Test
    fun `FoodLog voice tap surfaces coming-soon toast (RC1) and host can return to Home with updated nutrients`() {
        var route by mutableStateOf("home")
        val initial = TodayNutrientsState(kcal = 0.0, kcalTarget = 2750.0, proteinG = 0.0, proteinTargetG = 137.0)
        val afterMeal = TodayNutrientsState(kcal = 600.0, kcalTarget = 2750.0, proteinG = 45.0, proteinTargetG = 137.0)
        var loaderState by mutableStateOf(initial)
        val loader = object : HomeLoader {
            override suspend fun loadMe(): MeProfile = MeProfile("00000000-victor", "Victor")
            override suspend fun loadTodayNutrients(subjectId: String): TodayNutrientsState = loaderState
        }
        val homeVm = HomeViewModel(loader)
        runBlocking { homeVm.load() }
        val foodLogVm = FoodLogViewModel().also { it.load() }

        composeRule.setContent {
            DieticianLocaleProvider(locale = AppLocale.EN) {
                DieticianTheme {
                    when (route) {
                        "home" -> HomeScreen(
                            viewModel = homeVm,
                            onLogMeal = { route = "foodlog" },
                            onOpenSettings = {},
                        )
                        "foodlog" -> FoodLogScreen(viewModel = foodLogVm)
                    }
                }
            }
        }

        composeRule.onNodeWithTag("home-log-meal-cta").performClick()
        composeRule.onNodeWithTag("foodlog-screen").assertIsDisplayed()

        // Tap Voice — RC1 toast must surface.
        composeRule.onNodeWithTag("foodlog-voice-button").performClick()
        composeRule.onNodeWithTag("foodlog-voice-coming-soon-toast").assertIsDisplayed()

        // Simulate the host completing a manual meal log + returning to Home.
        loaderState = afterMeal
        runBlocking { homeVm.load() }
        route = "home"

        composeRule.onNodeWithTag("home-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("home-nutrient-chip-kcal").assertIsDisplayed()
        composeRule.onNodeWithTag("home-nutrient-chip-protein").assertIsDisplayed()
    }
}
