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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.screens.PantryItemView

/**
 * Pantry item row card.
 *
 * Layout:
 *   - Top row: display name + qty/unit
 *   - Bottom row: open-status indicator (if open) + expires-on chip
 *
 * Color: NEUTRAL throughout (no red even for soon-expiring). The chip surfaces
 * "expires in Nd" using the surface tint — R3 §1.7 no-red-green semantics.
 *
 * testTags: pantry-item-{sku}, pantry-expires-chip-{sku}, pantry-item-open-chip-{sku}.
 */
@Composable
fun PantryItemCard(
    item: PantryItemView,
    daysUntilExpiry: Long?,
    onTap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("pantry-item-${item.skuUuid}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatQty(item.qty, item.unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.open) {
                    AssistChip(
                        onClick = onTap,
                        label = { Text("open") },
                        colors = AssistChipDefaults.assistChipColors(),
                        modifier = Modifier.testTag("pantry-item-open-chip-${item.skuUuid}"),
                    )
                }
                if (item.expiringSoon && daysUntilExpiry != null) {
                    AssistChip(
                        onClick = onTap,
                        label = { Text("expires in ${daysUntilExpiry}d") },
                        colors = AssistChipDefaults.assistChipColors(),
                        modifier = Modifier.testTag("pantry-expires-chip-${item.skuUuid}"),
                    )
                }
            }
        }
    }
}

private fun formatQty(qty: Double, unit: String): String {
    val rounded = if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()
    return "$rounded $unit"
}
