package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Receipt thumbnail preview + Upload / Retake buttons.
 *
 * Image bytes are not actually rendered as a Bitmap in Batch C (commonMain has
 * no Bitmap-decode primitive — that's Compose-platform-specific). First-ship
 * shows a placeholder "preview: N bytes" caption + the two action buttons; the
 * Compose `Image(...)` decode wires in Batch E with the camera + picker actuals.
 *
 * testTags: receipt-preview, receipt-upload-button, receipt-retake-button.
 */
@Composable
fun ReceiptPreviewCard(
    byteCount: Int,
    uploading: Boolean,
    onUpload: () -> Unit,
    onRetake: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("receipt-preview"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Receipt preview: $byteCount bytes",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    enabled = !uploading,
                    modifier = Modifier.testTag("receipt-retake-button"),
                ) { Text("Retake") }
                Button(
                    onClick = onUpload,
                    enabled = !uploading,
                    modifier = Modifier.testTag("receipt-upload-button"),
                ) { Text(if (uploading) "Uploading..." else "Upload") }
            }
        }
    }
}
