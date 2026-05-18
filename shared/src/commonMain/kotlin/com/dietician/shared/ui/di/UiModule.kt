package com.dietician.shared.ui.di

import com.dietician.shared.ui.components.PlannedCutController
import com.dietician.shared.ui.components.TodayNutrientsState
import com.dietician.shared.ui.screens.HomeLoader
import com.dietician.shared.ui.screens.HomeViewModel
import com.dietician.shared.ui.screens.MeProfile
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared `uiModule` registering ViewModels + the data interfaces they consume.
 *
 * **Scope (nav-mount-fix iteration 1):** only Home is wired to a real screen via
 * Koin. Other tabs still render `PlaceholderScreen("...")` in [Routes.kt] until
 * follow-up iterations land their stubs / impls. This is on purpose: every
 * tab gets a smoke-walk-verified mount before adding the next, so we don't
 * repeat the 2026-05-11 Slice 1 ghost-component pattern at scale.
 *
 * [StubHomeLoader] returns hardcoded subject info + an empty
 * [TodayNutrientsState] so HomeScreen paints the user-facing empty state
 * ("Building your estimate" / neutral nutrient chips) instead of crashing on
 * a missing dependency. Real impl lands when Plan-3 `/me` adapter +
 * Plan-1 `MealEventStore.todayNutrients` query are wired.
 */
val uiModule: Module = module {
    single<HomeLoader> { StubHomeLoader() }
    factory { HomeViewModel(loader = get(), plannedCutController = PlannedCutController()) }
}

/**
 * Smoke-walk stub for [HomeLoader]. Returns deterministic placeholder data
 * so HomeScreen renders its real composables without needing the Plan-1
 * MealEventStore or Plan-3 `/me` endpoint live. Replace with the HTTP +
 * ledger-backed impl when those wires arrive.
 */
private class StubHomeLoader : HomeLoader {
    override suspend fun loadMe(): MeProfile = MeProfile(
        subjectId = "stub-subject-0000",
        displayName = "Victor",
    )

    override suspend fun loadTodayNutrients(subjectId: String): TodayNutrientsState =
        TodayNutrientsState()
}
