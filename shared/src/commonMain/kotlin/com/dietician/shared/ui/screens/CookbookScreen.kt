package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.IngestUrlButton
import com.dietician.shared.ui.components.RecipeCard
import com.dietician.shared.ui.i18n.strings
import kotlinx.coroutines.launch

/**
 * Cookbook browsing + ingest surface.
 *
 * Layout:
 *   - Header row: search TextField + IngestUrlButton
 *   - Body: LazyColumn of RecipeCards filtered by query
 *   - Empty state: "no recipes yet — ingest your first" (when corpus empty)
 *   - Ingest dialog: URL TextField + submit button
 *   - Snackbar host for ingest toasts
 *
 * testTags: cookbook-search, cookbook-list, cookbook-recipe-{id},
 *           cookbook-ingest-url-button, cookbook-ingest-url-input,
 *           cookbook-ingest-submit, cookbook-empty-state.
 */
@Composable
fun CookbookScreen(
    viewModel: CookbookViewModel,
    onRecipeTap: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("cookbook-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("cookbook-search"),
                label = { Text(s.cookbook_search_label) },
                singleLine = true,
            )
            IngestUrlButton(onClick = viewModel::showIngestDialog)
        }

        if (state.loaded && state.recipes.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cookbook-empty-state"),
            ) {
                Text(
                    text = s.cookbook_empty_state,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("cookbook-list"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.filtered, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onTap = { onRecipeTap(recipe.id) },
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar { Text(data.visuals.message) }
        }
    }

    if (state.ingestDialogVisible) {
        AlertDialog(
            onDismissRequest = viewModel::hideIngestDialog,
            title = { Text(s.cookbook_ingest_dialog_title) },
            text = {
                OutlinedTextField(
                    value = state.ingestUrl,
                    onValueChange = viewModel::onIngestUrlChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cookbook-ingest-url-input"),
                    label = { Text(s.cookbook_ingest_url_placeholder) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { coroutineScope.launch { viewModel.submitIngest() } },
                    modifier = Modifier.testTag("cookbook-ingest-submit"),
                ) { Text(s.cookbook_ingest_submit_button) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideIngestDialog) {
                    Text(s.cookbook_ingest_cancel_button)
                }
            },
        )
    }
}
