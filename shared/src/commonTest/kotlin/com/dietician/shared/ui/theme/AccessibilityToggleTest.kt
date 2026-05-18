package com.dietician.shared.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityToggleTest {
    @Test
    fun `default is system font`() {
        val state = AccessibilityState()
        assertFalse(state.accessibleTypography.value, "default toggle should be false (system font)")
    }

    @Test
    fun `initial state honoured`() {
        val state = AccessibilityState(initialAccessibleTypography = true)
        assertTrue(state.accessibleTypography.value)
    }

    @Test
    fun `flip toggle round-trips`() {
        val state = AccessibilityState()
        state.accessibleTypography.value = true
        assertTrue(state.accessibleTypography.value)
        state.accessibleTypography.value = false
        assertFalse(state.accessibleTypography.value)
    }

    @Test
    fun `independent instances do not share state`() {
        val a = AccessibilityState(initialAccessibleTypography = true)
        val b = AccessibilityState(initialAccessibleTypography = false)
        assertEquals(true, a.accessibleTypography.value)
        assertEquals(false, b.accessibleTypography.value)
        a.accessibleTypography.value = false
        assertEquals(false, a.accessibleTypography.value)
        assertEquals(false, b.accessibleTypography.value)
    }
}
