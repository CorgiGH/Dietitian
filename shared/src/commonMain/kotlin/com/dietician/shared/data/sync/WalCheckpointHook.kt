package com.dietician.shared.data.sync

expect class WalCheckpointHook() {
    fun registerOnBackground(action: () -> Unit)
}
