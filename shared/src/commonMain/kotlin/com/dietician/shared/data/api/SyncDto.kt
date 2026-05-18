package com.dietician.shared.data.api

import kotlinx.serialization.Serializable

@Serializable
data class EventEnvelope(
    val tableName: String,
    val eventUuid: String,
    val payloadJson: String,
)

@Serializable
data class PushRequest(val deviceId: String, val events: List<EventEnvelope>)

@Serializable
data class PushAccepted(val eventUuid: String, val serverRecvAt: Long)

@Serializable
data class PushRejected(val eventUuid: String, val reason: String)

@Serializable
data class PushResponse(val accepted: List<PushAccepted>, val rejected: List<PushRejected>)

@Serializable
data class PullRequest(val deviceId: String, val cursors: Map<String, Cursor>)

@Serializable
data class PulledRow(
    val tableName: String,
    val eventUuid: String,
    val originatedAtMs: Long,
    val payloadJson: String,
    val serverRecvAt: Long,
)

@Serializable
data class PullResponse(val rows: List<PulledRow>, val serverTimeMs: Long)
