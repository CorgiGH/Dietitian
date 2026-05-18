package com.dietician.server.llm

import com.dietician.server.audit.AuditLogWriter
import com.dietician.shared.llm.AuditEntry
import com.dietician.shared.llm.AuditLogSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Plan-2 Task 28 — Server-side adapter from `:shared:llm` [AuditLogSink] to Plan-3
 * [AuditLogWriter] (V018 `audit_log` table).
 *
 * Translates the commonMain [AuditEntry] flat-map [extra] into a [JsonObject] for the
 * jsonb column. Null subjectIds pass through unchanged — Plan-3 writer handles system
 * context.
 *
 * JDBC is sync; coroutines bounce through [Dispatchers.IO].
 */
class AuditLogSinkAdapter(
    private val writer: AuditLogWriter,
) : AuditLogSink {
    override suspend fun write(entry: AuditEntry) {
        val subjectUuid = entry.subjectId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val extraJson = if (entry.extra.isEmpty()) {
            null
        } else {
            JsonObject(entry.extra.mapValues { JsonPrimitive(it.value) })
        }
        withContext(Dispatchers.IO) {
            writer.write(
                subjectId = subjectUuid,
                kind = entry.kind,
                model = entry.model,
                promptHash = entry.promptHash,
                responseHash = entry.responseHash,
                inputTokens = entry.inputTokens,
                outputTokens = entry.outputTokens,
                costCents = entry.costCents,
                requestId = entry.requestId,
                extra = extraJson,
            )
        }
    }
}
