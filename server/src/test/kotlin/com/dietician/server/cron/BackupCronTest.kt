package com.dietician.server.cron

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Smoke test for [BackupCron] — Plan-3 Task 35.
 *
 * Real `pg_dump` + `rclone` are not available in CI. The cron's external
 * binaries are injected via constructor, so the test wires three stub
 * shell scripts that simulate success / failure.
 *
 * Skipped on Windows runners — the stubs depend on POSIX `/bin/sh`. CI is
 * Linux per `.github/workflows/ci.yml`.
 */
@Testcontainers
@DisabledOnOs(OS.WINDOWS)
class BackupCronTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "backup_cron_app_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS backup_cron_app")
                    st.execute("CREATE ROLE backup_cron_app LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO backup_cron_app")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO backup_cron_app")
                }
            }
            bootstrapped = true
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            dbRef?.close()
        }
    }

    private fun freshDb(): DatabaseFactory {
        bootstrap()
        val d = DatabaseFactory(pg.jdbcUrl, "backup_cron_app", APP_PW)
        dbRef = d
        return d
    }

    // Sh stub for zstd-like behaviour: parse -o <out> from args, copy stdin
    // to that path. Kotlin uses `${'$'}` to emit a literal `$` so the shell
    // sees its own variables; the trimIndent block preserves shell newlines.
    private val zstdStubBody: String =
        "out=\"\"\n" +
            "while [ \"\$#\" -gt 0 ]; do\n" +
            "  case \"\$1\" in\n" +
            "    -o) out=\"\$2\"; shift 2;;\n" +
            "    *) shift;;\n" +
            "  esac\n" +
            "done\n" +
            "cat > \"\$out\""

    private fun writeExec(dir: File, name: String, body: String): String {
        val f = File(dir, name)
        f.writeText("#!/bin/sh\n$body\n")
        f.setExecutable(true)
        return f.absolutePath
    }

    @Test
    fun `run produces BACKUP_COMPLETED audit row on success`() {
        val db = freshDb()
        val tmp = Files.createTempDirectory("backup-cron-").toFile()
        // pg_dump stub: emit a fake dump to stdout. zstd stub: write fixed
        // bytes to -o output. rclone stub: noop.
        val pgDumpStub = writeExec(tmp, "pg_dump_stub", "printf 'FAKE-DUMP-CONTENT'")
        val zstdStub = writeExec(
            tmp,
            "zstd_stub",
            // Args from BackupCron: -q -o <path> -. We consume stdin and write to the -o arg.
            zstdStubBody,
        )
        val rcloneStub = writeExec(tmp, "rclone_stub", "exit 0")

        val cron = BackupCron(
            pgHost = "127.0.0.1",
            pgUser = "ignored",
            pgDb = "ignored",
            rcloneRemote = "test-remote:bucket",
            auditLog = AuditLogWriter(db),
            pgDumpCmd = pgDumpStub,
            zstdCmd = zstdStub,
            rcloneCmd = rcloneStub,
            dumpDir = tmp.absolutePath,
        )
        val size = cron.run()
        assertTrue(size > 0, "uploaded size must be > 0")

        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE kind = '${AuditLogActions.BACKUP_COMPLETED}'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
    }

    @Test
    fun `run emits BACKUP_FAILED audit row and rethrows when rclone fails`() {
        val db = freshDb()
        val tmp = Files.createTempDirectory("backup-cron-fail-").toFile()
        val pgDumpStub = writeExec(tmp, "pg_dump_stub", "printf 'X'")
        val zstdStub = writeExec(tmp, "zstd_stub", zstdStubBody)
        val rcloneStub = writeExec(tmp, "rclone_stub", "exit 7")

        val cron = BackupCron(
            pgHost = "h",
            pgUser = "u",
            pgDb = "d",
            rcloneRemote = "test:bucket",
            auditLog = AuditLogWriter(db),
            pgDumpCmd = pgDumpStub,
            zstdCmd = zstdStub,
            rcloneCmd = rcloneStub,
            dumpDir = tmp.absolutePath,
        )
        assertFailsWith<IllegalArgumentException> { cron.run() }
        // BACKUP_FAILED audit row was emitted before the rethrow.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE kind = '${AuditLogActions.BACKUP_FAILED}'",
            ).use { rs ->
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
    }
}
