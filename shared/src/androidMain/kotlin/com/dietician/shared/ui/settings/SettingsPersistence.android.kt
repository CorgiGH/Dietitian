package com.dietician.shared.ui.settings

import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Android persistence — JSON file at `context.filesDir/settings.json`. The
 * context is supplied via [AndroidFilesDirHolder] so the no-arg
 * `actual class SettingsPersistence()` constructor stays expect-compatible.
 *
 * Robolectric tests + Compose UI tests using a minimal Application bind the
 * holder to a temp dir before instantiating.
 */
actual class SettingsPersistence actual constructor() {

    private val file: File = run {
        val dir = AndroidFilesDirHolder.filesDir
            ?: File(System.getProperty("java.io.tmpdir"), "dietician").apply { mkdirs() }
        File(dir, "settings.json")
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
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }
}

/**
 * Single-writer holder for Android's [android.content.Context.getFilesDir]. The
 * [DieticianAndroidApplication] populates this before Koin starts so the
 * [SettingsPersistence] no-arg constructor can resolve the dir without needing
 * a Context-aware Koin definition.
 */
object AndroidFilesDirHolder {
    @Volatile
    var filesDir: File? = null
}
