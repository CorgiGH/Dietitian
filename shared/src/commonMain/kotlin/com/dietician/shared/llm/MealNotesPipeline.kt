package com.dietician.shared.llm

/**
 * Plan-2 Task 31 — Meal-notes redaction pipeline (RC5 council mandate).
 *
 * Two-pass redaction:
 *   1. Always: client-side regex pass via [PiiRedactor] (EMAIL / CNP / IBAN / PHONE / PERSON_PREFIX).
 *   2. Conditional: server-side spaCy NER catches free-form names + locations the regexes miss.
 *
 * RC5 gate: when text length > 50 chars AND spaCy is unavailable, the content is queued to
 * `pii_review_queue` (V020 table) for human-in-the-loop verification BEFORE flowing to a
 * downstream LLM. This guards against the client-only redactor missing free-form personal
 * names in long voice-memo / recipe-ingest blobs.
 *
 * Result variants:
 *   - [MealNoteResult.Ready] — both passes ran (or regex pass on text ≤50 chars). Safe to
 *     forward to the LLM.
 *   - [MealNoteResult.NeedsReview] — queued for human review; caller MUST NOT forward to LLM.
 *
 * Audit: every queue event emits `kind=llm_call_pii_queued_for_review` with extras
 * `raw_ref` + `reason` so the audit trail captures why the gate triggered.
 */
sealed interface MealNoteResult {
    data class Ready(val redactedText: String, val entities: List<PiiEntity>) : MealNoteResult
    data class NeedsReview(val rawRef: String, val context: String, val reason: String) : MealNoteResult
}

/**
 * Persistence interface for the RC5 review-queue side-channel. Server-side impl writes to
 * V020 `pii_review_queue`. Tests use an in-memory recorder.
 */
interface PiiReviewQueue {
    suspend fun enqueue(subjectId: String, rawRef: String, context: String)
}

/**
 * In-memory queue for tests + desktop dry-runs.
 */
class InMemoryPiiReviewQueue : PiiReviewQueue {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val entries = mutableListOf<Entry>()

    data class Entry(val subjectId: String, val rawRef: String, val context: String)

    override suspend fun enqueue(subjectId: String, rawRef: String, context: String) {
        mutex.lock()
        try {
            entries.add(Entry(subjectId, rawRef, context))
        } finally {
            mutex.unlock()
        }
    }

    suspend fun snapshot(): List<Entry> {
        mutex.lock()
        return try {
            entries.toList()
        } finally {
            mutex.unlock()
        }
    }
}

/**
 * spaCy NER client surface. Server-side impl POSTs to a spaCy HTTP service (Plan-2 §4.6).
 * Desktop / Android pass null to [MealNotesPipeline] indicating "spaCy unreachable from this
 * device class".
 */
interface SpacyNerClient {
    suspend fun extractEntities(text: String): List<NerEntity>
}

data class NerEntity(val label: String, val text: String)

/**
 * The pipeline itself. Stateless — single instance serves arbitrary parallel callers.
 *
 * Gate logic (RC5):
 *   - text.length > 50 AND spacyClient == null → queue + NeedsReview.
 *   - else → regex pass + (optional) spaCy pass → Ready.
 */
class MealNotesPipeline(
    private val redactor: PiiRedactor,
    private val spacyClient: SpacyNerClient?,
    private val queue: PiiReviewQueue,
    private val auditLog: AuditLogSink,
) {
    suspend fun process(
        subjectId: String,
        text: String,
        rawRef: String,
        context: String = "meal_note",
    ): MealNoteResult {
        // Pass 1 — regex always.
        val regexResult = redactor.redact(text)

        // RC5 gate.
        if (text.length > 50 && spacyClient == null) {
            queue.enqueue(subjectId, rawRef, context)
            auditLog.write(
                AuditEntry(
                    subjectId = subjectId,
                    kind = "llm_call_pii_queued_for_review",
                    extra = mapOf(
                        "raw_ref" to rawRef,
                        "context" to context,
                        "reason" to "spacy_unavailable_text_gt_50",
                        "text_len" to text.length.toString(),
                    ),
                ),
            )
            return MealNoteResult.NeedsReview(
                rawRef = rawRef,
                context = context,
                reason = "spacy_unavailable",
            )
        }

        // Pass 2 — spaCy NER if available. Only PERSON / LOC / ORG / GPE labels are
        // treated as PII (per Plan-2 §4.6 — date / numeric labels are not load-bearing).
        val nerEntities = spacyClient
            ?.extractEntities(text)
            ?.filter { it.label in NER_PII_LABELS }
            ?: emptyList()
        val nerRedacted = nerEntities.fold(regexResult.text) { acc, e ->
            acc.replaceFirst(e.text, "[REDACTED_${e.label}]")
        }
        val mergedEntities = regexResult.entities +
            nerEntities.map { PiiEntity(it.label, it.text, IntRange.EMPTY) }

        return MealNoteResult.Ready(redactedText = nerRedacted, entities = mergedEntities)
    }

    companion object {
        /** NER labels classified as PII for downstream LLM redaction. */
        val NER_PII_LABELS: Set<String> = setOf("PERSON", "LOC", "ORG", "GPE")

        /** Length above which the RC5 gate fires when spaCy is unavailable. */
        const val SPACY_GATE_LENGTH: Int = 50
    }
}
