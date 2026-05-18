package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * Pre-auth Tailscale-disconnected blocker.
 *
 * **RC16 (Council 1779120600):** Renders BEFORE magic-link / Home when
 * [com.dietician.shared.ui.platform.TailnetReachability.check] returns false.
 * Same Composable on Android + Desktop — platform shell mounts it conditionally.
 *
 * Carries i18n via `tailscale_disconnected_title` + `tailscale_disconnected_body`
 * (EN + RO both ship in Batch A).
 *
 * testTags:
 *   - `tailscale-disconnected-blocker` (root Surface — used by Playwright/UI tests
 *     to assert the blocker is on screen)
 *   - `tailscale-disconnected-retry` (the Retry button — UI tests click it to
 *     drive re-check after the user opens Tailscale)
 */
@Composable
fun TailscaleDisconnectedScreen(
    onRetry: () -> Unit,
) {
    val s = strings()
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tailscale-disconnected-blocker"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = s.tailscale_disconnected_title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = s.tailscale_disconnected_body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("tailscale-disconnected-retry"),
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * Minimal splash shown while [com.dietician.shared.ui.platform.TailnetReachability.check]
 * is in flight. Single centered "Dietician" label — no animation, no spinner. The
 * probe completes in ≤3s so a flashy loader is overkill.
 *
 * testTag: `dietician-splash`.
 */
@Composable
fun SplashScreen() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dietician-splash"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Dietician",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
