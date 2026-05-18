package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.PantryItem
import com.dietician.shared.ui.data.PantryReader
import com.dietician.shared.ui.data.PantryWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * PantryScreen state holder.
 *
 * Read path: Plan-1 [com.dietician.shared.data.local.PantrySnapshotStore] via
 * [PantryReader.flowSnapshot]. The VM consumes the latest emission via [load]
 * (one-shot pull) — Compose `LaunchedEffect` re-invokes [load] on screen entry
 * + on writes; the reactive `collectAsState` form will land alongside Voyager
 * ScreenModel in Batch E Task 26.
 *
 * Sort: FEFO — earliest [PantryItem.expiresAtMs] first, items with null expiry
 * pushed to the end. Items with [PantryItem.expiresAtMs] within
 * [EXPIRY_WARN_WINDOW_MS] of [clockNowMs] get [PantryItemView.expiringSoon] = true
 * so the UI can surface the warning chip (NEUTRAL "expires in 2d", no red).
 *
 * Write path: [addItem] / [removeItem] delegate to [PantryWriter] which enqueues
 * pantry_event rows through Plan-1 [com.dietician.shared.data.local.EventStore].
 *
 * Detail bottomsheet state: [selectItem] / [clearSelection] populate
 * [PantryState.selected] which the host renders as a MealDetailScreen-like sheet.
 */
class PantryViewModel(
    private val reader: PantryReader,
    private val writer: PantryWriter,
    private val clockNowMs: () -> Long = { 0L },
) {
    private val _state = MutableStateFlow(PantryState())
    val state: StateFlow<PantryState> = _state.asStateFlow()

    suspend fun load() {
        val now = clockNowMs()
        val raw = reader.flowSnapshot().first()
        val sorted = raw.sortedWith(
            compareBy(
                { it.expiresAtMs == null },
                { it.expiresAtMs ?: Long.MAX_VALUE },
                { it.skuUuid },
            ),
        )
        val view = sorted.map { it.toView(now) }
        _state.value = _state.value.copy(items = view, loaded = true)
    }

    fun selectItem(sku: String) {
        val match = _state.value.items.firstOrNull { it.skuUuid == sku }
        _state.value = _state.value.copy(selected = match)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = null)
    }

    fun showAddSheet() {
        _state.value = _state.value.copy(addSheetVisible = true)
    }

    fun hideAddSheet() {
        _state.value = _state.value.copy(addSheetVisible = false)
    }

    fun addItem(item: PantryItem) {
        writer.addItem(item)
    }

    fun removeItem(sku: String, qty: Double, unit: String) {
        writer.removeItem(sku, qty, unit)
    }

    private fun PantryItem.toView(now: Long): PantryItemView {
        val soonCutoff = now + EXPIRY_WARN_WINDOW_MS
        val expiringSoon = expiresAtMs != null && expiresAtMs in (now..soonCutoff)
        return PantryItemView(
            skuUuid = skuUuid,
            displayName = displayName,
            qty = qty,
            unit = unit,
            expiresAtMs = expiresAtMs,
            open = open,
            expiringSoon = expiringSoon,
        )
    }

    companion object {
        /** 3 days — items expiring within this window get the warning chip. */
        const val EXPIRY_WARN_WINDOW_MS: Long = 3L * 24L * 60L * 60L * 1000L
    }
}

/**
 * UI-projection of a Pantry item. Adds [expiringSoon] computed from the current
 * clock for the warning chip; everything else mirrors [PantryItem].
 */
data class PantryItemView(
    val skuUuid: String,
    val displayName: String,
    val qty: Double,
    val unit: String,
    val expiresAtMs: Long? = null,
    val open: Boolean = false,
    val expiringSoon: Boolean = false,
)

data class PantryState(
    val items: List<PantryItemView> = emptyList(),
    val selected: PantryItemView? = null,
    val addSheetVisible: Boolean = false,
    val loaded: Boolean = false,
)
