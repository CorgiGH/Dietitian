package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.screens.DisclosureInfo

/**
 * Art 13 per-call disclosure pane. Shown collapsed beneath each assistant
 * bubble + expanded when the user taps the "Disclosure" affordance.
 *
 * Fields: model, input/output tokens, cost (cents), timestamp, call_uuid +
 * "Open audit row" deep-link (RC7) that fires [onOpenAuditRow] with the
 * call_uuid → host nav routes to AuditLogScreen filtered by that row.
 *
 * testTag: coach-disclosure-open-audit-{call_uuid}.
 */
@Composable
fun PerCallDisclosurePane(
    info: DisclosureInfo,
    onOpenAuditRow: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "model: ${info.model}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "tokens: ${info.inputTokens} in / ${info.outputTokens} out",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = formatCost(info.costCents),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "call_uuid: ${info.callUuid}",
                style = MaterialTheme.typography.labelSmall,
            )
            TextButton(
                onClick = { onOpenAuditRow(info.callUuid) },
                modifier = Modifier.testTag("coach-disclosure-open-audit-${info.callUuid}"),
            ) {
                Text("Open audit row")
            }
        }
    }
}

private fun formatCost(cents: Int): String {
    val dollars = cents / 100
    val rem = cents % 100
    val padded = rem.toString().padStart(2, '0')
    return "cost: $$dollars.$padded"
}
