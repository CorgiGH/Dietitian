package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.ReceiptPreviewCard
import com.dietician.shared.ui.data.captureImage

/**
 * Receipt upload surface.
 *
 * 4 states:
 *   - No preview: "Take photo" button → calls platform [captureImage]
 *   - Preview captured: ReceiptPreviewCard with Upload + Retake
 *   - Uploading: progress indicator
 *   - Success: success card with receipt_id + "View in Pantry" deep-link
 *
 * testTags: receipt-camera-button, receipt-preview, receipt-upload-button,
 *           receipt-retake-button, receipt-upload-progress,
 *           receipt-upload-success-{id}.
 */
@Composable
fun ReceiptUploadScreen(
    viewModel: ReceiptUploadViewModel,
    onViewInPantry: (String) -> Unit = {},
    captureProvider: () -> ByteArray? = ::captureImage,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
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
            .testTag("receipt-upload-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rid = state.uploadedReceiptId
        when {
            rid != null -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipt-upload-success-$rid"),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Receipt uploaded",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "id: $rid",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onViewInPantry(rid) }) {
                            Text("View in Pantry")
                        }
                    }
                }
            }
            state.uploading -> {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("receipt-upload-progress"),
                )
                Text("Uploading...")
            }
            state.previewBytes != null -> {
                ReceiptPreviewCard(
                    byteCount = state.previewBytes!!.size,
                    uploading = state.uploading,
                    onUpload = viewModel::upload,
                    onRetake = viewModel::retake,
                )
            }
            else -> {
                Button(
                    onClick = {
                        captureProvider()?.let(viewModel::onImageCaptured)
                    },
                    modifier = Modifier.testTag("receipt-camera-button"),
                ) {
                    Text("Take photo / pick file")
                }
            }
        }
        SnackbarHost(hostState = snackbarHost) { data ->
            Snackbar { Text(data.visuals.message) }
        }
    }
}
