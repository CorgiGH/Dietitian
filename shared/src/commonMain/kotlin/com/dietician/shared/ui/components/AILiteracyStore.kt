package com.dietician.shared.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists which AI-literacy banner version the user has acknowledged.
 *
 * Plan-1 `cache_metadata` table is not yet a generic key/value store (only the
 * `sync_cursor_per_table` schema exists at HEAD per Batch B audit). Until Plan-1
 * adds a `kv_meta` table (tracked under Batch C T10/T14 pantry/receipt persistence),
 * Batch B ships an in-memory store with a documented persistence-handoff hook
 * (`load`/`save`) that the platform shell wires to expect/actual file I/O in
 * Batch E Task 27.
 *
 * On app start the platform shell calls [load] with the persisted version (or null
 * if first launch). The UI subscribes to [shouldShowBanner] for re-display logic.
 *
 * When the user taps "I understand", [acknowledge] is called → `ackedVersion` flips
 * to [AILiteracyVersionGate.CURRENT_VERSION] → `shouldShowBanner` becomes false →
 * platform shell persists via [save] hook.
 */
class AILiteracyStore(
    private val onSave: (String) -> Unit = {},
) {
    private val _ackedVersion = MutableStateFlow<String?>(null)
    val ackedVersion: StateFlow<String?> = _ackedVersion.asStateFlow()

    private val _shouldShowBanner = MutableStateFlow(true)
    val shouldShowBanner: StateFlow<Boolean> = _shouldShowBanner.asStateFlow()

    /** Platform shell hands over persisted ack-version at app start. Null = never acked. */
    fun load(ackedVersion: String?) {
        _ackedVersion.value = ackedVersion
        _shouldShowBanner.value = AILiteracyVersionGate.shouldShow(ackedVersion)
    }

    /** User tapped "I understand". Banner hidden until next version bump. */
    fun acknowledge() {
        val v = AILiteracyVersionGate.CURRENT_VERSION
        _ackedVersion.value = v
        _shouldShowBanner.value = false
        onSave(v)
    }
}
