package com.dietician.shared.ui.screens

import com.dietician.shared.ui.components.PlannedCutController
import com.dietician.shared.ui.components.PlannedCutState
import com.dietician.shared.ui.components.TodayNutrientsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Home screen state holder. Plan-1 ledger query (today's nutrients) + Plan-3 /me
 * load (subject identity) wire in via the [HomeLoader] interface — kept abstract so
 * Batch B can ship without hard-binding to Plan-1's `MealEventStore` query path
 * (which lands in Batch C Task 10 alongside Pantry).
 *
 * Voyager `ScreenModel` integration deferred to Batch E Task 26 — for Batch B
 * tests construct directly. Subject id stays empty until [load] is invoked.
 */
class HomeViewModel(
    private val loader: HomeLoader,
    val plannedCutController: PlannedCutController = PlannedCutController(),
) {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    val plannedCut: StateFlow<PlannedCutState> = plannedCutController.state

    suspend fun load() {
        val me = loader.loadMe()
        val nutrients = loader.loadTodayNutrients(me.subjectId)
        _state.value = HomeState(
            displayName = me.displayName,
            subjectId = me.subjectId,
            nutrients = nutrients,
            loaded = true,
        )
    }

    fun togglePlannedCut(active: Boolean) {
        if (active) plannedCutController.activate() else plannedCutController.deactivate()
    }

    fun tickPlannedCut() {
        plannedCutController.tickFromClock()
    }
}

/** Snapshot of Home screen state. */
data class HomeState(
    val displayName: String = "",
    val subjectId: String = "",
    val nutrients: TodayNutrientsState = TodayNutrientsState(),
    val loaded: Boolean = false,
)

/** Abstraction over Plan-3 /me + Plan-1 ledger query. Real impls land in Batch C/E. */
interface HomeLoader {
    suspend fun loadMe(): MeProfile
    suspend fun loadTodayNutrients(subjectId: String): TodayNutrientsState
}

data class MeProfile(
    val subjectId: String,
    val displayName: String,
    val trialQueriesRemaining: Int = 0,
    val hasByok: Boolean = false,
)
