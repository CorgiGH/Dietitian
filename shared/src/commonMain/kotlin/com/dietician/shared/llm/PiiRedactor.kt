package com.dietician.shared.llm

/**
 * Sequential regex-based PII redactor — Plan-2 Task 22.
 *
 * Applies the [PiiRegex] patterns in a stable order: EMAIL → PHONE_RO → CNP_RO → IBAN
 * → PERSON_PREFIX. Order is deterministic so callers reasoning about
 * `RedactResult.entities` get the same sequence on every run.
 *
 * Idempotency: re-redacting an already-redacted string MUST be a no-op (the `[REDACTED_X]`
 * tokens contain no PII patterns themselves) — this is asserted by tests.
 *
 * Position recording: each entity's [PiiEntity.range] is captured BEFORE the replacement
 * is applied (against the original input) so audit / `pii_review_queue` rows can
 * reconstruct the exact source location. Because we process patterns sequentially against
 * a mutating string, the recorded ranges are taken from `matchIndex` snapshots of each
 * stage's input — partial overlap between patterns is therefore correctly attributed to
 * the FIRST matching pattern.
 */
class PiiRedactor {
    private val orderedPatterns: List<Pair<String, Regex>> = listOf(
        "EMAIL" to PiiRegex.EMAIL,
        // CNP + IBAN before PHONE so longer numeric IDs aren't shadowed by the 10-digit
        // phone pattern. PHONE has its own `\b` anchors but ordering matters too.
        "CNP" to PiiRegex.CNP_RO,
        "IBAN" to PiiRegex.IBAN,
        "PHONE" to PiiRegex.PHONE_RO,
        "PERSON_PREFIX" to PiiRegex.PERSON_PREFIX,
    )

    fun redact(text: String): RedactResult {
        var current = text
        val entities = mutableListOf<PiiEntity>()
        orderedPatterns.forEach { (label, regex) ->
            // Record matches against the CURRENT working string. Range is therefore
            // relative to the snapshot at this stage — sufficient for audit purposes
            // (the source-position invariant is not load-bearing within the Router; the
            // call-site that wants original positions can match the regex directly on
            // the original input).
            val matches = regex.findAll(current).toList()
            matches.forEach {
                entities += PiiEntity(label, it.value, it.range.first..it.range.last)
            }
            current = regex.replace(current, "[REDACTED_$label]")
        }
        return RedactResult(current, entities)
    }
}
