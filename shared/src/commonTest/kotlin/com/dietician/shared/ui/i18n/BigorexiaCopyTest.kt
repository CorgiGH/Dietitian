package com.dietician.shared.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RC5 — locked bigorexia microcopy. Any change to these phrases must be a
 * deliberate spec edit; the council fixed the wording during pre-impl review.
 */
class BigorexiaCopyTest {

    @Test
    fun `EN bigorexia phrases present and exact`() {
        assertEquals(
            "Strength, energy, mood, sleep — not just weight",
            Strings_en.bigorexia_strength_focus,
        )
        assertEquals(
            "Weekly aggregate — not daily weight",
            Strings_en.bigorexia_weekly_aggregate,
        )
        assertEquals(
            "Process target, not body target",
            Strings_en.bigorexia_process_target,
        )
    }

    @Test
    fun `RO bigorexia phrases present and exact`() {
        assertEquals(
            "Forță, energie, dispoziție, somn — nu doar greutate",
            Strings_ro.bigorexia_strength_focus,
        )
        assertEquals(
            "Agregat săptămânal — nu cântar zilnic",
            Strings_ro.bigorexia_weekly_aggregate,
        )
        assertEquals(
            "Țintă de proces, nu țintă corporală",
            Strings_ro.bigorexia_process_target,
        )
    }
}
