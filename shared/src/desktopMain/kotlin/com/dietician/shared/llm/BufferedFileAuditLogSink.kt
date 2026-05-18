package com.dietician.shared.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop fallback sink — append-only JSONL at [filePath]. Used when the server-side
 * `AuditLogWriter` (Plan-3 V018) is unavailable (offline desktop, dev runs, server outage).
 *
 * Server drains the file into Postgres `audit_log` on next start — that drain logic lives
 * in `:server` Plan-3 Task 23. This sink is the producer side only.
 *
 * Thread-safe via [Mutex] — concurrent Router calls serialize their writes so we never
 * interleave half-lines on disk. Each call performs a single `appendText` which JVM
 * `FileWriter.append` lowers to a single `write(2)` (modulo the OS page-cache fsync
 * semantics — file is NOT fsync'd per row; the drain reader tolerates up-to-EOF reads).
 *
 * [maxBufferBytes] reserved for future bounded-memory variant — currently unused; left in
 * the signature so the next batch can introduce it without breaking call sites.
 */
class BufferedFileAuditLogSink(
    private val filePath: String,
    @Suppress("UnusedPrivateProperty", "unused") private val maxBufferBytes: Int = 100_000,
) : AuditLogSink {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    override suspend fun write(entry: AuditEntry) {
        mutex.withLock {
            val line = json.encodeToString(AuditEntry.serializer(), entry) + "\n"
            val f = File(filePath)
            f.parentFile?.mkdirs()
            f.appendText(line)
        }
    }
}
