package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Home top section. Shows subject display name + opaque subject id (truncated) +
 * a Settings gear button that navigates to the Settings screen.
 *
 * Subject id is truncated to first 8 chars — full UUID never surfaced in UI (privacy
 * + screen-real-estate). Tooltip + long-press copy lands in Batch D Task 19.
 *
 * testTag selectors per Batch B brief: `home-subject-card`.
 */
@Composable
fun SubjectCard(
    displayName: String,
    subjectId: String,
    onOpenSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("home-subject-card")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = displayName)
                Text(text = subjectId.take(8) + "…")
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("home-subject-settings-button"),
            ) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    }
}
