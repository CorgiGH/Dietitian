package com.dietician.shared.llm

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Plan-2 Task 31 — MealNotesPipeline (RC5 mandate).
 *
 * Cases:
 *   - short text + no spaCy → regex-only redaction, Ready result.
 *   - long text + no spaCy → queued for review, NeedsReview result, audit row emitted.
 *   - long text + spaCy available → both passes run, Ready result.
 *   - spaCy returns non-PII labels (DATE, MISC) → ignored, not in entity list.
 */
class MealNotesPipelineTest {

    private val redactor = PiiRedactor()

    @Test
    fun `short text without spaCy returns Ready via regex pass only`() = runTest {
        val audit = InMemoryAuditLogSink()
        val queue = InMemoryPiiReviewQueue()
        val pipeline = MealNotesPipeline(
            redactor = redactor,
            spacyClient = null,
            queue = queue,
            auditLog = audit,
        )
        // 47 chars, contains an email — regex catches it.
        val text = "I had lunch with foo@bar.com today afternoon."
        val result = pipeline.process(
            subjectId = "victor",
            text = text,
            rawRef = "raw-abc",
            context = "meal_note",
        )
        result.shouldBeInstanceOf<MealNoteResult.Ready>()
        (result as MealNoteResult.Ready).redactedText shouldContain "[REDACTED_EMAIL]"
        result.redactedText shouldNotContain "foo@bar.com"
        queue.snapshot().size shouldBe 0
        audit.snapshot().none { it.kind == "llm_call_pii_queued_for_review" } shouldBe true
    }

    @Test
    fun `RC5 long text without spaCy is queued for review and audit row written`() = runTest {
        val audit = InMemoryAuditLogSink()
        val queue = InMemoryPiiReviewQueue()
        val pipeline = MealNotesPipeline(
            redactor = redactor,
            spacyClient = null,
            queue = queue,
            auditLog = audit,
        )
        // 80+ chars — exceeds the 50-char gate; spaCy unavailable.
        val text = "Long voice memo from Victor about his lunch with Maria yesterday afternoon downtown."
        val result = pipeline.process(
            subjectId = "victor",
            text = text,
            rawRef = "raw-xyz",
            context = "voice_memo",
        )
        result.shouldBeInstanceOf<MealNoteResult.NeedsReview>()
        (result as MealNoteResult.NeedsReview).rawRef shouldBe "raw-xyz"
        result.context shouldBe "voice_memo"
        result.reason shouldBe "spacy_unavailable"

        val q = queue.snapshot()
        q.size shouldBe 1
        q.first().subjectId shouldBe "victor"
        q.first().rawRef shouldBe "raw-xyz"
        q.first().context shouldBe "voice_memo"

        val row = audit.snapshot().first { it.kind == "llm_call_pii_queued_for_review" }
        row.extra["raw_ref"] shouldBe "raw-xyz"
        row.extra["context"] shouldBe "voice_memo"
        row.extra["reason"] shouldBe "spacy_unavailable_text_gt_50"
    }

    @Test
    fun `long text with spaCy runs both passes and returns Ready`() = runTest {
        val audit = InMemoryAuditLogSink()
        val queue = InMemoryPiiReviewQueue()
        val fakeSpacy = object : SpacyNerClient {
            override suspend fun extractEntities(text: String): List<NerEntity> = listOf(
                NerEntity("PERSON", "Maria"),
                NerEntity("DATE", "yesterday"),
                NerEntity("LOC", "downtown"),
            )
        }
        val pipeline = MealNotesPipeline(
            redactor = redactor,
            spacyClient = fakeSpacy,
            queue = queue,
            auditLog = audit,
        )
        val text = "Long voice memo from Victor about his lunch with Maria yesterday afternoon downtown."
        val result = pipeline.process(
            subjectId = "victor",
            text = text,
            rawRef = "raw-q1",
        )
        result.shouldBeInstanceOf<MealNoteResult.Ready>()
        (result as MealNoteResult.Ready).redactedText shouldContain "[REDACTED_PERSON]"
        result.redactedText shouldContain "[REDACTED_LOC]"
        // DATE label is NOT a PII label per NER_PII_LABELS — keep "yesterday" visible.
        result.redactedText shouldContain "yesterday"
        val labels = result.entities.map { it.label }
        labels shouldContain "PERSON"
        labels shouldContain "LOC"
        (labels.contains("DATE")) shouldBe false

        queue.snapshot().size shouldBe 0
    }

    @Test
    fun `boundary at exactly 50 chars treats text as short`() = runTest {
        val audit = InMemoryAuditLogSink()
        val queue = InMemoryPiiReviewQueue()
        val pipeline = MealNotesPipeline(
            redactor = redactor,
            spacyClient = null,
            queue = queue,
            auditLog = audit,
        )
        // Exactly 50 chars → gate does NOT fire (gate is > 50, not >=).
        val text = "x".repeat(50)
        val result = pipeline.process(subjectId = "victor", text = text, rawRef = "r")
        result.shouldBeInstanceOf<MealNoteResult.Ready>()
        queue.snapshot().size shouldBe 0
    }
}
