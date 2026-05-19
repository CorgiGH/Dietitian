package com.dietician.shared.ui.settings

import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Desktop persistence — JSON file at `%APPDATA%/Dietician/settings.json` on
 * Windows or `~/.dietician/settings.json` elsewhere. Atomic-ish via tmp-write +
 * rename to avoid corruption on crash mid-write.
 */
actual class SettingsPersistence actual constructor() {

    private val file: File = run {
        val appData = System.getenv("APPDATA")
        val baseDir = if (!appData.isNullOrBlank()) {
            File(appData, "Dietician")
        } else {
            File(System.getProperty("user.home"), ".dietician")
        }
        baseDir.mkdirs()
        File(baseDir, "settings.json")
    }

    actual fun load(): SettingsState? = runCatching {
        if (!file.exists()) return@runCatching null
        val text = file.readText()
        if (text.isBlank()) return@runCatching null
        SettingsJson.decodeFromString<PersistedSettings>(text).toState()
    }.getOrNull()

    actual fun save(state: SettingsState) {
        runCatching {
            val text = SettingsJson.encodeToString(state.toPersisted())
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            // Best-effort atomic-ish replace; Files.move w/ ATOMIC_MOVE would be stricter.
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }
}
