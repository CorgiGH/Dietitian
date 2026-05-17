package com.dietician.shared.data

import java.io.File
import java.util.UUID

private val ID_FILE: File by lazy {
    val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Dietician")
    dir.mkdirs()
    File(dir, "device_id.txt")
}

actual fun deviceId(): String {
    if (!ID_FILE.exists()) ID_FILE.writeText("desktop-${UUID.randomUUID()}")
    return ID_FILE.readText().trim()
}
