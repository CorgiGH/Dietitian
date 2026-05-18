package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.EDFlag
import com.dietician.shared.ui.data.EDRuleVerdict
import com.dietician.shared.ui.i18n.strings

/**
 * ED-safeguard modal — surfaces in response to [EDFlag] trips from the detector.
 *
 * Three trigger variants matching the server-side detector:
 *   1. [EDFlag.KcalFloorBreach] — kcal under 80% target for 3+ consecutive days
 *   2. [EDFlag.WeightRateExcessive] — weight loss > 0.5 kg/week sustained
 *   3. [EDFlag.RestrictivePattern] — restrictive-pattern composite detector signal
 *
 * Modal copy uses bigorexia-aware framing (i18n keys ed_safeguard_*):
 *   - Title: "Some days have run low" (gentle, non-diagnostic)
 *   - Body: "Anything we can adjust? Process target, not body target."
 *
 * Action buttons:
 *   - Adjust target → callback to settings/target screen
 *   - "I'm in a planned cut" toggle (RC14) → activates 7-day window
 *   - Pause tracking → callback to pause-tracking screen
 *   - Dismiss → snooze 24h
 *
 * Hard-refuse vs soft-warn banner choice driven by [EDFlag.severity]:
 *   - HardRefuse → `ed-rules-hard-refuse-banner`
 *   - SoftWarn   → `ed-rules-soft-warn-banner`
 *
 * testTags: `ed-checkin-modal`, `ed-checkin-title`, `ed-checkin-adjust-target`,
 *   `ed-checkin-planned-cut-toggle`, `ed-checkin-pause`, `ed-checkin-dismiss`,
 *   `ed-rules-hard-refuse-banner`, `ed-rules-soft-warn-banner`.
 */
@Composable
fun EDSafeguardModal(
    flag: EDFlag,
    plannedCutActive: Boolean,
    onAdjustTarget: () -> Unit,
    onPlannedCutToggle: (Boolean) -> Unit,
    onPauseTracking: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ed-checkin-modal"),
        title = {
            Text(
                text = s.ed_safeguard_title,
                modifier = Modifier.testTag("ed-checkin-title"),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = s.ed_safeguard_body,
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Severity banner — hard refuse vs soft warn (locked copy).
                if (flag.severity == EDRuleVerdict.HardRefuse) {
                    Text(
                        text = s.ed_rules_hard_refuse,
                        modifier = Modifier.testTag("ed-rules-hard-refuse-banner"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (flag.severity == EDRuleVerdict.SoftWarn) {
                    Text(
                        text = s.ed_rules_soft_warn,
                        modifier = Modifier.testTag("ed-rules-soft-warn-banner"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Bigorexia framing (RC5 locked phrases).
                Text(
                    text = s.bigorexia_process_target,
                    style = MaterialTheme.typography.bodySmall,
                )

                // Variant-specific evidence line.
                val evidence = when (flag) {
                    is EDFlag.KcalFloorBreach ->
                        "kcal target ${flag.target}, ${flag.daysBelow80pct}d < 80%"
                    is EDFlag.WeightRateExcessive ->
                        "rate ${roundTo1(flag.kgPerWeek)} kg/wk"
                    is EDFlag.RestrictivePattern -> flag.detail
                }
                Text(
                    text = evidence,
                    style = MaterialTheme.typography.bodySmall,
                )

                // RC14 — planned-cut toggle inside modal.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = s.ed_safeguard_planned_cut,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = plannedCutActive,
                        onCheckedChange = onPlannedCutToggle,
                        modifier = Modifier.testTag("ed-checkin-planned-cut-toggle"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAdjustTarget,
                modifier = Modifier.testTag("ed-checkin-adjust-target"),
            ) { Text(s.ed_safeguard_adjust_target) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onPauseTracking,
                    modifier = Modifier.testTag("ed-checkin-pause"),
                ) { Text(s.ed_safeguard_pause_tracking) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("ed-checkin-dismiss"),
                ) { Text(s.ed_safeguard_dismiss) }
            }
        },
    )
}

private fun roundTo1(v: Double): String {
    val scaled = (v * 10).toInt() / 10.0
    return scaled.toString()
}
