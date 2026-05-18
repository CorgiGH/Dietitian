package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * AI Act Article 4 first-launch transparency modal.
 *
 * Shown when:
 * - First app launch (no ack-version in store).
 * - After [AILiteracyVersionGate.CURRENT_VERSION] bump per RC18 policy.
 *
 * Re-acknowledgeable from Settings → About (Batch C Task 20). i18n EN+RO strings
 * shipped in Batch A (`ai_literacy_banner_title` / `ai_literacy_banner_disclaimer`).
 *
 * Visual: Material 3 `AlertDialog` (modal-blocking — user must ack before app reaches
 * Home). Dismiss-on-back is intentionally disabled (`onDismissRequest` no-op) so the
 * user must tap the explicit "I understand" CTA — required by AI Act Art 4 active-
 * acknowledgement reading.
 *
 * testTag selectors per Batch B brief: `ai-literacy-banner`, `ai-literacy-title`,
 * `ai-literacy-body`, `ai-literacy-ok-button`.
 */
@Composable
fun AILiteracyBanner(
    onAcknowledge: () -> Unit,
) {
    val s = strings()
    AlertDialog(
        modifier = Modifier.testTag("ai-literacy-banner"),
        onDismissRequest = { /* AI Act Art 4 — must explicitly acknowledge */ },
        title = {
            Text(
                text = s.ai_literacy_banner_title,
                modifier = Modifier.testTag("ai-literacy-title"),
            )
        },
        text = {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = s.ai_literacy_banner_disclaimer,
                    modifier = Modifier.testTag("ai-literacy-body"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag("ai-literacy-ok-button"),
            ) {
                Text("I understand")
            }
        },
    )
}
