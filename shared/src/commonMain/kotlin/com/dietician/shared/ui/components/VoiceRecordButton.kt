package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * RC1 (Council 1779120600) — voice-input button with toast fallback.
 *
 * Whisper.cpp JNI binding ships in Plan-7 (Round-3 §5 — RO ASR ~3-5% WER). Until
 * then, tapping the voice button shows the localized `voice_coming_soon` toast and
 * focuses the manual-entry field (graceful degradation, NOT a hard block).
 *
 * Per Batch B brief: 80dp tall, large tap target. Toast surface is host-managed
 * (Compose `SnackbarHost` typically) — the button only invokes [onTap] which the
 * screen wires to a `voice_coming_soon_toast` testTag.
 */
@Composable
fun VoiceRecordButton(onTap: () -> Unit) {
    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(8.dp)
            .testTag("foodlog-voice-button"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Phone, contentDescription = null)
            Text(text = "Record voice")
        }
    }
}
