package com.dietician.shared.ui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory backing for both [PantryReader] and [PantryWriter]. Replaces the
 * iter-2 split stubs (separate read-empty + write-noop) so the Pantry FAB can
 * actually add items + reflect them in the list within a single app session.
 *
 * Replace with the Plan-1 `PantrySnapshotStore` + `EventStore.enqueuePantryEvent`
 * pair when the SQLDelight wires land. Same StateFlow surface, so the swap is
 * a 1-line Koin binding change in `uiModule`.
 *
 * Single-writer-per-process. Concurrent writes from background coroutines on
 * Desktop are serialized by the underlying [MutableStateFlow]'s lock.
 */
class InMemoryPantryStore : PantryReader, PantryWriter {

    private val items = MutableStateFlow<List<PantryItem>>(emptyList())

    override fun flowSnapshot(): Flow<List<PantryItem>> = items

    override fun addItem(item: PantryItem) {
        items.value = items.value + item
    }

    override fun removeItem(sku: String, qty: Double, unit: String) {
        items.value = items.value.filterNot {
            it.skuUuid == sku && it.qty == qty && it.unit == unit
        }
    }
}
