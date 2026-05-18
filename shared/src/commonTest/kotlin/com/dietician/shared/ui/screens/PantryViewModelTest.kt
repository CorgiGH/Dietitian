package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.PantryItem
import com.dietician.shared.ui.data.PantryReader
import com.dietician.shared.ui.data.PantryWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PantryViewModelTest {

    private class FakeReader(initial: List<PantryItem> = emptyList()) : PantryReader {
        val flow = MutableStateFlow(initial)
        override fun flowSnapshot(): Flow<List<PantryItem>> = flow
    }

    private class FakeWriter : PantryWriter {
        val added = mutableListOf<PantryItem>()
        val consumed = mutableListOf<Triple<String, Double, String>>()
        override fun addItem(item: PantryItem) {
            added += item
        }

        override fun removeItem(sku: String, qty: Double, unit: String) {
            consumed += Triple(sku, qty, unit)
        }
    }

    private fun item(
        sku: String,
        qty: Double = 1.0,
        unit: String = "g",
        expiresAtMs: Long? = null,
        open: Boolean = false,
        name: String = sku,
    ) = PantryItem(
        skuUuid = sku,
        displayName = name,
        qty = qty,
        unit = unit,
        expiresAtMs = expiresAtMs,
        open = open,
    )

    @Test
    fun `initial state is empty and not loaded`() {
        val vm = PantryViewModel(reader = FakeReader(), writer = FakeWriter())
        assertFalse(vm.state.value.loaded)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `load surfaces FEFO-sorted items earliest expiry first`() = runTest {
        val today = 1_700_000_000_000L
        val day = 86_400_000L
        val unsorted = listOf(
            item("sku-c", expiresAtMs = today + 10 * day),
            item("sku-a", expiresAtMs = today + 1 * day),
            item("sku-b", expiresAtMs = today + 5 * day),
            // null-expiry goes last
            item("sku-d", expiresAtMs = null),
        )
        val vm = PantryViewModel(
            reader = FakeReader(initial = unsorted),
            writer = FakeWriter(),
            clockNowMs = { today },
        )
        vm.load()
        val ids = vm.state.value.items.map { it.skuUuid }
        assertEquals(listOf("sku-a", "sku-b", "sku-c", "sku-d"), ids)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `expiring-soon flag set for items within 3 days`() = runTest {
        val today = 1_700_000_000_000L
        val day = 86_400_000L
        val items = listOf(
            item("near", expiresAtMs = today + 2 * day),
            item("far", expiresAtMs = today + 10 * day),
            item("none", expiresAtMs = null),
        )
        val vm = PantryViewModel(
            reader = FakeReader(initial = items),
            writer = FakeWriter(),
            clockNowMs = { today },
        )
        vm.load()
        val byId = vm.state.value.items.associateBy { it.skuUuid }
        assertTrue(byId.getValue("near").expiringSoon)
        assertFalse(byId.getValue("far").expiringSoon)
        assertFalse(byId.getValue("none").expiringSoon)
    }

    @Test
    fun `selectItem populates detail`() = runTest {
        val vm = PantryViewModel(
            reader = FakeReader(initial = listOf(item("milk", name = "Milk"))),
            writer = FakeWriter(),
        )
        vm.load()
        vm.selectItem("milk")
        val sel = vm.state.value.selected
        assertNotNull(sel)
        assertEquals("milk", sel.skuUuid)
    }

    @Test
    fun `clearSelection nulls detail`() = runTest {
        val vm = PantryViewModel(
            reader = FakeReader(initial = listOf(item("milk"))),
            writer = FakeWriter(),
        )
        vm.load()
        vm.selectItem("milk")
        vm.clearSelection()
        assertNull(vm.state.value.selected)
    }

    @Test
    fun `addItem delegates to writer`() {
        val writer = FakeWriter()
        val vm = PantryViewModel(reader = FakeReader(), writer = writer)
        val newItem = item("eggs", qty = 12.0, unit = "ct")
        vm.addItem(newItem)
        assertEquals(1, writer.added.size)
        assertEquals("eggs", writer.added.first().skuUuid)
    }

    @Test
    fun `removeItem delegates to writer`() {
        val writer = FakeWriter()
        val vm = PantryViewModel(reader = FakeReader(), writer = writer)
        vm.removeItem("milk", qty = 200.0, unit = "ml")
        assertEquals(Triple("milk", 200.0, "ml"), writer.consumed.first())
    }

    @Test
    fun `flowSnapshot updates propagate via subsequent load`() = runTest {
        val reader = FakeReader(initial = listOf(item("a")))
        val vm = PantryViewModel(reader = reader, writer = FakeWriter())
        vm.load()
        assertEquals(1, vm.state.value.items.size)
        reader.flow.value = listOf(item("a"), item("b"))
        vm.load()
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun `showAddSheet toggles visibility`() {
        val vm = PantryViewModel(reader = FakeReader(), writer = FakeWriter())
        vm.showAddSheet()
        assertTrue(vm.state.value.addSheetVisible)
        vm.hideAddSheet()
        assertFalse(vm.state.value.addSheetVisible)
    }
}
