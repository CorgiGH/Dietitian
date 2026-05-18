package com.dietician.shared.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannedCutControllerTest {

    @Test
    fun `initial state is inactive`() {
        val c = PlannedCutController(clockNowMs = { 0L })
        assertFalse(c.state.value.active)
        assertEquals(0, c.state.value.daysRemaining)
        assertNull(c.state.value.activatedAtEpochMs)
    }

    @Test
    fun `activate flips state + emits onActivate + persists`() {
        var activated = 0
        var persisted: PlannedCutState? = null
        val now = 10_000_000L
        val c = PlannedCutController(
            clockNowMs = { now },
            onActivate = { activated++ },
            onPersist = { persisted = it },
        )
        c.activate()
        assertTrue(c.state.value.active)
        assertEquals(7, c.state.value.daysRemaining)
        assertEquals(now, c.state.value.activatedAtEpochMs)
        assertEquals(1, activated)
        assertEquals(c.state.value, persisted)
    }

    @Test
    fun `tickFromClock decrements daysRemaining`() {
        var now = 0L
        val c = PlannedCutController(clockNowMs = { now })
        c.activate()
        assertEquals(7, c.state.value.daysRemaining)
        now += 3L * PlannedCutController.DAY_MS
        c.tickFromClock()
        assertEquals(4, c.state.value.daysRemaining)
        assertTrue(c.state.value.active)
    }

    @Test
    fun `tickFromClock past 7 days flips off + emits onExpire`() {
        var now = 0L
        var expired = 0
        val c = PlannedCutController(
            clockNowMs = { now },
            onExpire = { expired++ },
        )
        c.activate()
        now += 8L * PlannedCutController.DAY_MS
        c.tickFromClock()
        assertFalse(c.state.value.active)
        assertEquals(1, expired)
    }

    @Test
    fun `deactivate flips off without emitting expire`() {
        var expired = 0
        val c = PlannedCutController(
            clockNowMs = { 0L },
            onExpire = { expired++ },
        )
        c.activate()
        c.deactivate()
        assertFalse(c.state.value.active)
        assertEquals(0, expired)
    }

    @Test
    fun `restore from persisted activatedAt continues countdown`() {
        var now = 4L * PlannedCutController.DAY_MS
        val c = PlannedCutController(clockNowMs = { now })
        c.restore(activatedAtEpochMs = 0L)
        assertTrue(c.state.value.active)
        assertEquals(3, c.state.value.daysRemaining)
    }

    @Test
    fun `restore with null is inactive`() {
        val c = PlannedCutController(clockNowMs = { 0L })
        c.restore(null)
        assertFalse(c.state.value.active)
    }

    @Test
    fun `restore past 7 days immediately expires`() {
        var expired = 0
        val c = PlannedCutController(
            clockNowMs = { 10L * PlannedCutController.DAY_MS },
            onExpire = { expired++ },
        )
        c.restore(activatedAtEpochMs = 0L)
        assertFalse(c.state.value.active)
        assertEquals(1, expired)
    }
}
