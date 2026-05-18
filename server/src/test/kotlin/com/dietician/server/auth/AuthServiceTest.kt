package com.dietician.server.auth

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.repo.SubjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the magic-link flow. Anti-enumeration smoke + audit
 * row emission asserted against a live Postgres container so we exercise
 * the real SubjectRepository + AuditLogWriter contract.
 */
@Testcontainers
class AuthServiceTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "auth_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS auth_app_test")
                    st.execute("CREATE ROLE auth_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO auth_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO auth_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "auth_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun buildService(db: DatabaseFactory, mailer: NoopEmailSender = NoopEmailSender()): Pair<AuthService, NoopEmailSender> {
        val subjects = SubjectRepository(db)
        val audit = AuditLogWriter(db)
        val ml = MagicLinkService()
        val ss = SessionStore()
        return AuthService(
            subjects = subjects,
            magicLinks = ml,
            sessions = ss,
            email = mailer,
            audit = audit,
            fromAddress = "onboarding@resend.dev",
            magicLinkBaseUrl = "https://test.local/auth/magic-link",
        ) to mailer
    }

    @Test
    fun `requestMagicLink for known email sends email and returns knownSubject=true`() = runBlocking {
        val db = freshDb()
        val (svc, mailer) = buildService(db)
        val email = "known-${UUID.randomUUID()}@example.com"
        SubjectRepository(db).create("Carol", email)

        val outcome = svc.requestMagicLink(email)
        assertTrue(outcome.knownSubject)
        assertTrue(outcome.emailSent)
        assertEquals(1, mailer.sent.size)
        val msg = mailer.sent.first()
        assertEquals(email, msg.to)
        assertEquals("onboarding@resend.dev", msg.from)
        assertTrue(msg.htmlBody.contains("magic-link?token="), "body should contain magic-link URL")
    }

    @Test
    fun `requestMagicLink for unknown email does NOT send and reports knownSubject=false`() = runBlocking {
        val db = freshDb()
        val (svc, mailer) = buildService(db)
        val outcome = svc.requestMagicLink("nobody-${UUID.randomUUID()}@example.com")
        assertFalse(outcome.knownSubject)
        assertFalse(outcome.emailSent)
        assertEquals(0, mailer.sent.size, "no email must be sent for unknown subjects (anti-enumeration)")
    }

    @Test
    fun `verifyMagicLink consumes token + creates session + emits SIGN_IN audit row`() = runBlocking {
        val db = freshDb()
        val email = "victor-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("Victor", email)
        val (svc, mailer) = buildService(db)
        svc.requestMagicLink(email)
        val token = extractToken(mailer.sent.first().htmlBody)

        val session = svc.verifyMagicLink(token)
        assertNotNull(session)
        assertEquals(subjectId, session.subjectId)

        // Second verify should fail (single-use).
        assertNull(svc.verifyMagicLink(token), "token must be single-use")

        // currentSubject resolves cookie back to subject id.
        assertEquals(subjectId, svc.currentSubject(session.sessionId))

        // Audit row must exist.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' AND kind = 'sign_in'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1, "expected SIGN_IN audit row")
            }
        }
    }

    @Test
    fun `signOutAll invalidates every session and emits audit row`() = runBlocking {
        val db = freshDb()
        val email = "soa-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("SOAVictor", email)
        val (svc, mailer) = buildService(db)
        // Create two sessions for the same subject.
        svc.requestMagicLink(email)
        val t1 = extractToken(mailer.sent.last().htmlBody)
        val s1 = svc.verifyMagicLink(t1)
        assertNotNull(s1)
        svc.requestMagicLink(email)
        val t2 = extractToken(mailer.sent.last().htmlBody)
        val s2 = svc.verifyMagicLink(t2)
        assertNotNull(s2)

        val killed = svc.signOutAll(subjectId)
        assertEquals(2, killed)
        assertNull(svc.currentSubject(s1.sessionId))
        assertNull(svc.currentSubject(s2.sessionId))

        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' AND kind = 'sign_out_all_sessions'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1, "expected SIGN_OUT_ALL_SESSIONS audit row")
            }
        }
    }

    private fun extractToken(htmlBody: String): String {
        val rx = Regex("""token=([A-Za-z0-9_-]+)""")
        return rx.find(htmlBody)?.groupValues?.get(1)
            ?: error("no token found in HTML body")
    }
}
