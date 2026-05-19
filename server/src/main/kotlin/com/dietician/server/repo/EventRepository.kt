package com.dietician.server.repo

import com.dietician.server.db.DatabaseFactory
import org.postgresql.util.PGobject
import java.util.UUID

/**
 * RLS-aware reader/writer for the V001 event-ledger tables (pantry_events,
 * meal_events, weight_events, receipt_events).
 *
 * All operations go through [DatabaseFactory.withSubject] so PG enforces
 * per-subject row isolation via the V013 policies. Cross-subject pulls
 * return zero rows transparently.
 *
 * Pull cursor: half-open `>` window per the existing Plan-1 sync semantics
 * (synced_at, event_uuid) — newer-than ordering, monotonic per subject.
 */
class EventRepository(private val db: DatabaseFactory) {
    /**
     * Pull events for [subjectId] from [table] strictly newer than
     * (syncedAtCursorMs, syncedAtUuidCursor). Limit caps the batch.
     *
     * Returns events ordered (synced_at ASC, event_uuid ASC). Caller drives
     * pagination by passing the last row's (synced_at, event_uuid) on the
     * next call.
     */
    fun listSince(
        subjectId: UUID,
        table: String,
        syncedAtCursorMs: Long,
        syncedAtUuidCursor: UUID?,
        limit: Int = 500,
    ): List<EventRow> {
        require(table in TABLES) { "unknown event table: $table" }
        return db.withSubject(subjectId) { conn ->
            val cursorUuid = syncedAtUuidCursor ?: ZERO_UUID
            val sql =
                """
                SELECT event_uuid, device_id, originated_at, synced_at,
                       row_to_json($table)::TEXT AS payload
                FROM $table
                WHERE (synced_at > to_timestamp(? / 1000.0))
                   OR (synced_at = to_timestamp(? / 1000.0) AND event_uuid > ?)
                ORDER BY synced_at ASC, event_uuid ASC
                LIMIT ?
                """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.setLong(1, syncedAtCursorMs)
                ps.setLong(2, syncedAtCursorMs)
                ps.setObject(3, cursorUuid)
                ps.setInt(4, limit)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<EventRow>()
                    while (rs.next()) {
                        out +=
                            EventRow(
                                table = table,
                                eventUuid = rs.getObject("event_uuid", UUID::class.java),
                                deviceId = rs.getString("device_id"),
                                originatedAtMs = rs.getTimestamp("originated_at").time,
                                syncedAtMs = rs.getTimestamp("synced_at").time,
                                payloadJson = rs.getString("payload"),
                            )
                    }
                    out
                }
            }
        }
    }

    /**
     * Idempotent upsert by `event_uuid`. Returns true if a new row was
     * inserted, false on conflict (already present).
     *
     * Payload is a JSONB literal matching the table's row shape; callers
     * (sync push handler) build it from the client-side outbox payload.
     *
     * Server-injected columns (council 1779188964 tracer-bullet finding):
     * `subject_id` and `synced_at` are forced server-side via `jsonb_set`
     * before the row is populated. The client's outbox doesn't know
     * `subject_id` (session-derived) and `synced_at` must be the server's
     * time-of-arrival, not the client's clock. Both keys win over whatever
     * the client supplied (server-authoritative). RLS defense-in-depth on
     * the V013 policy still rejects mismatched `subject_id` even if this
     * injection were ever bypassed.
     */
    fun upsert(
        subjectId: UUID,
        table: String,
        payloadJson: String,
    ): Boolean {
        require(table in TABLES) { "unknown event table: $table" }
        return db.withSubject(subjectId) { conn ->
            val sql =
                """
                INSERT INTO $table
                SELECT * FROM jsonb_populate_record(
                    NULL::$table,
                    jsonb_set(
                        jsonb_set(?::jsonb, '{subject_id}', to_jsonb(?::text)),
                        '{synced_at}', to_jsonb(now())
                    )
                )
                ON CONFLICT (event_uuid) DO NOTHING
                """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                val pg = PGobject().apply {
                    type = "jsonb"
                    value = payloadJson
                }
                ps.setObject(1, pg)
                ps.setString(2, subjectId.toString())
                ps.executeUpdate() > 0
            }
        }
    }

    companion object {
        val TABLES = setOf("pantry_events", "meal_events", "weight_events", "receipt_events")
        private val ZERO_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    }
}

data class EventRow(
    val table: String,
    val eventUuid: UUID,
    val deviceId: String,
    val originatedAtMs: Long,
    val syncedAtMs: Long,
    val payloadJson: String,
)
