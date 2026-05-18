package com.dietician.shared.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodLogViewModelTest {

    @Test
    fun `initial state is empty + toast hidden`() {
        val vm = FoodLogViewModel()
        assertFalse(vm.state.value.voiceToastVisible)
        assertFalse(vm.state.value.coachDisabled)
        assertEquals("", vm.state.value.manualQuery)
        assertFalse(vm.state.value.sameAsSheetVisible)
    }

    @Test
    fun `onVoiceTap shows toast (RC1)`() {
        val vm = FoodLogViewModel()
        vm.onVoiceTap()
        assertTrue(vm.state.value.voiceToastVisible)
    }

    @Test
    fun `clearVoiceToast hides toast`() {
        val vm = FoodLogViewModel()
        vm.onVoiceTap()
        vm.clearVoiceToast()
        assertFalse(vm.state.value.voiceToastVisible)
    }

    @Test
    fun `load picks up coachDisabled from provider (RC9)`() {
        val vm = FoodLogViewModel(coachDisabledProvider = { true })
        vm.load()
        assertTrue(vm.state.value.coachDisabled)
    }

    @Test
    fun `load with coach enabled keeps notice hidden`() {
        val vm = FoodLogViewModel(coachDisabledProvider = { false })
        vm.load()
        assertFalse(vm.state.value.coachDisabled)
    }

    @Test
    fun `onManualQueryChange propagates`() {
        val vm = FoodLogViewModel()
        vm.onManualQueryChange("sarmale")
        assertEquals("sarmale", vm.state.value.manualQuery)
    }

    @Test
    fun `showSameAsSheet + hideSameAsSheet round-trip`() {
        val vm = FoodLogViewModel()
        vm.showSameAsSheet()
        assertTrue(vm.state.value.sameAsSheetVisible)
        vm.hideSameAsSheet()
        assertFalse(vm.state.value.sameAsSheetVisible)
    }
}
