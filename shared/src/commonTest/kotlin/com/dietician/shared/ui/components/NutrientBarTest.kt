package com.dietician.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NutrientBarTest {

    @Test
    fun `actual zero produces fraction zero`() {
        val f = nutrientBarFillFraction(actual = 0.0, target = 100.0)
        assertEquals(0f, f)
    }

    @Test
    fun `actual at target produces fraction below 1`() {
        // Default cap is 1.5x target — at target, fill is 2/3.
        val f = nutrientBarFillFraction(actual = 100.0, target = 100.0)
        assertTrue(f in 0.65f..0.68f, "expected ~0.666, got $f")
    }

    @Test
    fun `actual above cap clamps to 1`() {
        val f = nutrientBarFillFraction(actual = 1_000.0, target = 100.0)
        assertEquals(1f, f)
    }

    @Test
    fun `target zero does not divide by zero`() {
        // Cap coerced to 1.0 → fill = actual / 1.0 clamped to 1.0.
        val f = nutrientBarFillFraction(actual = 0.5, target = 0.0)
        assertEquals(0.5f, f)
    }

    @Test
    fun `custom multiplier widens cap`() {
        // At 2.0x cap, target=100 → cap=200 → actual=100 → fill = 0.5.
        val f = nutrientBarFillFraction(actual = 100.0, target = 100.0, displayCapMultiplier = 2.0)
        assertEquals(0.5f, f)
    }
}
