package com.dietician.server.sync

import com.dietician.server.repo.EventRow
import com.dietician.shared.data.api.Cursor
import com.dietician.shared.data.api.EventEnvelope
import com.dietician.shared.data.api.PullRequest
import com.dietician.shared.data.api.PullResponse
import com.dietician.shared.data.api.PulledRow
import com.dietician.shared.data.api.PushAccepted
import com.dietician.shared.data.api.PushRejected
import com.dietician.shared.data.api.PushRequest
import com.dietician.shared.data.api.PushResponse

/**
 * Server-side helpers tying [com.dietician.shared.data.api] wire DTOs
 * (defined in :shared:commonMain for Plan-1 client) to the server
 * [com.dietician.server.repo.EventRepository] row shape.
 *
 * The DTOs themselves live in `:shared` so client + server share a single
 * contract; this file is purely server-side mapping glue. Re-exporting the
 * DTO names with `typealias` lets sync route handlers (next batch) import
 * `com.dietician.server.sync.PushRequest` instead of weaving through the
 * shared package path.
 *
 * Plan-3 design contract (council 1779120000 + Plan-1 ClientSyncClient):
 *   - cursor key = `table name` (e.g. "meal_events").
 *   - Pull response carries `cursor` = (synced_at_ms, event_uuid) of the
 *     last row in the page; client persists this opaquely.
 *   - Push response splits accepted vs rejected; rejected rows carry a
 *     reason string so the client can decide whether to retry or DLQ.
 */
typealias ServerPushRequest = PushRequest
typealias ServerPushResponse = PushResponse
typealias ServerPushAccepted = PushAccepted
typealias ServerPushRejected = PushRejected
typealias ServerPullRequest = PullRequest
typealias ServerPullResponse = PullResponse
typealias ServerPulledRow = PulledRow
typealias ServerEventEnvelope = EventEnvelope
typealias ServerCursor = Cursor

/**
 * Maps a server [EventRow] (raw JDBC row) to the wire [PulledRow] shape the
 * Plan-1 client expects.
 */
fun EventRow.toPulledRow(): PulledRow =
    PulledRow(
        tableName = table,
        eventUuid = eventUuid.toString(),
        originatedAtMs = originatedAtMs,
        payloadJson = payloadJson,
        serverRecvAt = syncedAtMs,
    )

/**
 * Maps a wire-side push envelope to the tuple [com.dietician.server.repo.EventRepository.upsert]
 * needs (`tableName`, `payloadJson`). The shared envelope already lines up
 * 1-to-1; this is here mostly so handlers don't need to import the shared
 * package directly.
 */
data class ServerPushItem(val tableName: String, val eventUuid: String, val payloadJson: String)

fun EventEnvelope.toServerPushItem(): ServerPushItem =
    ServerPushItem(tableName = tableName, eventUuid = eventUuid, payloadJson = payloadJson)
