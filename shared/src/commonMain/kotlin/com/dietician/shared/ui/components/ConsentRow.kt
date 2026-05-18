package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings
import com.dietician.shared.ui.data.ConsentRow as ConsentRowDto

/**
 * Consent row composable (RC19 Council 1779120600).
 *
 * Plan-3 Art 9 + cross-border-transfer (SCC + DPF) consents are SEPARATE
 * records — never merged into a single "agree to everything" toggle. Each row
 * carries its own scope name + grant/withdraw timestamps + version hash for
 * audit traceability.
 *
 * testTags expected: `consent-row-art9-health`, `consent-row-cross-border-transfer`.
 * Callers pass [testTag] explicitly because the scope string itself is the
 * stable id.
 */
@Composable
fun ConsentRow(
    testTag: String,
    label: String,
    row: ConsentRowDto?,
    onToggle: (Boolean) -> Unit,
) {
    val s = strings()
    val granted = row?.granted == true
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val timestampLine = when {
                    granted && row?.grantedAtMs != null ->
                        "${s.consent_granted_at} @ ${row.grantedAtMs}"
                    !granted && row?.withdrawnAtMs != null ->
                        "${s.consent_withdrawn_at} @ ${row.withdrawnAtMs}"
                    else -> null
                }
                timestampLine?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                row?.versionHash?.let {
                    Text(
                        text = "v=$it",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Switch(
                checked = granted,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("$testTag-switch"),
            )
        }
    }
}
