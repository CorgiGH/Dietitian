package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.screens.ChatMessage

/**
 * Chat bubble row. User messages right-aligned on primary tint, assistant
 * messages left-aligned on surface variant.
 *
 * Assistant bubbles surface the disclosure pane inline (Art 13) — first-ship
 * keeps it always-expanded so visual acceptance tests don't depend on a tap.
 * Batch E may add a collapse toggle.
 *
 * testTag: coach-message-{idx}.
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    index: Int,
    onOpenAudit: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("coach-message-$index"),
    ) {
        Column(
            modifier = Modifier.align(if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart),
        ) {
            if (message.bypassBanner) {
                Text(
                    text = "Just-tell-me bypass — no AI used.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag("coach-bypass-banner-${message.id}"),
                )
            }
            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                colors = if (message.fromUser) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                },
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!message.fromUser && message.disclosure != null) {
                Box(modifier = Modifier.testTag("coach-disclosure-${message.disclosure.callUuid}")) {
                    PerCallDisclosurePane(
                        info = message.disclosure,
                        onOpenAuditRow = onOpenAudit,
                    )
                }
            }
        }
    }
}
