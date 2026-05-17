package com.dietician.shared.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

sealed interface PullTrigger {
    data object Ws : PullTrigger
    data object Ntfy : PullTrigger
    data object Manual : PullTrigger
    data object Periodic : PullTrigger
}

class PullTriggerCoalescer(
    private val debounceMs: Long,
    private val scope: CoroutineScope,
) {
    private val out = MutableSharedFlow<PullTrigger>(extraBufferCapacity = 16)
    private var pending: Job? = null
    private var pendingTrigger: PullTrigger? = null

    fun coalesced(): Flow<PullTrigger> = out

    fun push(trigger: PullTrigger) {
        pending?.cancel()
        pendingTrigger = trigger
        pending = scope.launch {
            delay(debounceMs)
            val t = pendingTrigger ?: return@launch
            pendingTrigger = null
            out.tryEmit(t)
        }
    }
}
