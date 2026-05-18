package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.AuditRow
import com.dietician.shared.ui.i18n.strings

/**
 * Audit log row Composable.
 *
 * Shows: occurred-at + kind + model + cost + summary, plus RC10 compliance badge
 * when [AuditRow.emotionInferenceDisabled] is true (AI Act Art 5(1)(f) hard ban
 * — emotion inference disabled, evidence retained for regulator inspection).
 *
 * testTags:
 *   - `audit-log-row-{index}` on the row card
 *   - `audit-row-emotion-disabled-{rowId}` on the RC10 badge (only emitted
 *     when the badge is rendered — selector existence == evidence presence)
 */
@Composable
fun AuditLogRow(
    row: AuditRow,
    index: Int,
) {
    val s = strings()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("audit-log-row-$index"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.kind,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = row.occurredAtMs.toString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            row.model?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            row.costCents?.let {
                Text(
                    text = "cost=${it}¢",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            row.summary?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            if (row.emotionInferenceDisabled) {
                AssistChip(
                    onClick = {},
                    label = { Text(s.audit_log_emotion_disabled_badge) },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("audit-row-emotion-disabled-${row.id}"),
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}
