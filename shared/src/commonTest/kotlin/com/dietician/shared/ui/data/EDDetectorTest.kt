package com.dietician.shared.ui.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EDDetectorTest {

    // --- EDDetectorRules ---

    @Test
    fun `kcal target 1400 hard refuses (below 1500 floor)`() {
        assertEquals(EDRuleVerdict.HardRefuse, EDDetectorRules.checkKcalTarget(1400))
    }

    @Test
    fun `kcal target 1700 soft warns (below 1800 soft floor)`() {
        assertEquals(EDRuleVerdict.SoftWarn, EDDetectorRules.checkKcalTarget(1700))
    }

    @Test
    fun `kcal target 2200 allows`() {
        assertEquals(EDRuleVerdict.Allow, EDDetectorRules.checkKcalTarget(2200))
    }

    @Test
    fun `weight rate 1_0 kg per wk hard refuses`() {
        assertEquals(EDRuleVerdict.HardRefuse, EDDetectorRules.checkWeightRate(1.0))
    }

    @Test
    fun `weight rate 0_6 kg per wk soft warns`() {
        assertEquals(EDRuleVerdict.SoftWarn, EDDetectorRules.checkWeightRate(0.6))
    }

    @Test
    fun `weight rate 0_3 kg per wk allows`() {
        assertEquals(EDRuleVerdict.Allow, EDDetectorRules.checkWeightRate(0.3))
    }

    @Test
    fun `body-comparison feature always hard refuses`() {
        assertEquals(EDRuleVerdict.HardRefuse, EDDetectorRules.checkBodyComparisonFeatureRequest())
    }

    // --- EDDetectorHook ---

    @Test
    fun `hook with no rule trips returns null`() {
        val state = EDState(
            kcalTarget = 2200,
            daysBelow80pct = 0,
            weightRateKgPerWeek = 0.1,
            restrictivePatternTriggers = 0,
        )
        assertNull(EDDetectorHook.shouldShowCheckIn(state))
    }

    @Test
    fun `hook surfaces KcalFloorBreach when 3d below 80pct + soft floor`() {
        val state = EDState(
            kcalTarget = 1700,
            daysBelow80pct = 3,
            weightRateKgPerWeek = 0.0,
        )
        val flag = EDDetectorHook.shouldShowCheckIn(state)
        assertNotNull(flag)
        assertTrue(flag is EDFlag.KcalFloorBreach)
        assertEquals(EDRuleVerdict.SoftWarn, flag.severity)
    }

    @Test
    fun `hook surfaces hard refuse when kcal lt 1500 + 3d streak`() {
        val state = EDState(
            kcalTarget = 1400,
            daysBelow80pct = 3,
        )
        val flag = EDDetectorHook.shouldShowCheckIn(state)
        assertNotNull(flag)
        assertEquals(EDRuleVerdict.HardRefuse, flag.severity)
    }

    @Test
    fun `hook surfaces WeightRateExcessive when sustained loss above floor`() {
        val state = EDState(weightRateKgPerWeek = 0.7)
        val flag = EDDetectorHook.shouldShowCheckIn(state)
        assertNotNull(flag)
        assertTrue(flag is EDFlag.WeightRateExcessive)
    }

    @Test
    fun `hook surfaces RestrictivePattern when triggers cross threshold`() {
        val state = EDState(
            kcalTarget = 2000,
            daysBelow80pct = 0,
            weightRateKgPerWeek = 0.0,
            restrictivePatternTriggers = 6,
        )
        val flag = EDDetectorHook.shouldShowCheckIn(state)
        assertNotNull(flag)
        assertTrue(flag is EDFlag.RestrictivePattern)
    }

    @Test
    fun `RC14 plannedCut active skips soft warnings`() {
        val state = EDState(
            kcalTarget = 1700,
            daysBelow80pct = 3,
            plannedCutActive = true,
        )
        assertNull(EDDetectorHook.shouldShowCheckIn(state))
    }

    @Test
    fun `RC14 plannedCut active does NOT skip hard refuses`() {
        val state = EDState(
            kcalTarget = 1400,
            daysBelow80pct = 3,
            plannedCutActive = true,
        )
        val flag = EDDetectorHook.shouldShowCheckIn(state)
        assertNotNull(flag)
        assertEquals(EDRuleVerdict.HardRefuse, flag.severity)
    }

    @Test
    fun `hook prioritizes kcal floor over weight rate when both trip`() {
        val state = EDState(
            kcalTarget = 1400,
            daysBelow80pct = 3,
            weightRateKgPerWeek = 0.7,
        )
        val flag = EDDetectorHook.shouldShowCheckIn(state)
        assertNotNull(flag)
        assertTrue(flag is EDFlag.KcalFloorBreach)
    }

    @Test
    fun `kcal target below 1500 but only 2d streak does not trip the kcal flag`() {
        val state = EDState(
            kcalTarget = 1400,
            daysBelow80pct = 2,
            weightRateKgPerWeek = 0.0,
        )
        assertNull(EDDetectorHook.shouldShowCheckIn(state))
    }
}
