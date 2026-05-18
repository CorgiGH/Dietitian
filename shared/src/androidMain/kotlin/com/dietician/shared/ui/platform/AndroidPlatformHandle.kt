package com.dietician.shared.ui.platform

import android.content.Context

/**
 * Static handle for the Android [Context] reference + optional CameraX/MediaStore
 * facade. The expect/actual top-level functions for [com.dietician.shared.ui.data.captureImage]
 * + [com.dietician.shared.ui.data.saveExportedFile] + the [com.dietician.shared.ui.data.DieticianClipboardManager]
 * actual class read from this handle.
 *
 * Why a static holder rather than Koin injection: the receiver signatures
 * (`fun captureImage(): ByteArray?` / `class DieticianClipboardManager() { ... }`)
 * are dictated by the commonMain expect declarations and don't allow an
 * injection parameter. Wiring through Koin would require a separate `getKoin()`
 * lookup at every call site — works, but the static handle is simpler and
 * keeps the actuals self-contained.
 *
 * **Initialized by [com.dietician.android.DieticianAndroidApplication.onCreate]
 * via [install]** with a callback to the CameraX + MediaStore facades. Tests
 * inject fakes via [installForTest].
 */
object AndroidPlatformHandle {
    @Volatile private var applicationContext: Context? = null
    @Volatile private var capture: (() -> ByteArray?)? = null
    @Volatile private var saver: ((String, String, ByteArray) -> String?)? = null

    fun install(
        context: Context,
        captureImage: () -> ByteArray?,
        saveFile: (String, String, ByteArray) -> String?,
    ) {
        applicationContext = context.applicationContext
        capture = captureImage
        saver = saveFile
    }

    /** Test seam — direct override without the full install path. */
    fun installForTest(
        context: Context? = null,
        captureImage: (() -> ByteArray?)? = null,
        saveFile: ((String, String, ByteArray) -> String?)? = null,
    ) {
        if (context != null) applicationContext = context.applicationContext
        if (captureImage != null) capture = captureImage
        if (saveFile != null) saver = saveFile
    }

    /** Returns the installed Application context (or null pre-Application-init). */
    fun context(): Context? = applicationContext

    /** Returns the installed camera-capture facade result, or null pre-init. */
    fun runCapture(): ByteArray? = capture?.invoke()

    /** Returns the installed media-store-save result, or null pre-init / on error. */
    fun runSave(name: String, mime: String, bytes: ByteArray): String? =
        saver?.invoke(name, mime, bytes)

    /** Test-only reset between runs. */
    fun reset() {
        applicationContext = null
        capture = null
        saver = null
    }
}
