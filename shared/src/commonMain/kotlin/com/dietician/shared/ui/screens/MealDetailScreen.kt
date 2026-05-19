package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.data.Recipe
import com.dietician.shared.ui.data.RecipeReader
import com.dietician.shared.ui.i18n.strings
import kotlinx.coroutines.launch

/**
 * MealDetailScreen — shown when a recipe is tapped in Cookbook or from a future
 * meal-log entry. Loads the Recipe via [RecipeReader] (one-shot fetch + filter
 * by id), then renders title + ingredients (split from CSV) + servings + a
 * "Cook with this pantry" CTA that surfaces a placeholder toast for now.
 *
 * Real Cook-with-pantry semantics (Plan-1 + Plan-7):
 *   1. cross-reference ingredientsCsv against PantryReader.flowSnapshot()
 *   2. if all ingredients present → enqueue meal_event with the recipe id
 *      + decrement pantry quantities accordingly
 *   3. if some missing → surface a "missing N items" toast with a tap-to-add
 *      affordance routing to the pantry add dialog
 *
 * testTag selectors: `meal-detail-screen`, `meal-detail-title`,
 * `meal-detail-servings`, `meal-detail-ingredient-{index}`,
 * `meal-detail-cook-button`, `meal-detail-loading`, `meal-detail-not-found`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    recipeId: String,
    reader: RecipeReader,
    onBack: () -> Unit = {},
) {
    val s = strings()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(recipeId) {
        recipe = reader.all().firstOrNull { it.id == recipeId }
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("meal-detail-screen"),
    ) {
        TopAppBar(
            title = { Text(recipe?.title ?: "") },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("meal-detail-back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !loaded -> {
                    CircularProgressIndicator(modifier = Modifier.testTag("meal-detail-loading"))
                }
                recipe == null -> {
                    Text(
                        text = s.meal_detail_not_found,
                        modifier = Modifier.testTag("meal-detail-not-found"),
                    )
                }
                else -> {
                    val r = recipe!!
                    Text(
                        text = r.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("meal-detail-title"),
                    )
                    Text(
                        text = "${s.meal_detail_servings_label}: ${r.servings}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("meal-detail-servings"),
                    )
                    Text(
                        text = s.meal_detail_ingredients_label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            r.ingredientsCsv.split(",").forEachIndexed { i, ing ->
                                Text(
                                    text = "• ${ing.trim()}",
                                    modifier = Modifier.testTag("meal-detail-ingredient-$i"),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                snackbarHost.showSnackbar(s.meal_detail_cook_action_toast)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meal-detail-cook-button"),
                    ) {
                        Text(s.meal_detail_cook_with_pantry_button)
                    }
                }
            }
            SnackbarHost(hostState = snackbarHost, modifier = Modifier.fillMaxWidth()) { data ->
                Snackbar(modifier = Modifier.padding(8.dp)) { Text(data.visuals.message) }
            }
        }
    }
}
