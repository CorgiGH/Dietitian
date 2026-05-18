package com.dietician.shared.ui.data

/**
 * Cross-platform clipboard handle.
 *
 * Used by [com.dietician.shared.ui.screens.ByokScreen] to detect paste events
 * and clear sensitive content per RC13 (Council 1779120600): when a user pastes
 * an API key into the BYOK field, the OS clipboard is immediately wiped + a
 * toast surfaces ("Clipboard cleared for security") so the key cannot be
 * harvested by other apps running with clipboard-read permission.
 *
 * Implementations:
 *   - Desktop (`ClipboardManager.desktop.kt`) — `Toolkit.getDefaultToolkit()
 *     .systemClipboard` with an empty `StringSelection`.
 *   - Android (`ClipboardManager.android.kt`) — `android.content.ClipboardManager`
 *     plus a Context-injected factory; full wiring lands in Batch E Task 23 shell.
 *     First-ship Android actual no-ops so the screen + viewmodel compile.
 */
expect class DieticianClipboardManager() {
    /** Read clipboard text (or null if non-text / empty). */
    fun get(): String?

    /** Replace clipboard contents with empty string. */
    fun clear()
}
