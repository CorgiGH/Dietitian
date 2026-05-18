package com.dietician.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AILiteracyVersionGateTest {

    @Test
    fun `shouldShow true when no version acked`() {
        assertTrue(AILiteracyVersionGate.shouldShow(null))
    }

    @Test
    fun `shouldShow false when current version acked`() {
        assertFalse(AILiteracyVersionGate.shouldShow(AILiteracyVersionGate.CURRENT_VERSION))
    }

    @Test
    fun `shouldShow true when older version acked (RC18 bump)`() {
        // Sim: user acked v0.9, now CURRENT_VERSION is 1.0 — banner re-shown.
        assertTrue(AILiteracyVersionGate.shouldShow("0.9"))
    }

    @Test
    fun `CURRENT_VERSION matches policy doc`() {
        // Mirror of docs/policies/AI_LITERACY_TEXT_VERSION.md CURRENT_VERSION = 1.0.
        // If you bump one, bump the other (per the policy doc procedure).
        assertEquals("1.0", AILiteracyVersionGate.CURRENT_VERSION)
    }
}

class AILiteracyStoreTest {

    @Test
    fun `initial state shows banner`() {
        val store = AILiteracyStore()
        assertTrue(store.shouldShowBanner.value)
        assertNull(store.ackedVersion.value)
    }

    @Test
    fun `load with null keeps banner shown`() {
        val store = AILiteracyStore()
        store.load(null)
        assertTrue(store.shouldShowBanner.value)
    }

    @Test
    fun `load with current version hides banner`() {
        val store = AILiteracyStore()
        store.load(AILiteracyVersionGate.CURRENT_VERSION)
        assertFalse(store.shouldShowBanner.value)
        assertEquals(AILiteracyVersionGate.CURRENT_VERSION, store.ackedVersion.value)
    }

    @Test
    fun `load with older version shows banner (RC18)`() {
        val store = AILiteracyStore()
        store.load("0.9")
        assertTrue(store.shouldShowBanner.value)
        assertEquals("0.9", store.ackedVersion.value)
    }

    @Test
    fun `acknowledge persists current version + hides banner + invokes onSave`() {
        var saved: String? = null
        val store = AILiteracyStore(onSave = { saved = it })
        store.acknowledge()
        assertEquals(AILiteracyVersionGate.CURRENT_VERSION, store.ackedVersion.value)
        assertFalse(store.shouldShowBanner.value)
        assertEquals(AILiteracyVersionGate.CURRENT_VERSION, saved)
    }

    @Test
    fun `acknowledge after older ack flips to current version`() {
        val store = AILiteracyStore()
        store.load("0.9")
        assertTrue(store.shouldShowBanner.value)
        store.acknowledge()
        assertFalse(store.shouldShowBanner.value)
        assertEquals(AILiteracyVersionGate.CURRENT_VERSION, store.ackedVersion.value)
    }
}
