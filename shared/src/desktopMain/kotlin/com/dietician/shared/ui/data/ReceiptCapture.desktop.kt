package com.dietician.shared.ui.data

/**
 * Desktop file-picker capture stub for Batch C.
 *
 * Real implementation lands in Batch E Task 26 — `Swing JFileChooser` (or
 * Compose Desktop's `FileDialog`) opened in a background dispatcher; selected
 * file bytes returned.
 *
 * First-ship returns null so ReceiptUploadScreen can exercise the rest of the
 * upload pipe via injected test bytes.
 */
actual fun captureImage(): ByteArray? = null
