package com.dietician.shared.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StringsTest {

    @Test
    fun `EN and RO both implement Strings interface`() {
        val en: Strings = Strings_en
        val ro: Strings = Strings_ro
        // Spot-check a few keys are non-blank in both locales.
        assertTrue(en.bigorexia_strength_focus.isNotBlank())
        assertTrue(ro.bigorexia_strength_focus.isNotBlank())
        assertTrue(en.nav_home.isNotBlank())
        assertTrue(ro.nav_home.isNotBlank())
    }

    @Test
    fun `RC11 photo suggestion none-of-these exact in both locales`() {
        assertEquals("None of these — type manually", Strings_en.photo_suggestion_none_of_these)
        assertEquals("Niciuna dintre acestea — scrie manual", Strings_ro.photo_suggestion_none_of_these)
    }

    @Test
    fun `RC13 BYOK clipboard cleared exact in both locales`() {
        assertEquals("Clipboard cleared for security", Strings_en.byok_clipboard_cleared)
        assertEquals("Clipboard-ul șters pentru securitate", Strings_ro.byok_clipboard_cleared)
    }

    @Test
    fun `RC16 Tailscale disconnected exact in both locales`() {
        assertEquals("Connect to Tailscale to use Dietician", Strings_en.tailscale_disconnected_title)
        assertEquals(
            "Conectează-te la Tailscale pentru a folosi Dietician",
            Strings_ro.tailscale_disconnected_title,
        )
    }

    @Test
    fun `RC1 voice-fallback present in both locales`() {
        assertTrue(Strings_en.voice_coming_soon.contains("Voice transcription"))
        assertTrue(Strings_ro.voice_coming_soon.contains("Transcrierea vocală"))
    }

    @Test
    fun `RC9 coach-disabled notice present in both locales`() {
        assertTrue(Strings_en.coach_disabled_notice.contains("AI coach disabled"))
        assertTrue(Strings_ro.coach_disabled_notice.contains("Coach-ul AI dezactivat"))
    }

    @Test
    fun `RC14 planned-cut toggle present in both locales`() {
        assertTrue(Strings_en.planned_cut_toggle.contains("planned cut"))
        assertTrue(Strings_ro.planned_cut_toggle.contains("tăiere planificată"))
    }

    @Test
    fun `RC18 AI literacy banner copy present in both locales`() {
        assertEquals(
            "Dietician uses AI to suggest meals + parse receipts",
            Strings_en.ai_literacy_banner_title,
        )
        assertEquals(
            "Dietician folosește AI pentru sugestii de mese + parsare bonuri",
            Strings_ro.ai_literacy_banner_title,
        )
        assertTrue(Strings_en.ai_literacy_banner_disclaimer.contains("not medical advice"))
        assertTrue(Strings_ro.ai_literacy_banner_disclaimer.contains("nu constituie sfat medical"))
    }

    @Test
    fun `RO and EN strings actually differ (not stub copies)`() {
        assertNotEquals(Strings_en.bigorexia_strength_focus, Strings_ro.bigorexia_strength_focus)
        assertNotEquals(Strings_en.nav_home, Strings_ro.nav_home)
        assertNotEquals(Strings_en.nav_food_log, Strings_ro.nav_food_log)
        assertNotEquals(Strings_en.tailscale_disconnected_title, Strings_ro.tailscale_disconnected_title)
    }

    @Test
    fun `expenditure-preview empty-state non-blank + locale-distinct`() {
        assertTrue(Strings_en.expenditure_preview_empty_title.isNotBlank())
        assertTrue(Strings_en.expenditure_preview_empty_body.isNotBlank())
        assertTrue(Strings_ro.expenditure_preview_empty_title.isNotBlank())
        assertTrue(Strings_ro.expenditure_preview_empty_body.isNotBlank())
        assertNotEquals(
            Strings_en.expenditure_preview_empty_title,
            Strings_ro.expenditure_preview_empty_title,
        )
    }

    @Test
    fun `AppLocale fromTag resolves common cases`() {
        assertEquals(AppLocale.RO, AppLocale.fromTag("ro"))
        assertEquals(AppLocale.RO, AppLocale.fromTag("ro-MD"))
        assertEquals(AppLocale.RO, AppLocale.fromTag("ro-RO"))
        assertEquals(AppLocale.EN, AppLocale.fromTag("en"))
        assertEquals(AppLocale.EN, AppLocale.fromTag("en-US"))
        assertEquals(AppLocale.EN, AppLocale.fromTag(null))
        assertEquals(AppLocale.EN, AppLocale.fromTag("fr"))
    }
}
