package com.dietician.shared.ui.screens

import com.dietician.shared.ui.components.PlannedCutController
import com.dietician.shared.ui.components.TodayNutrientsState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {

    private class FakeLoader(
        private val me: MeProfile = MeProfile("victor-uuid", "Victor", 50, false),
        private val nutrients: TodayNutrientsState = TodayNutrientsState(
            kcal = 2500.0,
            kcalTarget = 2700.0,
            proteinG = 170.0,
            proteinTargetG = 160.0,
            carbsG = 270.0,
            carbsTargetG = 300.0,
            fatG = 95.0,
            fatTargetG = 90.0,
        ),
    ) : HomeLoader {
        override suspend fun loadMe() = me
        override suspend fun loadTodayNutrients(subjectId: String) = nutrients
    }

    @Test
    fun `initial state is empty and not loaded`() {
        val vm = HomeViewModel(FakeLoader())
        assertFalse(vm.state.value.loaded)
        assertEquals("", vm.state.value.displayName)
    }

    @Test
    fun `load populates state from loader`() = runTest {
        val vm = HomeViewModel(FakeLoader())
        vm.load()
        assertTrue(vm.state.value.loaded)
        assertEquals("Victor", vm.state.value.displayName)
        assertEquals("victor-uuid", vm.state.value.subjectId)
        assertEquals(2500.0, vm.state.value.nutrients.kcal)
    }

    @Test
    fun `togglePlannedCut on triggers controller activation`() {
        var activated = 0
        val vm = HomeViewModel(
            loader = FakeLoader(),
            plannedCutController = PlannedCutController(
                clockNowMs = { 0L },
                onActivate = { activated++ },
            ),
        )
        vm.togglePlannedCut(active = true)
        assertEquals(1, activated)
        assertTrue(vm.plannedCut.value.active)
    }

    @Test
    fun `togglePlannedCut off after on deactivates`() {
        val vm = HomeViewModel(
            loader = FakeLoader(),
            plannedCutController = PlannedCutController(clockNowMs = { 0L }),
        )
        vm.togglePlannedCut(active = true)
        vm.togglePlannedCut(active = false)
        assertFalse(vm.plannedCut.value.active)
    }
}
