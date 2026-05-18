package com.dietician.android

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Save downloaded export bytes (audit PDF, JSON, DSAR ZIP) to the user's
 * Downloads collection via MediaStore on Android 10+; pre-10 falls back to
 * `Environment.DIRECTORY_DOWNLOADS` directly (legacy storage; only minSdk 26
 * targets this path so we can be brief).
 *
 * Returns the `content://` URI string on success (Android 10+) or the absolute
 * file path on pre-10. Returns `null` on any error.
 *
 * Used as the Android actual for [com.dietician.shared.ui.data.saveExportedFile].
 * Caller (Audit screen) surfaces the returned string in a "Saved to <X>" toast.
 */
class MediaStoreSaver(
    private val context: Context,
) {

    fun save(name: String, mime: String, bytes: ByteArray): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(name, mime, bytes)
        } else {
            saveLegacy(name, bytes)
        }
    } catch (t: Throwable) {
        null
    }

    private fun saveScoped(name: String, mime: String, bytes: ByteArray): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Dietician")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri).use { stream ->
            stream?.write(bytes)
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(name: String, bytes: ByteArray): String? {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        val dir = File(downloads, "Dietician").also { it.mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }
}
