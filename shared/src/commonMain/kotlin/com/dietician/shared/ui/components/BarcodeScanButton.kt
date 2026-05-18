package com.dietician.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Barcode scan entry point — Android opens CameraX + ZXing (Batch E Task 23) /
 * Desktop opens file picker for image-of-barcode (first-ship). The expect/actual
 * platform shim lands in Batch E; for Batch B [onTap] just signals "user clicked".
 *
 * testTag selector: `foodlog-barcode-button`.
 */
@Composable
fun BarcodeScanButton(onTap: () -> Unit) {
    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(8.dp)
            .testTag("foodlog-barcode-button"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Menu, contentDescription = null)
            Text(text = "Scan barcode")
        }
    }
}
