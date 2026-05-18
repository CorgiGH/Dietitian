package com.dietician.server.cron

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate

/**
 * Nightly Postgres → encrypted-OneDrive backup — Plan-3 Task 35.
 *
 * Pipeline: `pg_dump -Fc | zstd > $dumpFile` then `rclone copy $dumpFile
 * onedrive-crypt:dietician-backups`. The `onedrive-crypt` remote is a
 * rclone `crypt`-wrapped onedrive backend so OneDrive sees only opaque
 * ciphertext.
 *
 * Council 1779120000 RC11 mandates that this cron always emits a row
 * (success or failure) so `/health.last_backup_at` can surface drift even
 * when the operator forgot to set up a watchdog. The runbook
 * `docs/runbooks/restore.md` documents the corresponding pull path.
 *
 * Subprocess strategy: each binary is a separate [ProcessBuilder] so the
 * caller can run pg_dump as the `postgres` peer-auth user (no password)
 * and zstd in a separate process group. Output buffering risks a 1-2 GB
 * dump — we stream through pipes, never an in-memory `ByteArray`.
 *
 * Failure semantics: any non-zero exit + thrown by [require] is caught
 * by [run], logged via `audit_log.backup_failed`, and rethrown so the
 * CronBootstrap loop increments its failure counter.
 *
 * Path overrides (test injection):
 *   - [pgDumpCmd] / [zstdCmd] / [rcloneCmd] default to bare names (PATH
 *     lookup). Tests override with a stub script that fakes success / failure.
 *   - [dumpDir] is `/tmp` by default; tests can pass an in-temp-dir path.
 */
class BackupCron(
    private val pgHost: String,
    private val pgUser: String,
    private val pgDb: String,
    private val rcloneRemote: String = "onedrive-crypt:dietician-backups",
    private val auditLog: AuditLogWriter,
    private val pgDumpCmd: String = "pg_dump",
    private val zstdCmd: String = "zstd",
    private val rcloneCmd: String = "rclone",
    private val dumpDir: String = "/tmp",
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val log = LoggerFactory.getLogger(BackupCron::class.java)

    /**
     * Runs the backup pipeline. Returns the bytes uploaded for telemetry.
     * Throws if any step fails (caught by CronBootstrap's per-job catch).
     */
    fun run(): Long {
        val dumpName = "dietician-${today()}.dump.zst"
        val dumpFile = File(dumpDir, dumpName)
        try {
            // 1. pg_dump | zstd → dumpFile
            val pgDump = ProcessBuilder(pgDumpCmd, "-Fc", "-h", pgHost, "-U", pgUser, pgDb)
                .redirectErrorStream(false)
                .start()
            val zstd = ProcessBuilder(zstdCmd, "-q", "-o", dumpFile.absolutePath, "-")
                .redirectErrorStream(true)
                .start()

            // Stream pg_dump stdout → zstd stdin in a daemon thread so we can
            // await both processes in parallel.
            val pipe = Thread {
                pgDump.inputStream.use { src ->
                    zstd.outputStream.use { dst -> src.copyTo(dst) }
                }
            }.apply { isDaemon = true; start() }

            val pgExit = pgDump.waitFor()
            pipe.join()
            val zstdExit = zstd.waitFor()
            require(pgExit == 0) { "pg_dump exited $pgExit" }
            require(zstdExit == 0) { "zstd exited $zstdExit" }

            // 2. rclone copy dumpFile remote
            val rclone = ProcessBuilder(rcloneCmd, "copy", dumpFile.absolutePath, rcloneRemote)
                .redirectErrorStream(true)
                .start()
            val rcloneExit = rclone.waitFor()
            require(rcloneExit == 0) { "rclone exited $rcloneExit" }

            val sizeBytes = dumpFile.length()
            log.info("backup: uploaded {} ({} bytes) to {}", dumpName, sizeBytes, rcloneRemote)
            auditLog.write(
                subjectId = null,
                kind = AuditLogActions.BACKUP_COMPLETED,
                extra = JsonObject(
                    mapOf(
                        "size_bytes" to JsonPrimitive(sizeBytes),
                        "rclone_remote" to JsonPrimitive(rcloneRemote),
                        "dump_name" to JsonPrimitive(dumpName),
                    ),
                ),
            )
            return sizeBytes
        } catch (e: Exception) {
            log.error("backup failed", e)
            auditLog.write(
                subjectId = null,
                kind = AuditLogActions.BACKUP_FAILED,
                extra = JsonObject(
                    mapOf(
                        "error" to JsonPrimitive(e.message ?: e::class.simpleName ?: "unknown"),
                    ),
                ),
            )
            throw e
        } finally {
            if (dumpFile.exists()) dumpFile.delete()
        }
    }
}
