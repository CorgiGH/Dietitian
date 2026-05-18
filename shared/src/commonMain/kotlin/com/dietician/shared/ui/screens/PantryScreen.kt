package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.PantryItemCard
import com.dietician.shared.ui.i18n.strings

/**
 * Pantry browsing surface.
 *
 * Layout:
 *   - LazyColumn of PantryItemCard rows (FEFO sorted by VM)
 *   - Empty-state card when [PantryState.items] is empty and [PantryState.loaded]
 *   - FAB "+ Add manually" bottom-right
 *   - Bottom-sheet preview when an item is selected (Batch C ships as inline
 *     "selection card"; full ModalBottomSheet wiring lands in Batch E when
 *     Material 3 bottom-sheets are wired with the navigation backstack)
 *
 * testTags: pantry-list, pantry-item-{sku}, pantry-expires-chip-{sku},
 *           pantry-add-fab, pantry-item-detail, pantry-empty-state.
 */
@Composable
fun PantryScreen(
    viewModel: PantryViewModel,
    clockNowMs: () -> Long = { 0L },
) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    LaunchedEffect(Unit) { viewModel.load() }
    Box(modifier = Modifier.fillMaxSize().testTag("pantry-screen")) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (state.loaded && state.items.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("pantry-empty-state"),
                ) {
                    Text(
                        text = s.pantry_empty_state,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("pantry-list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.items, key = { it.skuUuid }) { item ->
                        val daysLeft = item.expiresAtMs?.let { ts ->
                            ((ts - clockNowMs()) / (24L * 60L * 60L * 1000L)).coerceAtLeast(0)
                        }
                        PantryItemCard(
                            item = item,
                            daysUntilExpiry = daysLeft,
                            onTap = { viewModel.selectItem(item.skuUuid) },
                        )
                    }
                }
            }
            state.selected?.let { sel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("pantry-item-detail"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = sel.displayName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(text = "${sel.qty} ${sel.unit}")
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { viewModel.showAddSheet() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("pantry-add-fab"),
            text = { Text(s.pantry_add_manually_fab) },
            icon = {},
        )
    }
}
