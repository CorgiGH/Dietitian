package com.dietician.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Accessibility-toggle holder.
 *
 * Two consumers wire this in:
 * - Settings screen: bind the toggle UI to [accessibleTypography.value].
 * - Theme root: pass [accessibleTypography.value] to [DieticianTheme.useAccessibleTypography].
 *
 * Storage is volatile-in-memory by default. The Settings screen (later task)
 * wires it to Plan-1 `cache_metadata` so the choice survives process death.
 */
class AccessibilityState(
    initialAccessibleTypography: Boolean = false,
) {
    val accessibleTypography: MutableState<Boolean> = mutableStateOf(initialAccessibleTypography)
}

/**
 * Composable factory that survives configuration changes via [rememberSaveable]
 * for the underlying boolean. The class itself is plain-state (no Saver needed)
 * because the only field already round-trips through [rememberSaveable].
 */
@Composable
fun rememberAccessibilityState(initialAccessibleTypography: Boolean = false): AccessibilityState {
    val persisted = rememberSaveable { mutableStateOf(initialAccessibleTypography) }
    return remember(persisted) {
        AccessibilityState(initialAccessibleTypography = persisted.value).also {
            // mirror toggles back into the saveable.
            it.accessibleTypography.value = persisted.value
        }
    }.also { state ->
        // Keep persisted in sync if state.accessibleTypography is mutated by callers.
        if (persisted.value != state.accessibleTypography.value) {
            persisted.value = state.accessibleTypography.value
        }
    }
}
