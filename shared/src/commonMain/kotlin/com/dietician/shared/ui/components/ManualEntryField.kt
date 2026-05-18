package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Manual food-entry text field with autocomplete-from-local-food-DB hook.
 *
 * The autocomplete suggestion list is host-managed (Plan-1 will own the local food
 * DB; lookup ships in Batch C Task 10 alongside Pantry). Batch B exposes the field
 * with a `query`/`onQueryChange` contract + selector for click-smoke.
 *
 * testTag selector: `foodlog-manual-field`. The autocomplete dropdown wraps rows
 * in `foodlog-manual-suggestion-{index}` once Plan-1 wires the search.
 */
@Composable
fun ManualEntryField(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<String> = emptyList(),
    onSuggestionPicked: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Type a food or meal") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("foodlog-manual-field"),
        )
        suggestions.forEachIndexed { i, s ->
            Text(
                text = s,
                modifier = Modifier
                    .padding(8.dp)
                    .testTag("foodlog-manual-suggestion-$i"),
            )
        }
    }
}
