package com.dietician.shared.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoutesTest {

    @Test
    fun `screen keys are stable and unique among bottom nav`() {
        val keys = BottomNavScreens.map { it.key }
        assertEquals(keys.toSet().size, keys.size, "duplicate keys in BottomNavScreens: $keys")
        assertEquals(
            listOf("home", "food-log", "pantry", "coach-chat", "settings"),
            keys,
            "spec'd 5-tab order: home / food log / pantry / coach / settings",
        )
    }

    @Test
    fun `all top-level screen keys are stable strings`() {
        assertEquals("home", DieticianScreen.Home.key)
        assertEquals("food-log", DieticianScreen.FoodLog.key)
        assertEquals("pantry", DieticianScreen.Pantry.key)
        assertEquals("cookbook", DieticianScreen.Cookbook.key)
        assertEquals("coach-chat", DieticianScreen.CoachChat.key)
        assertEquals("settings", DieticianScreen.Settings.key)
        assertEquals("audit-log", DieticianScreen.AuditLog.key)
    }

    @Test
    fun `meal-detail screen embeds id in key`() {
        val s = DieticianScreen.MealDetail("abc-123")
        assertEquals("meal-detail/abc-123", s.key)
    }

    @Test
    fun `two meal-detail screens with different ids have different keys`() {
        assertNotEquals(
            DieticianScreen.MealDetail("a").key,
            DieticianScreen.MealDetail("b").key,
        )
    }

    @Test
    fun `bottom nav contains exactly 5 entries`() {
        assertEquals(5, BottomNavScreens.size)
    }

    @Test
    fun `bottom nav does not include audit-log or cookbook by default`() {
        val keys = BottomNavScreens.map { it.key }.toSet()
        assertTrue("audit-log" !in keys, "audit-log is a Settings sub-screen, not bottom nav")
        assertTrue("cookbook" !in keys, "cookbook is reachable from Home, not bottom nav per spec")
    }

    @Test
    fun `data objects compare by identity`() {
        // Voyager-friendly sanity — data object instances are singletons.
        assertEquals(DieticianScreen.Home, DieticianScreen.Home)
        assertEquals(DieticianScreen.FoodLog, DieticianScreen.FoodLog)
        assertNotEquals<DieticianScreen>(DieticianScreen.Home, DieticianScreen.FoodLog)
    }

    @Test
    fun `meal-detail uses value equality on id`() {
        assertEquals(DieticianScreen.MealDetail("x"), DieticianScreen.MealDetail("x"))
        assertNotEquals(DieticianScreen.MealDetail("x"), DieticianScreen.MealDetail("y"))
    }
}
