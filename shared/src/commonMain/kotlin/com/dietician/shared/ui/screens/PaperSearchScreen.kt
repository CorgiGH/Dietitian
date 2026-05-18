package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.PaperResultCard

/**
 * Paper search surface.
 *
 * Layout:
 *   - Top: query TextField + submit button
 *   - Filter chips row: papers / supplements / recipes / foods
 *   - Body: result list OR empty-state ("No matches" + "Try a broader query")
 *     OR 501-banner ("Search coming soon, embedding service is still ramping")
 *   - Snackbar host for transient errors
 *
 * testTags: paper-search-query, paper-search-filter-chip-{corpus},
 *           paper-result-{idx}, paper-search-empty, paper-search-not-implemented,
 *           paper-search-submit.
 */
@Composable
fun PaperSearchScreen(
    viewModel: PaperSearchViewModel,
    onOpenDetail: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.errorToast) {
        state.errorToast?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("paper-search-screen"),
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
                    .testTag("paper-search-query"),
                label = { Text("Search papers + corpora") },
                singleLine = true,
            )
            TextButton(
                onClick = viewModel::submitQuery,
                modifier = Modifier.testTag("paper-search-submit"),
            ) { Text("Search") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (corpus in listOf("papers", "supplements", "recipes", "foods")) {
                FilterChip(
                    selected = corpus in state.corpora,
                    onClick = { viewModel.toggleCorpus(corpus) },
                    label = { Text(corpus) },
                    modifier = Modifier.testTag("paper-search-filter-chip-$corpus"),
                )
            }
        }
        when {
            state.embedNotImplemented -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paper-search-not-implemented"),
                ) {
                    Text(
                        text = "Search coming soon, embedding service is still ramping",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.searched && state.results.isEmpty() -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paper-search-empty"),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "No matches", style = MaterialTheme.typography.titleSmall)
                        Text(text = "Try a broader query", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("paper-search-list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.results) { index, result ->
                        PaperResultCard(
                            result = result,
                            index = index,
                            onOpenDetail = onOpenDetail,
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHost) { data ->
            Snackbar { Text(data.visuals.message) }
        }
    }
}
