package com.dietician.shared.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

sealed interface PullTrigger {
    data object Ws : PullTrigger

    data object Ntfy : PullTrigger

    data object Manual : PullTrigger

    data object Periodic : PullTrigger
}

/**
 * Coalesces a burst of pull triggers (from WS callback / okhttp IO / main UI / drain worker)
 * into a single downstream emission after [debounceMs] ms of quiet.
 *
 * Council #3 BREAK: the previous impl mutated `pending: Job?` and `pendingTrigger` without
 * synchronization, so multi-threaded call sites could race and produce zero emissions.
 *
 * This rewrite uses a Channel.UNLIMITED → debounce → MutableSharedFlow pipeline. The Channel
 * reliably buffers `trySend` from any thread (lock-free / atomic), and `Flow.debounce` is the
 * canonical coalescing operator. No shared mutable state in the coalescer itself.
 */
@OptIn(FlowPreview::class)
class PullTriggerCoalescer(
    private val debounceMs: Long,
    scope: CoroutineScope,
) {
    private val inbound = Channel<PullTrigger>(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
    private val outbound = MutableSharedFlow<PullTrigger>(extraBufferCapacity = 16)

    init {
        scope.launch {
            inbound.consumeAsFlow().debounce(debounceMs).collect { outbound.emit(it) }
        }
    }

    fun coalesced(): Flow<PullTrigger> = outbound

    fun push(trigger: PullTrigger) {
        inbound.trySend(trigger)
    }
}
