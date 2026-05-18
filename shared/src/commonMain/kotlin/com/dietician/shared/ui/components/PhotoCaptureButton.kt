package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Photo-of-meal capture button. CNN top-1 is only 72.92% on ISIA-Food-200 (R3 §1.13
 * pattern 5) — confirmation step mandatory. PhotoSuggestionCard (RC11 — "None of
 * these" escape hatch) lands in Batch D Task 19; this button is the entry-point
 * surface here.
 *
 * Android opens CameraX (Batch E Task 23); Desktop opens file picker (first-ship).
 *
 * testTag selector: `foodlog-photo-button`.
 */
@Composable
fun PhotoCaptureButton(onTap: () -> Unit) {
    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(8.dp)
            .testTag("foodlog-photo-button"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Face, contentDescription = null)
            Text(text = "Photo of meal")
        }
    }
}
