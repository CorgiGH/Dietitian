package com.dietician.shared.ui.data

/**
 * Android clipboard stub.
 *
 * First-ship returns null on `get()` and no-ops on `clear()` — full wiring
 * lands in Batch E Task 23 (requires a Context-injected `ClipboardManager`
 * resolved via Koin from the Activity/Application). ByokViewModel tolerates
 * the stub via its `clipboard` constructor parameter (tests inject a fake).
 */
actual class DieticianClipboardManager actual constructor() {
    actual fun get(): String? = null
    actual fun clear() {}
}
