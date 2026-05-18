package com.dietician.shared.ui.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dietician.shared.ui.platform.AndroidPlatformHandle

/**
 * Android clipboard actual — wires [android.content.ClipboardManager] via
 * [Context.CLIPBOARD_SERVICE] looked up from [AndroidPlatformHandle].
 *
 * `get()` returns the first item's coerced text (or null if the clip is image /
 * the clipboard is empty / the handle isn't installed yet).
 *
 * `clear()` replaces the clip with an empty plain-text item. Note that on
 * Android 13+ (T+) the system may still surface a "App accessed clipboard"
 * notification; that's correct behaviour — the user IS being told their key
 * was wiped. The RC13 spec body explicitly accepts this overlay.
 *
 * Returns null / no-ops on:
 *   - Application not yet initialized (handle null context)
 *   - System service lookup failure (very rare; only on stripped-down ROMs)
 */
actual class DieticianClipboardManager actual constructor() {

    private fun clipboardOrNull(): ClipboardManager? {
        val ctx = AndroidPlatformHandle.context() ?: return null
        return ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    actual fun get(): String? {
        val cb = clipboardOrNull() ?: return null
        val clip = cb.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val ctx = AndroidPlatformHandle.context() ?: return null
        return clip.getItemAt(0).coerceToText(ctx)?.toString()
    }

    actual fun clear() {
        val cb = clipboardOrNull() ?: return
        try {
            cb.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (t: Throwable) {
            // No-op; clipboard may be read-only on some OEM configurations.
        }
    }
}
