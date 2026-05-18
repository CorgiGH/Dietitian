package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * Same-as-recent meal cloning entry point. Tapping opens a BottomSheet with the
 * last 10 logged meals + tap-to-clone — implemented inline in [FoodLogScreen]'s
 * sheet host or as a separate screen depending on host preference.
 *
 * The button just signals "user wants to clone" via [onTap].
 *
 * testTag selector: `foodlog-same-as-button`.
 */
@Composable
fun SameAsRecentButton(onTap: () -> Unit) {
    val s = strings()
    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(8.dp)
            .testTag("foodlog-same-as-button"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Text(text = s.foodlog_same_as_recent_button)
        }
    }
}
