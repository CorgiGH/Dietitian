package com.dietician.shared.ui.data

import com.dietician.shared.ui.platform.AndroidPlatformHandle

/**
 * Android export-save actual — Batch E wiring.
 *
 * Delegates to [AndroidPlatformHandle.runSave] which is installed by
 * [com.dietician.android.DieticianAndroidApplication] with a callback wrapping
 * [com.dietician.android.MediaStoreSaver.save] (MediaStore.Downloads on
 * Android 10+, Environment.DIRECTORY_DOWNLOADS pre-10).
 *
 * Returns:
 *   - `content://...` URI string on Android 10+
 *   - absolute file path on pre-10
 *   - null when AndroidPlatformHandle not installed (tests) or write failed
 */
@Suppress("unused")
actual suspend fun saveExportedFile(name: String, mime: String, bytes: ByteArray): String? =
    AndroidPlatformHandle.runSave(name, mime, bytes)
