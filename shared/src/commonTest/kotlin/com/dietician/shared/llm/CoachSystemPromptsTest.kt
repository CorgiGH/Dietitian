package com.dietician.shared.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoachSystemPromptsTest {
    @Test
    fun `EN prompt names Victor and references 2750 kcal target`() {
        val p = CoachSystemPrompts.forLocale("en")
        assertTrue("Victor" in p, "EN must address Victor by name")
        assertTrue("2750" in p, "EN must reference daily kcal target")
        assertTrue("137" in p, "EN must reference protein target")
    }

    @Test
    fun `RO prompt is non-empty and uses comma-below diacritics`() {
        val p = CoachSystemPrompts.forLocale("ro")
        assertTrue(p.isNotBlank())
        assertTrue("ş" !in p && "ţ" !in p, "must use ș (U+0219) / ț (U+021B), not cedilla")
    }

    @Test
    fun `unknown locale falls back to EN`() {
        assertEquals(CoachSystemPrompts.forLocale("en"), CoachSystemPrompts.forLocale("xx"))
    }

    @Test
    fun `EN and RO both carry the hard-refusal safeguards`() {
        // Council 1779292644 — the desktop Coach must ship with the ED /
        // bigorexia guardrails; assert both locales actually contain them.
        val en = CoachSystemPrompts.forLocale("en")
        val ro = CoachSystemPrompts.forLocale("ro")
        assertTrue("bigorexia" in en.lowercase(), "EN must carry the bigorexia safeguard")
        assertTrue("bigorexia" in ro.lowercase(), "RO must carry the bigorexia safeguard")
        assertTrue("1800" in en && "1800" in ro, "both must name the very-low-calorie floor")
    }
}
