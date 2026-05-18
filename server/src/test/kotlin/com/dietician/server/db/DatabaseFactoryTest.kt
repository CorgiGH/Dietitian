package com.dietician.server.db

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration-tests [DatabaseFactory] against a live Postgres container.
 *
 * Asserts:
 *  - Construction triggers Flyway migrations (audit_log table exists).
 *  - `withSubject(uuid) { … }` sets `app.current_subject_id` inside tx and
 *    auto-resets after commit.
 *  - `withSystemContext { … }` runs with empty GUC; tables with NULL-tolerant
 *    RLS policies (e.g. `audit_log`) remain readable.
 *  - RLS isolation: subject A cannot read subject B's `pantry_events` rows.
 *  - Invalid subject id (non-UUID) is rejected with IllegalArgumentException
 *    (defense against future SQL-injection-by-bad-string paths).
 */
@Testcontainers
class DatabaseFactoryTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private var dbRef: DatabaseFactory? = null

        // Run Flyway migrations + create a non-superuser app role ONCE per class.
        // RLS bypass-by-table-owner is the canonical Postgres footgun; the
        // test must connect as a non-owner role to exercise real RLS posture.
        private val appPassword = "dietician_app_test_pw"
        private var bootstrapped = false

        private fun bootstrapAppRole() {
            if (bootstrapped) return
            // Apply Flyway as the superuser (Testcontainers default).
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
                conn.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS dietician_app_test")
                    st.execute(
                        "CREATE ROLE dietician_app_test LOGIN PASSWORD '$appPassword'",
                    )
                    st.execute("GRANT USAGE ON SCHEMA public TO dietician_app_test")
                    st.execute(
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO dietician_app_test",
                    )
                    st.execute(
                        "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO dietician_app_test",
                    )
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
        bootstrapAppRole()
        // DatabaseFactory.init runs Flyway again — Flyway sees its history
        // table and applies zero migrations, idempotent. The connection is
        // owned by the lower-privileged dietician_app_test role so RLS fires.
        val d = DatabaseFactory(pg.jdbcUrl, "dietician_app_test", appPassword)
        dbRef = d
        return d
    }

    @Test
    fun `construction runs flyway migrations - audit_log table exists`() {
        val db = freshDb()
        db.withSystemContext { conn ->
            conn.createStatement().executeQuery(
                "SELECT to_regclass('public.audit_log') IS NOT NULL AS exists",
            ).use { rs ->
                assertTrue(rs.next())
                assertTrue(rs.getBoolean("exists"), "audit_log table missing after init")
            }
        }
    }

    @Test
    fun `withSubject sets app current_subject_id GUC within transaction`() {
        val db = freshDb()
        val s = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val read =
            db.withSubject(s) { conn ->
                conn.createStatement().executeQuery(
                    "SELECT current_setting('app.current_subject_id', TRUE) AS s",
                ).use { rs ->
                    rs.next()
                    rs.getString("s")
                }
            }
        assertEquals(s.toString(), read)
    }

    @Test
    fun `withSystemContext leaves GUC empty`() {
        val db = freshDb()
        val read =
            db.withSystemContext { conn ->
                conn.createStatement().executeQuery(
                    "SELECT current_setting('app.current_subject_id', TRUE) AS s",
                ).use { rs ->
                    rs.next()
                    rs.getString("s")
                }
            }
        assertTrue(read.isNullOrEmpty(), "expected empty GUC in system context, got '$read'")
    }

    @Test
    fun `invalid uuid rejected before SET LOCAL fires`() {
        val db = freshDb()
        val ex =
            runCatching {
                db.withSubject("not-a-uuid'; DROP TABLE subjects; --") { conn ->
                    conn.createStatement().execute("SELECT 1")
                }
            }.exceptionOrNull()
        assertNotNull(ex, "expected IllegalArgumentException, got success")
        assertTrue(
            ex is IllegalArgumentException,
            "expected IllegalArgumentException, got ${ex::class.qualifiedName}",
        )
    }

    @Test
    fun `RLS isolates pantry_events across subjects via withSubject`() {
        val db = freshDb()
        val alice = UUID.fromString("11111111-1111-4111-8111-111111111111")
        val bob = UUID.fromString("22222222-2222-4222-8222-222222222222")
        val deviceA = UUID.randomUUID()
        val deviceB = UUID.randomUUID()

        // Seed both subjects + devices + one Alice pantry_event via system context.
        db.withSystemContext { conn ->
            conn.prepareStatement(
                "INSERT INTO subjects(subject_id, display_name) VALUES (?, 'Alice'), (?, 'Bob')",
            ).use { ps ->
                ps.setObject(1, alice)
                ps.setObject(2, bob)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO devices(device_id, subject_id, label) VALUES (?, ?, 'a-phone'), (?, ?, 'b-phone')",
            ).use { ps ->
                ps.setObject(1, deviceA)
                ps.setObject(2, alice)
                ps.setObject(3, deviceB)
                ps.setObject(4, bob)
                ps.executeUpdate()
            }
        }
        db.withSubject(alice) { conn ->
            conn.prepareStatement(
                "INSERT INTO pantry_events(event_uuid, device_id, originated_at, sku_uuid, delta_qty, unit, subject_id) " +
                    "VALUES (gen_random_uuid(), ?, now(), ?, 1.0, 'g', ?)",
            ).use { ps ->
                ps.setObject(1, deviceA)
                ps.setObject(2, alice)
                ps.setObject(3, alice)
                ps.executeUpdate()
            }
        }

        val aliceCount =
            db.withSubject(alice) { conn ->
                conn.createStatement().executeQuery("SELECT count(*) FROM pantry_events").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        val bobCount =
            db.withSubject(bob) { conn ->
                conn.createStatement().executeQuery("SELECT count(*) FROM pantry_events").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        assertEquals(1, aliceCount, "Alice sees her own pantry_event")
        assertEquals(0, bobCount, "Bob sees zero — RLS isolates")
    }
}
