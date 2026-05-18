package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * RC9 — coach disabled notice. Replaces the chat input bar when
 * `subject.llmCoachDisabled = true`. Tap "Re-enable in Settings" → nav to
 * SettingsScreen (privacy section).
 *
 * Copy is from i18n `coach_disabled_notice` (RC9 locked).
 *
 * testTag: coach-disabled-notice.
 */
@Composable
fun CoachDisabledNotice(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("coach-disabled-notice"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = s.coach_disabled_notice,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("coach-disabled-open-settings"),
            ) { Text(s.coach_re_enable_in_settings_button) }
        }
    }
}
