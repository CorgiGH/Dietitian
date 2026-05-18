package com.dietician.shared.llm

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class BufferedFileAuditLogSinkTest {
    @Test
    fun `writes one JSONL line per entry`() = runTest {
        val tmp = Files.createTempFile("audit-", ".jsonl").toFile()
        tmp.deleteOnExit()
        val sink = BufferedFileAuditLogSink(tmp.absolutePath)
        sink.write(AuditEntry(subjectId = "v", kind = "llm_call", costCents = 1))
        sink.write(AuditEntry(kind = "backup_completed"))

        val lines = tmp.readLines()
        lines.size shouldBe 2
        val json = Json { encodeDefaults = true }
        json.decodeFromString(AuditEntry.serializer(), lines[0]).subjectId shouldBe "v"
        json.decodeFromString(AuditEntry.serializer(), lines[1]).kind shouldBe "backup_completed"
    }

    @Test
    fun `concurrent writes do not interleave lines`() = runTest {
        val tmp = Files.createTempFile("audit-c-", ".jsonl").toFile()
        tmp.deleteOnExit()
        val sink = BufferedFileAuditLogSink(tmp.absolutePath)

        coroutineScope {
            (1..50).map { i ->
                async {
                    sink.write(AuditEntry(subjectId = "subj-$i", kind = "llm_call", costCents = i))
                }
            }.awaitAll()
        }

        val lines = tmp.readLines()
        lines.size shouldBe 50
        val json = Json { encodeDefaults = true }
        // Every line is a valid AuditEntry JSON (no torn writes).
        val decoded = lines.map { json.decodeFromString(AuditEntry.serializer(), it) }
        decoded.map { it.subjectId }.toSet().size shouldBe 50
    }

    @Test
    fun `creates parent directories`() = runTest {
        val base = Files.createTempDirectory("audit-parent-").toFile()
        base.deleteOnExit()
        val nested = File(base, "subdir/deeper/audit.jsonl")
        val sink = BufferedFileAuditLogSink(nested.absolutePath)
        sink.write(AuditEntry(kind = "sign_in"))
        nested.exists() shouldBe true
        nested.readLines().size shouldBe 1
    }
}
