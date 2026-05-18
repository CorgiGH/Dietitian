package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.ChatMessageBubble
import com.dietician.shared.ui.components.CoachDisabledNotice
import com.dietician.shared.ui.components.JustTellMeButton

/**
 * Coach chat surface — Plan-2 LlmRouterStream + Art 13 disclosure + RC7
 * deep-link + RC9 disabled-notice + RC12 cancel audit.
 *
 * Layout:
 *   - TopAppBar: "Coach" title + JustTellMeButton on the right
 *   - Body: LazyColumn of ChatMessageBubbles (newest at bottom)
 *   - Footer (one of):
 *     - Streaming indicator (animated dots placeholder) + Cancel button
 *     - Coach disabled notice (RC9) when `coachDisabled = true`
 *     - Input row: TextField + Send button (default)
 *
 * testTags: coach-input, coach-send, coach-streaming-indicator, coach-cancel,
 *           coach-message-{idx}, coach-disclosure-{call_uuid},
 *           coach-disclosure-open-audit-{call_uuid}, coach-disabled-notice,
 *           coach-just-tell-me-button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachChatScreen(
    viewModel: CoachChatViewModel,
    onOpenAuditRow: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.onOpenAuditRow = onOpenAuditRow
        viewModel.load()
    }

    Column(modifier = Modifier.fillMaxSize().testTag("coach-screen")) {
        TopAppBar(
            title = { Text("Coach") },
            actions = {
                JustTellMeButton(onClick = viewModel::justTellMe)
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp).testTag("coach-message-list"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.messages) { index, message ->
                ChatMessageBubble(
                    message = message,
                    index = index,
                    onOpenAudit = viewModel::openAuditFor,
                )
            }
        }
        when {
            state.coachDisabled -> CoachDisabledNotice(onOpenSettings = onOpenSettings)
            state.streaming -> StreamingIndicatorRow(onCancel = viewModel::cancelStream)
            else -> InputRow(
                input = state.input,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
            )
        }
    }
}

@Composable
private fun StreamingIndicatorRow(onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "…",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("coach-streaming-indicator"),
        )
        Button(
            onClick = onCancel,
            modifier = Modifier.testTag("coach-cancel"),
        ) { Text("Cancel") }
    }
}

@Composable
private fun InputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .testTag("coach-input"),
            label = { Text("Ask the coach...") },
            singleLine = true,
        )
        Button(
            onClick = onSend,
            enabled = input.isNotBlank(),
            modifier = Modifier.testTag("coach-send"),
        ) { Text("Send") }
    }
}
