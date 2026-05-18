package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RC14 (Council 1779120600) — planned-cut 7-day window toggle.
 *
 * Lives in Home screen body. Toggling ON:
 * 1. Emits `planned_cut_activated` audit event (per Plan-2 AuditLogSink — wiring
 *    handed off to host via [PlannedCutController.onActivate]).
 * 2. Starts a 7-day countdown — UI shows "N days remaining".
 * 3. Persists active-state to the same cache_metadata-shim as AILiteracyStore
 *    (`onPersist` callback — platform shell wires expect/actual file I/O in
 *    Batch E Task 27).
 *
 * After 7 days elapsed:
 * - Emits `planned_cut_expired`.
 * - Auto-flips toggle to OFF.
 *
 * The composable is dumb — all state + side-effects live in [PlannedCutController].
 * UI tests mount the controller with a fake clock + verify daysRemaining transitions.
 *
 * testTag selectors per Batch B brief: `home-planned-cut-toggle`,
 * `home-planned-cut-days-remaining`.
 */
@Composable
fun PlannedCutToggle(
    state: PlannedCutState,
    onToggle: (Boolean) -> Unit,
) {
    val s = strings()
    Card(modifier = Modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(text = s.planned_cut_toggle)
                if (state.active) {
                    Text(
                        text = "${state.daysRemaining} ${s.planned_cut_days_remaining_suffix}",
                        modifier = Modifier.testTag("home-planned-cut-days-remaining"),
                    )
                }
            }
            Switch(
                checked = state.active,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("home-planned-cut-toggle"),
            )
        }
    }
}

/** Snapshot of planned-cut UI state. */
data class PlannedCutState(
    val active: Boolean = false,
    val daysRemaining: Int = 0,
    val activatedAtEpochMs: Long? = null,
)

/**
 * Side-effecting controller — manages 7-day countdown + audit emits + persistence.
 *
 * The host (HomeViewModel) calls [activate] / [deactivate] / [tickFromClock]
 * (the latter on `LaunchedEffect` boot + every ~30s while screen visible) and
 * subscribes to [state] for rendering.
 *
 * Clock is injected so tests can drive virtual time without `Dispatchers.Main` games.
 */
class PlannedCutController(
    private val clockNowMs: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
    private val onActivate: () -> Unit = {},
    private val onExpire: () -> Unit = {},
    private val onPersist: (PlannedCutState) -> Unit = {},
) {
    private val _state = MutableStateFlow(PlannedCutState())
    val state: StateFlow<PlannedCutState> = _state.asStateFlow()

    /** Restore from persisted state at boot. */
    fun restore(activatedAtEpochMs: Long?) {
        if (activatedAtEpochMs == null) {
            _state.value = PlannedCutState()
        } else {
            _state.value = PlannedCutState(active = true, activatedAtEpochMs = activatedAtEpochMs)
            tickFromClock()
        }
    }

    fun activate() {
        val now = clockNowMs()
        _state.value = PlannedCutState(
            active = true,
            daysRemaining = WINDOW_DAYS,
            activatedAtEpochMs = now,
        )
        onActivate()
        onPersist(_state.value)
    }

    fun deactivate() {
        _state.value = PlannedCutState()
        onPersist(_state.value)
    }

    /** Recompute days-remaining + expire if past window. Idempotent; safe to call often. */
    fun tickFromClock() {
        val activatedAt = _state.value.activatedAtEpochMs ?: return
        val elapsedMs = clockNowMs() - activatedAt
        val daysElapsed = (elapsedMs / DAY_MS).toInt()
        val daysRemaining = WINDOW_DAYS - daysElapsed
        if (daysRemaining <= 0 && _state.value.active) {
            _state.value = PlannedCutState()
            onExpire()
            onPersist(_state.value)
        } else if (_state.value.active) {
            _state.value = _state.value.copy(daysRemaining = daysRemaining)
        }
    }

    companion object {
        const val WINDOW_DAYS: Int = 7
        const val DAY_MS: Long = 24L * 60L * 60L * 1000L
    }
}
