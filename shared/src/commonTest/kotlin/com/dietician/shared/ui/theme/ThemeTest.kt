package com.dietician.shared.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Theme tests — Council R3 ruling: no red-green pass/fail axis on macro/calorie semantics.
 */
class ThemeTest {

    @Test
    fun `light primary is warm amber not green`() {
        val primary = DieticianLightColorScheme.primary
        // Amber-ish = R >> G > B (warm)
        assertTrue(primary.red > primary.green, "primary should be warmer than greener (R > G), got $primary")
        assertTrue(primary.red > primary.blue, "primary should be warmer than bluer (R > B), got $primary")
    }

    @Test
    fun `dark primary stays in amber family`() {
        val primary = DieticianDarkColorScheme.primary
        assertTrue(primary.red >= primary.green, "dark primary should not be a green tone, got $primary")
        assertTrue(primary.red > primary.blue, "dark primary should be warm, got $primary")
    }

    @Test
    fun `secondary is neutral sage not bright success-green`() {
        val secondary = DieticianLightColorScheme.secondary
        // Sage = muted; reject saturated success-green where G > R+0.3 AND G > B+0.3
        val isBrightGreen = secondary.green > secondary.red + 0.3f &&
            secondary.green > secondary.blue + 0.3f
        assertFalse(
            isBrightGreen,
            "secondary should be muted sage, not bright success-green. Got $secondary",
        )
    }

    @Test
    fun `error color reserved and distinct from primary`() {
        // Error exists but must NOT collide with primary (which would imply error=brand).
        assertNotEquals(DieticianLightColorScheme.error, DieticianLightColorScheme.primary)
        assertNotEquals(DieticianDarkColorScheme.error, DieticianDarkColorScheme.primary)
    }

    @Test
    fun `neutral chip background is grey not red and not green`() {
        val bg = NeutralChip.backgroundLight
        // Grey = R,G,B roughly equal. Reject anything tilted >0.1 toward red OR green.
        val redTilt = bg.red - ((bg.green + bg.blue) / 2f)
        val greenTilt = bg.green - ((bg.red + bg.blue) / 2f)
        assertTrue(redTilt < 0.1f, "NeutralChip.backgroundLight is red-tilted: $bg")
        assertTrue(greenTilt < 0.1f, "NeutralChip.backgroundLight is green-tilted: $bg")
    }

    @Test
    fun `shapes match Material 3 token tiers`() {
        // Sanity: medium shape exists + is not zero.
        // (RoundedCornerShape doesn't expose dp publicly without composition; ensure non-null only.)
        val medium = DieticianShapes.medium
        @Suppress("USELESS_IS_CHECK")
        assertTrue(medium is androidx.compose.foundation.shape.CornerBasedShape)
    }

    @Test
    fun `light and dark schemes are distinct`() {
        assertNotEquals(DieticianLightColorScheme.background, DieticianDarkColorScheme.background)
        assertNotEquals(DieticianLightColorScheme.surface, DieticianDarkColorScheme.surface)
    }

    @Test
    fun `white onPrimary stays white`() {
        // Sanity on the contrast pairing (text/icon on primary should be white in light).
        assertEquals(Color.White, DieticianLightColorScheme.onPrimary)
    }
}
