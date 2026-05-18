package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.i18n.strings

/**
 * BYOK (Bring Your Own Key) screen — Plan-3 `POST /me/byok` wiring.
 *
 * Layout:
 *   - Provider dropdown (OpenRouter / Anthropic / Gemini / Groq)
 *   - Key TextField (masked password visual transform)
 *   - Save button
 *   - Snackbar host — surfaces RC13 "Clipboard cleared for security" on paste
 *     event detection + transient errors
 *
 * testTags: `byok-screen`, `byok-provider-dropdown`,
 *   `byok-provider-item-{provider}`, `byok-key-field`, `byok-save-button`,
 *   `byok-paste-clipboard-cleared-toast` (RC13).
 */
@Composable
fun ByokScreen(viewModel: ByokViewModel) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    val snackbarHost = remember { SnackbarHostState() }
    var dropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.pasteToastVisible) {
        if (state.pasteToastVisible) {
            snackbarHost.showSnackbar(s.byok_paste_detected + " — " + s.byok_clipboard_cleared)
            viewModel.dismissPasteToast()
        }
    }
    LaunchedEffect(state.errorToast) {
        state.errorToast?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("byok-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = s.byok_title,
            style = MaterialTheme.typography.titleLarge,
        )
        Box {
            OutlinedTextField(
                value = state.provider.ifBlank { "—" },
                onValueChange = {},
                readOnly = true,
                label = { Text(s.byok_provider_label) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("byok-provider-dropdown"),
            )
            // Tap-to-open: a TextButton overlay row works around DropdownMenu
            // anchor mechanics for the first-ship surface.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { dropdownOpen = true }) {
                    Text("▾")
                }
            }
            DropdownMenu(
                expanded = dropdownOpen,
                onDismissRequest = { dropdownOpen = false },
            ) {
                for (provider in ByokProviders.ALL) {
                    DropdownMenuItem(
                        text = { Text(provider) },
                        onClick = {
                            viewModel.selectProvider(provider)
                            dropdownOpen = false
                        },
                        modifier = Modifier.testTag("byok-provider-item-$provider"),
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.keyValue,
            onValueChange = viewModel::onKeyValueChange,
            label = { Text(s.byok_key_label) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("byok-key-field"),
        )

        Button(
            onClick = viewModel::save,
            enabled = !state.saving && state.keyValue.isNotBlank() && state.provider.isNotBlank(),
            modifier = Modifier.testTag("byok-save-button"),
        ) {
            Text(s.byok_save)
        }

        SnackbarHost(hostState = snackbarHost) { data ->
            Snackbar(
                modifier = Modifier.testTag("byok-paste-clipboard-cleared-toast"),
            ) {
                Text(data.visuals.message)
            }
        }
    }
}
