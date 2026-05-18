package com.dietician.shared.ui.data

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale

/**
 * Desktop image-capture actual via AWT [FileDialog].
 *
 * Opens a system file picker filtered for common image extensions (JPEG / PNG /
 * HEIC) — the AWT-level filename filter is best-effort on Windows (which uses
 * the native common-dialog and ignores the filter) but works correctly on Linux
 * + macOS. On Windows the user can still select non-image files; the bytes are
 * returned unchanged and the upstream OCR pipe handles MIME sniffing.
 *
 * Returns:
 *   - the file bytes on success
 *   - null on cancel / IO error / headless JVM (CI tests run with
 *     `java.awt.headless=true` which makes FileDialog throw; we swallow + null)
 *
 * **Why no Compose Desktop `AwtWindow { FileDialog }` wrapper:** that wrapper
 * is for embedding a dialog INSIDE a running Composable — but [captureImage] is
 * called from a screen-model coroutine outside the Composable scope. AWT
 * FileDialog works fine standalone; we just block on a temporary Frame parent.
 */
actual fun captureImage(): ByteArray? {
    return try {
        if (java.awt.GraphicsEnvironment.isHeadless()) return null
        val parent = Frame()
        val dialog = FileDialog(parent, "Select receipt image", FileDialog.LOAD).apply {
            // Accepts *.jpg / *.jpeg / *.png / *.heic (case-insensitive).
            setFilenameFilter { _, name ->
                val lower = name.lowercase(Locale.ROOT)
                lower.endsWith(".jpg") ||
                    lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") ||
                    lower.endsWith(".heic")
            }
            // Windows ignores the filter but still respects the wildcard hint.
            file = "*.jpg;*.jpeg;*.png;*.heic"
            isMultipleMode = false
            isVisible = true
        }
        val dir = dialog.directory
        val name = dialog.file
        parent.dispose()
        if (dir == null || name == null) return null
        val target = File(dir, name)
        if (!target.exists() || !target.isFile) return null
        target.readBytes()
    } catch (t: Throwable) {
        null
    }
}
