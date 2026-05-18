package com.dietician.shared.ui.data

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Desktop clipboard wiring via AWT [Toolkit].
 *
 * Used by ByokScreen to detect + clear pasted API keys (RC13). On headless
 * environments (`java.awt.headless=true`) we silently no-op rather than throw
 * — tests in CI often run headless and we'd rather degrade than crash.
 */
actual class DieticianClipboardManager actual constructor() {

    private val clipboard get() = try {
        Toolkit.getDefaultToolkit().systemClipboard
    } catch (t: Throwable) {
        null
    }

    actual fun get(): String? = try {
        val cb = clipboard ?: return null
        val contents = cb.getContents(null) ?: return null
        if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            contents.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    } catch (t: Throwable) {
        null
    }

    actual fun clear() {
        try {
            val cb = clipboard ?: return
            cb.setContents(StringSelection(""), null)
        } catch (t: Throwable) {
            // No-op; clipboard unavailable (headless).
        }
    }
}
