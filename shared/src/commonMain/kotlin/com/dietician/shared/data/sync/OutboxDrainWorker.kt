package com.dietician.shared.data.sync

import com.dietician.shared.data.WallClock
import com.dietician.shared.data.api.EventEnvelope
import com.dietician.shared.data.api.PushRequest
import com.dietician.shared.data.deviceId
import com.dietician.shared.data.local.OutboxStore
import com.dietician.shared.data.local.SyncLogStore
import com.dietician.shared.data.remote.RetryPolicy
import com.dietician.shared.data.remote.SyncClient
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Drains outbox to /sync/push. Each successful 200 ACK calls markSynced (stamps event +
 * deletes outbox row in one tx). Each rejected uuid records failure + may promote to
 * outbox_dead at attempt #10 (council BREAK #6, NEVER silent-drop).
 *
 * Council BREAK #8 (ack-vs-flip): if the process crashes between server returning 200 and
 * local markSynced committing, the next drain retries the same uuid. Server is idempotent
 * on event_uuid via UPSERT-by-uuid. Snapshot maintenance is one-shot at EventStore.enqueue,
 * not on push-ack, so replay does not double-apply. See AckVsFlipChaosTest.
 */
class OutboxDrainWorker(
    private val outbox: OutboxStore,
    private val client: SyncClient,
    private val syncLog: SyncLogStore,
    private val clock: WallClock,
    private val batchSize: Int = 50,
    private val maxAttempts: Int = 10,
) {
    suspend fun drainOnce() {
        val batch = outbox.nextBatch(batchSize)
        if (batch.isEmpty()) return
        val req =
            PushRequest(
                deviceId = deviceId(),
                events = batch.map { EventEnvelope(it.table_name, it.event_uuid, it.payload_json) },
            )
        val resp =
            try {
                client.push(req)
            } catch (e: Throwable) {
                batch.forEach { outbox.recordFailure(it.event_uuid, e.message ?: e::class.simpleName.orEmpty()) }
                batch.forEach { outbox.promoteIfDead(it.event_uuid, clock.nowMillis(), maxAttempts) }
                return
            }
        resp.accepted.forEach { outbox.markSynced(it.eventUuid, it.serverRecvAt) }
        resp.rejected.forEach {
            outbox.recordFailure(it.eventUuid, it.reason)
            outbox.promoteIfDead(it.eventUuid, clock.nowMillis(), maxAttempts)
        }
    }

    /** Long-running drain loop with exponential backoff on empty / failure cycles. */
    suspend fun runForever() {
        var failureStreak = 0
        while (true) {
            val empty = outbox.nextBatch(1).isEmpty()
            try {
                drainOnce()
                failureStreak = 0
                if (empty) delay(2.seconds)
            } catch (e: Throwable) {
                failureStreak += 1
                delay(RetryPolicy.nextDelay(failureStreak))
            }
        }
    }
}
