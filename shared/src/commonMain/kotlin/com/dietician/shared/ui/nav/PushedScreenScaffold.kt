package com.dietician.shared.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Wrapper for screens reached via `navigator.push(...)`. Adds a TopAppBar
 * with a back arrow that fires [onBack]. Bottom-nav screens (Home / FoodLog /
 * Pantry / Coach / Settings) do NOT use this — they live under DieticianApp's
 * own Scaffold with the bottom NavigationBar instead.
 *
 * testTag on the back IconButton: `pushed-screen-back` so smoke walks can
 * drive every pushed screen identically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushedScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pushed-screen-back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            content(Modifier.fillMaxSize())
        }
    }
}
