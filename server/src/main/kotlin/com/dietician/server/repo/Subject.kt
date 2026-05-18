package com.dietician.server.repo

import java.time.OffsetDateTime
import java.util.UUID

/**
 * V013 `subjects` row. Schema is intentionally sparse for first-ship — only
 * auth + lifecycle fields. Profile / goal / weight columns will be added by
 * a later migration when Plan-3.5 ships the onboarding flow.
 */
data class Subject(
    val subjectId: UUID,
    val displayName: String,
    val emailForMagicLink: String?,
    val createdAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * V013 `devices` row.
 */
data class Device(
    val deviceId: UUID,
    val subjectId: UUID,
    val label: String,
    val createdAt: OffsetDateTime,
    val lastSeenAt: OffsetDateTime?,
)
