package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.AppLocale
import com.dietician.shared.ui.i18n.strings

/**
 * SettingsScreen — first-ship surface for the user-controllable toggles that
 * propagate across the app shell via [com.dietician.shared.ui.settings.SettingsStore].
 *
 * Rows (top to bottom):
 *   1. Locale switcher (EN / RO) — `settings-locale-en` / `settings-locale-ro`
 *   2. Dark theme toggle — `settings-dark-theme`
 *   3. Accessible typography (Atkinson Hyperlegible) — `settings-accessible-typography`
 *   4. Coach disabled toggle (RC9) — `settings-coach-disabled`
 *   5. "View audit log" button — `settings-view-audit-log`
 *   6. About card with version + spec date — `settings-about`
 *
 * Not yet wired (later iterations / Plan-3.5):
 *   - Sign out (needs session store)
 *   - Bigorexia config (planned-cut window length)
 *   - BYOK key management (Plan-3 byok_credentials table)
 *   - Consent management (separate Privacy sub-screen)
 *
 * The screen DOES NOT persist toggles to disk yet — the [SettingsStore] is in-
 * memory. State survives within a single app session; restarting the desktop
 * binary or the Android process resets everything to defaults.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAuditLog: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .testTag("settings-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = s.nav_settings,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp),
        )

        LocaleSwitcherRow(
            current = state.locale,
            onSelect = viewModel::setLocale,
        )

        ToggleRow(
            label = "Dark theme",
            checked = state.darkTheme,
            onCheckedChange = viewModel::setDarkTheme,
            testTagName = "settings-dark-theme",
        )

        ToggleRow(
            label = "Accessible typography (Atkinson Hyperlegible)",
            checked = state.useAccessibleTypography,
            onCheckedChange = viewModel::setUseAccessibleTypography,
            testTagName = "settings-accessible-typography",
        )

        ToggleRow(
            label = "Disable AI coach features",
            checked = state.coachDisabled,
            onCheckedChange = viewModel::setCoachDisabled,
            testTagName = "settings-coach-disabled",
        )

        Button(
            onClick = onOpenAuditLog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("settings-view-audit-log"),
        ) {
            Text(s.audit_log_title)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        AboutCard(
            version = viewModel.versionLabel,
            specDate = viewModel.specDateLabel,
        )
    }
}

@Composable
private fun LocaleSwitcherRow(
    current: AppLocale,
    onSelect: (AppLocale) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("settings-locale-row")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Language",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSelect(AppLocale.EN) },
                    enabled = current != AppLocale.EN,
                    modifier = Modifier.weight(1f).testTag("settings-locale-en"),
                ) {
                    Text("English")
                }
                Button(
                    onClick = { onSelect(AppLocale.RO) },
                    enabled = current != AppLocale.RO,
                    modifier = Modifier.weight(1f).testTag("settings-locale-ro"),
                ) {
                    Text("Română")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTagName: String,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).testTag(testTagName)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AboutCard(version: String, specDate: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).testTag("settings-about")) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "About", style = MaterialTheme.typography.titleMedium)
            Text(text = "Dietician $version", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Spec: $specDate", style = MaterialTheme.typography.bodySmall)
        }
    }
}
