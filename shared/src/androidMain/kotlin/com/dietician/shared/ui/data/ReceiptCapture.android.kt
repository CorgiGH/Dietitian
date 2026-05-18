package com.dietician.shared.ui.data

/**
 * Android camera/SAF capture stub for Batch C.
 *
 * Real implementation lands in Batch E Task 23 (CameraX or Storage Access
 * Framework with `ActivityResultContracts.GetContent`). The actual wires through
 * a Compose-friendly registerForActivityResult-equivalent.
 *
 * First-ship returns null so ReceiptUploadScreen can exercise the rest of the
 * upload pipe via injected test bytes.
 */
actual fun captureImage(): ByteArray? = null
