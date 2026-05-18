package com.dietician.shared.ui.data

import com.dietician.shared.ui.platform.AndroidPlatformHandle

/**
 * Android camera capture actual — Batch E wiring.
 *
 * Delegates to [AndroidPlatformHandle.runCapture] which is installed by
 * [com.dietician.android.DieticianAndroidApplication] with a callback wrapping
 * [com.dietician.android.CameraXCapture.capture]. The wrapping is synchronous;
 * the camera call itself is suspended on the Activity side and resolved before
 * the lambda returns.
 *
 * Returns null when:
 *   - Application not yet initialized (e.g. unit test without
 *     [AndroidPlatformHandle.installForTest])
 *   - User denied CAMERA permission
 *   - CameraX bind / take-picture failed
 *
 * Caller (ReceiptUploadScreen) tolerates null + shows "Capture failed" toast.
 */
actual fun captureImage(): ByteArray? = AndroidPlatformHandle.runCapture()
