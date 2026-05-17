package com.dietician.shared.data.sync

import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

actual class WalCheckpointHook actual constructor() {
    private val pending = mutableListOf<() -> Unit>()
    actual fun registerOnBackground(action: () -> Unit) {
        // Plan-5 will hook this to ComposeWindow's WindowFocusListener.
        // For now, retain references; Compose app wires the trigger.
        pending += action
    }
    fun fireForTest() { pending.forEach { it() } }
}
