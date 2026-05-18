package com.dietician.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodayNutrientsTest {

    @Test
    fun `above target produces 'above target' label`() {
        val label = formatMacroLabel("protein", actual = 220.0, target = 200.0, unit = "g")
        assertTrue(label.contains("above target"))
        assertTrue(label.contains("10%"))
    }

    @Test
    fun `below target produces 'below target' label`() {
        val label = formatMacroLabel("kcal", actual = 1800.0, target = 2000.0, unit = "")
        assertTrue(label.contains("below target"))
        assertTrue(label.contains("10%"))
    }

    @Test
    fun `no red or green color words in label (RC ED-safeguard)`() {
        // Round-3 §3 hard ban — no red/green pass-fail semantics anywhere.
        val labelOver = formatMacroLabel("kcal", 2500.0, 2000.0, "")
        val labelUnder = formatMacroLabel("kcal", 1500.0, 2000.0, "")
        for (label in listOf(labelOver, labelUnder)) {
            assertFalse(label.contains("red", ignoreCase = true))
            assertFalse(label.contains("green", ignoreCase = true))
            assertFalse(label.contains("fail", ignoreCase = true))
            assertFalse(label.contains("pass", ignoreCase = true))
            assertFalse(label.contains("warning", ignoreCase = true))
        }
    }

    @Test
    fun `target zero falls back to neutral count`() {
        val label = formatMacroLabel("alcohol", 0.0, 0.0, "g")
        assertEquals("alcohol 0g", label)
    }

    @Test
    fun `exactly on target reads above-target-by-zero (neutral, no special case)`() {
        // Equal-to-target is mathematically delta=0. No celebratory styling per ED-safeguard.
        val label = formatMacroLabel("fat", 60.0, 60.0, "g")
        assertTrue(label.contains("above target by 0%"))
    }
}
