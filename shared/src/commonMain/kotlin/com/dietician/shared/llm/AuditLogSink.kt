package com.dietician.shared.llm

import kotlinx.serialization.Serializable

/**
 * Plan-2 Router emits audit rows through this sink. Server-side production wires the sink
 * onto Plan-3's `AuditLogWriter` (V018 `audit_log` table). Client-side desktop/Android wire
 * the in-process queue + buffered-file fallback impls.
 *
 * RC12 (Council 1779062699) already shipped `SUBJECT_CREDENTIAL_REVOKED` in
 * `AuditLogActions` server-side; this commonMain interface intentionally treats `kind` as a
 * free string so both server and shared code can extend the closed enum without coupling.
 *
 * AI Act Art 12 mandate: every LLM call emits a row. The mandate is enforced at the
 * Router-call-site, NOT at this sink level — sink impls only persist what they receive.
 */
interface AuditLogSink {
    suspend fun write(entry: AuditEntry)
}

/**
 * Audit row. Mirrors the V018 `audit_log` table column set (subject_id, kind, model,
 * prompt_hash, response_hash, input_tokens, output_tokens, cost_cents, request_id, extra).
 *
 * [subjectId] null = system-scoped event (cron, backup, audit-prune). RLS on the server
 * is NULL-tolerant for these rows.
 *
 * [extra] is a flat `Map<String, String>` — Plan-2 Router only emits primitives. JSON
 * nesting at the table level uses jsonb via the server-side writer's PGobject path.
 */
@Serializable
data class AuditEntry(
    val subjectId: String? = null,
    val kind: String,
    val model: String? = null,
    val promptHash: String? = null,
    val responseHash: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val costCents: Int? = null,
    val requestId: String? = null,
    val extra: Map<String, String> = emptyMap(),
)

/**
 * Test + dev-only sink. Captures rows in memory for assertions. Thread-safe via mutex.
 */
class InMemoryAuditLogSink : AuditLogSink {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val entries = mutableListOf<AuditEntry>()

    override suspend fun write(entry: AuditEntry) {
        mutex.lock()
        try {
            entries.add(entry)
        } finally {
            mutex.unlock()
        }
    }

    suspend fun snapshot(): List<AuditEntry> {
        mutex.lock()
        return try {
            entries.toList()
        } finally {
            mutex.unlock()
        }
    }
}
