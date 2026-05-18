package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.PantryItemCard
import com.dietician.shared.ui.data.PantryItem
import com.dietician.shared.ui.i18n.strings
import kotlinx.coroutines.launch

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
    onOpenCookbook: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    LaunchedEffect(Unit) { viewModel.load() }
    Box(modifier = Modifier.fillMaxSize().testTag("pantry-screen")) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onOpenCookbook,
                    modifier = Modifier.testTag("pantry-open-cookbook"),
                ) {
                    Text(s.settings_view_cookbook_button)
                }
            }
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
    if (state.addSheetVisible) {
        AddPantryItemDialog(
            onDismiss = viewModel::hideAddSheet,
            onSave = { item ->
                viewModel.addItem(item)
                viewModel.hideAddSheet()
            },
            onAfterSave = { viewModel.load() },
        )
    }
}

@Composable
private fun AddPantryItemDialog(
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onAfterSave: suspend () -> Unit,
) {
    val s = strings()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    val canSave = name.isNotBlank() && qty.toDoubleOrNull()?.let { it > 0 } == true
    AlertDialog(
        modifier = Modifier.testTag("pantry-add-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(s.pantry_add_dialog_title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.pantry_add_name_label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pantry-add-name"),
                )
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(s.pantry_add_qty_label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pantry-add-qty"),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(s.pantry_add_unit_label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pantry-add-unit"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val item = PantryItem(
                        skuUuid = "manual-${name.trim().lowercase().hashCode()}-$qty",
                        displayName = name.trim(),
                        qty = qty.toDouble(),
                        unit = unit.trim().ifBlank { "g" },
                    )
                    onSave(item)
                    scope.launch { onAfterSave() }
                },
                enabled = canSave,
                modifier = Modifier.testTag("pantry-add-save"),
            ) { Text(s.pantry_add_save_button) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("pantry-add-cancel")) {
                Text(s.pantry_add_cancel_button)
            }
        },
    )
}
