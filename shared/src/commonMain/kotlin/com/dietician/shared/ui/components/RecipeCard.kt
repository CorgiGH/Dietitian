package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.Recipe

/**
 * Cookbook recipe row card.
 *
 * Minimal first-ship: title + servings line. Full nutritional summary +
 * ingredient preview lands when Plan-1 V010 corpus is populated by Plan-7
 * ingest pipeline.
 *
 * testTag: cookbook-recipe-{id}.
 */
@Composable
fun RecipeCard(
    recipe: Recipe,
    onTap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("cookbook-recipe-${recipe.id}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${recipe.servings} servings",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
