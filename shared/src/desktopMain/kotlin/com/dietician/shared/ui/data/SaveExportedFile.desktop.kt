package com.dietician.shared.ui.data

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop save-export actual.
 *
 * Strategy: open an AWT [FileDialog] in SAVE mode pre-populated with the
 * suggested [name]. If the user picks a destination, write the bytes there.
 * If the JVM is headless (CI tests) or the user cancels, fall back to writing
 * deterministically to `~/Downloads/dietician/<name>` so the upstream "Saved
 * to <path>" toast still has a real path to surface.
 *
 * Returns the absolute path written to, or null if every path (FileDialog
 * cancel + headless fallback write) failed.
 */
@Suppress("unused")
actual suspend fun saveExportedFile(name: String, mime: String, bytes: ByteArray): String? {
    // Path 1 — interactive save dialog (only if not headless).
    val interactivePath = try {
        if (java.awt.GraphicsEnvironment.isHeadless()) null else openSaveDialog(name)
    } catch (_: Throwable) {
        null
    }
    if (interactivePath != null) {
        return runCatching {
            Files.write(interactivePath, bytes)
            interactivePath.toAbsolutePath().toString()
        }.getOrNull()
    }
    // Path 2 — fallback for headless / cancel: deterministic Downloads path.
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

private fun openSaveDialog(suggestedName: String): Path? {
    val parent = Frame()
    val dialog = FileDialog(parent, "Save export as", FileDialog.SAVE).apply {
        file = suggestedName
        isMultipleMode = false
        isVisible = true
    }
    val dir = dialog.directory
    val name = dialog.file
    parent.dispose()
    if (dir == null || name == null) return null
    return File(dir, name).toPath()
}
