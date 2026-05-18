package com.dietician.shared.ui.auth

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStoreTest {

    @AfterTest
    fun cleanup() {
        SessionStore.clear()
    }

    @Test
    fun `set populates current and currentSubjectId`() {
        val s = Session("sess", "vic", "2026-06-01T00:00:00Z")
        SessionStore.set(s)
        assertEquals(s, SessionStore.current.value)
        assertEquals("vic", SessionStore.currentSubjectId)
    }

    @Test
    fun `clear wipes session`() {
        SessionStore.set(Session("sess", "vic", "2026-06-01T00:00:00Z"))
        SessionStore.clear()
        assertNull(SessionStore.current.value)
        assertNull(SessionStore.currentSubjectId)
    }

    @Test
    fun `set null is equivalent to clear`() {
        SessionStore.set(Session("sess", "vic", "2026-06-01T00:00:00Z"))
        SessionStore.set(null)
        assertNull(SessionStore.current.value)
    }
}
