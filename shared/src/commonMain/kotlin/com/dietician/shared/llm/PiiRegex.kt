package com.dietician.shared.llm

/**
 * Lightweight PII pattern set for client-side first-pass redaction.
 *
 * Romanian-specific because the Dietician user base + paper corpus are RO. CNP =
 * Cod Numeric Personal (13-digit national ID, first digit ∈ {1,2,5,6,7}). IBAN format
 * is the canonical RO22-BANK-16digits shape.
 *
 * RC5 (Council 1779062699): this client-side set is intentionally PARTIAL — server-side
 * spaCy NER (Plan-2 Task 31 MealNotesPipeline) catches free-form names and locations the
 * regexes here can't. When [PiiRedactor] is run without spaCy fallback (e.g. on Android
 * where spaCy isn't reachable) the MealNotesPipeline queues the content to
 * `pii_review_queue` (Plan-3 V020 table) for human-in-the-loop verification.
 */
object PiiRegex {
    /** Standard RFC-ish email — practical, not RFC-5322 strict. */
    val EMAIL: Regex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")

    /**
     * Romanian phone: international `+40` prefix + 9 digits OR national `0` + 9 digits.
     * Matches mobile (07x) and landline (02x/03x) lengths uniformly. `\b` boundaries
     * prevent matches inside longer digit sequences (e.g. a CNP).
     */
    val PHONE_RO: Regex = Regex("""(?:\+40|\b0)\d{9}\b""")

    /** Romanian CNP — 13 digits starting with sex/century marker {1,2,5,6,7}. */
    val CNP_RO: Regex = Regex("""\b[12567]\d{12}\b""")

    /**
     * Romanian IBAN: RO + 2 check digits + 4-letter bank code + 16 alphanumeric BBAN.
     * Anchored with `\b` so longer alphanumeric strings don't bleed into match.
     */
    val IBAN: Regex = Regex("""\bRO\d{2}[A-Z]{4}[A-Z0-9]{16}\b""")

    /**
     * Honorific-prefixed person tokens. Catches "dl Popescu", "doamna Maria", etc.
     * Partial — full NER lives in server-side spaCy pipeline.
     */
    val PERSON_PREFIX: Regex = Regex("""\b(?:dl|dna|mr|mrs|domnul|doamna)\s+[A-Z][a-z]+""")
}

/**
 * Result of [PiiRedactor.redact].
 *
 * [text] is the input with all matched PII replaced by `[REDACTED_<LABEL>]` tokens.
 * [entities] is the ordered list of detected hits, useful for audit / review-queue rows.
 */
data class RedactResult(val text: String, val entities: List<PiiEntity>)

/**
 * A single PII hit. [range] reflects the position WITHIN THE ORIGINAL input — useful for
 * server-side audit reconstruction. The redacted text's positions differ because
 * `[REDACTED_X]` tokens have different lengths than the originals.
 */
data class PiiEntity(val label: String, val original: String, val range: IntRange)
