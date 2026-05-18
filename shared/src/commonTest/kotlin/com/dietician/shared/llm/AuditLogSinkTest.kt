package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

class AuditLogSinkTest {
    @Test
    fun `InMemoryAuditLogSink captures writes in order`() = runTest {
        val sink = InMemoryAuditLogSink()
        sink.write(AuditEntry(subjectId = "victor", kind = "llm_call", costCents = 5))
        sink.write(AuditEntry(subjectId = null, kind = "backup_completed"))
        val snap = sink.snapshot()
        snap.size shouldBe 2
        snap[0].kind shouldBe "llm_call"
        snap[0].costCents shouldBe 5
        snap[1].subjectId shouldBe null
    }

    @Test
    fun `AuditEntry round-trips through JSON`() {
        val e = AuditEntry(
            subjectId = "victor",
            kind = "llm_call",
            model = "anthropic/claude-sonnet-4.5",
            promptHash = "sha256:abc",
            responseHash = "sha256:def",
            inputTokens = 100,
            outputTokens = 50,
            costCents = 2,
            requestId = "req-1",
            extra = mapOf("provider" to "openrouter"),
        )
        val json = Json { encodeDefaults = true }
        val str = json.encodeToString(AuditEntry.serializer(), e)
        val decoded = json.decodeFromString(AuditEntry.serializer(), str)
        decoded shouldBe e
    }

    @Test
    fun `AuditEntry defaults are null and empty`() {
        val e = AuditEntry(kind = "sign_in")
        e.subjectId shouldBe null
        e.model shouldBe null
        e.extra shouldBe emptyMap()
    }
}
