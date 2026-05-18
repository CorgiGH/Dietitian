package com.dietician.shared.ui.data

/**
 * Android save-export stub.
 *
 * First-ship returns null — the real impl lands in Batch E Task 23 (MediaStore
 * INSERT into `Downloads` collection on Android 10+; pre-10 uses
 * `Environment.DIRECTORY_DOWNLOADS`). AuditLogScreen tolerates null by surfacing
 * "Saved (location pending platform shell)" copy.
 */
@Suppress("unused")
actual suspend fun saveExportedFile(name: String, mime: String, bytes: ByteArray): String? = null
