package com.dietician.shared.ui.data

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop fallback: writes to the user's home dir under `~/Downloads/dietician/`
 * if that dir is writable, else under the OS temp dir.
 *
 * A full Compose Desktop `FileDialog` integration lands in Batch E Task 25; for
 * Batch D first-ship this saves to a deterministic path and returns it so the
 * UI can surface "Saved to <path>" in a toast.
 */
@Suppress("unused")
actual suspend fun saveExportedFile(name: String, mime: String, bytes: ByteArray): String? {
    return try {
        val home = System.getProperty("user.home")
        val targetDir = if (!home.isNullOrBlank()) {
            File(home, "Downloads/dietician").also { it.mkdirs() }
        } else {
            File(System.getProperty("java.io.tmpdir"), "dietician").also { it.mkdirs() }
        }
        val outFile = File(targetDir, name)
        Files.write(outFile.toPath() as Path, bytes)
        outFile.absolutePath
    } catch (t: Throwable) {
        null
    }
}
