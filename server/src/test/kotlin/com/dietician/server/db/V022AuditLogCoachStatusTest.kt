package com.dietician.server.db

import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
class V022AuditLogCoachStatusTest {
    @Container
    val pg = PostgreSQLContainer("pgvector/pgvector:pg16")

    @Test
    fun `V022 adds status idempotency_key reserved_until and partial unique index`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        pg.createConnection("").use { c ->
            c.prepareStatement(
                "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'audit_log' " +
                    "ORDER BY column_name",
            ).executeQuery().use { rs ->
                val cols = buildList { while (rs.next()) add(rs.getString(1)) }
                assertTrue("status" in cols)
                assertTrue("idempotency_key" in cols)
                assertTrue("reserved_until" in cols)
            }
            c.prepareStatement(
                "SELECT indexdef FROM pg_indexes " +
                    "WHERE tablename = 'audit_log' AND indexname = 'idx_audit_log_idempotency_key'",
            ).executeQuery().use { rs ->
                assertTrue(rs.next())
                val def = rs.getString(1)
                assertTrue("UNIQUE" in def, "expected unique partial index, got: $def")
                assertTrue("WHERE" in def && "idempotency_key IS NOT NULL" in def)
            }
            c.prepareStatement(
                "SELECT proname FROM pg_proc WHERE proname = 'refund_orphaned'",
            ).executeQuery().use { rs ->
                assertTrue(rs.next(), "refund_orphaned(...) PG fn must exist after V022")
            }
        }
    }

    @Test
    fun `V022 status CHECK rejects unknown values`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        pg.createConnection("").use { c ->
            // Seed a subject so FK doesn't fail.
            c.prepareStatement(
                "INSERT INTO subjects (subject_id, display_name) " +
                    "VALUES ('00000000-0000-0000-0000-000000000099'::uuid, 'test')",
            ).execute()
            val ex = kotlin.runCatching {
                c.prepareStatement(
                    "INSERT INTO audit_log (subject_id, kind, status) " +
                        "VALUES ('00000000-0000-0000-0000-000000000099'::uuid, 'llm_call', 'bogus')",
                ).execute()
            }.exceptionOrNull()
            assertNotNull(ex)
            assertTrue(
                ex.message!!.contains("audit_log_status_check", ignoreCase = true) ||
                    ex.message!!.contains("violates check constraint"),
            )
        }
    }

    @Test
    fun `refund_orphaned flips stale pending row to orphaned and decrements budget`() {
        runMigrations(pg.jdbcUrl, pg.username, pg.password)
        pg.createConnection("").use { c ->
            // Seed subject + budget row.
            c.prepareStatement(
                "INSERT INTO subjects (subject_id, display_name) " +
                    "VALUES ('00000000-0000-0000-0000-000000000099'::uuid, 'test')",
            ).execute()
            c.prepareStatement(
                "INSERT INTO llm_budget " +
                    "(subject_id, provider, period_starts_at, period_ends_at, cost_cents_used) " +
                    "VALUES ('00000000-0000-0000-0000-000000000099'::uuid, 'openrouter', " +
                    "date_trunc('month', now())::DATE, " +
                    "(date_trunc('month', now()) + interval '1 month - 1 day')::DATE, 50)",
            ).execute()
            // Seed a pending row with reserved_until in the past + occurred_at >60s ago.
            c.prepareStatement(
                "INSERT INTO audit_log " +
                    "(subject_id, kind, model, cost_cents, status, idempotency_key, reserved_until, occurred_at) " +
                    "VALUES ('00000000-0000-0000-0000-000000000099'::uuid, 'llm_call', 'openrouter', 10, " +
                    "'pending', 'abcdef00-0000-0000-0000-000000000001'::uuid, " +
                    "now() - interval '120 seconds', now() - interval '120 seconds')",
            ).execute()

            c.prepareStatement("SELECT refund_orphaned(60)").executeQuery().use { rs ->
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
            // Verify status flipped + budget decremented.
            c.prepareStatement(
                "SELECT status FROM audit_log " +
                    "WHERE idempotency_key = 'abcdef00-0000-0000-0000-000000000001'::uuid",
            ).executeQuery().use { rs ->
                rs.next()
                assertEquals("orphaned", rs.getString(1))
            }
            c.prepareStatement(
                "SELECT cost_cents_used FROM llm_budget " +
                    "WHERE subject_id = '00000000-0000-0000-0000-000000000099'::uuid AND provider = 'openrouter'",
            ).executeQuery().use { rs ->
                rs.next()
                assertEquals(40, rs.getInt(1)) // 50 - 10 refund
            }
        }
    }
}
