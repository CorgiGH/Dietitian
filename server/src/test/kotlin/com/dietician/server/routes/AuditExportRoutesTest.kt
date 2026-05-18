package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.SessionStore
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.SESSION_COOKIE
import com.dietician.server.repo.AuditRepository
import com.dietician.server.repo.ConsentRepository
import com.dietician.server.repo.CredentialRepository
import com.dietician.server.repo.EventRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class AuditExportRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "audit_export_app_pw"
        private const val PASSPHRASE = "audit-test-passphrase"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS audit_export_app")
                    st.execute("CREATE ROLE audit_export_app LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO audit_export_app")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO audit_export_app")
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
        val d = DatabaseFactory(pg.jdbcUrl, "audit_export_app", APP_PW)
        dbRef = d
        return d
    }

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    private fun freshModule(db: DatabaseFactory, sessions: SessionStore) = module {
        single { db }
        single { sessions }
        single { RateLimiter() }
        single { SubjectRepository(get<DatabaseFactory>()) }
        single { EventRepository(get<DatabaseFactory>()) }
        single { ConsentRepository(get<DatabaseFactory>()) }
        single { CredentialRepository(get<DatabaseFactory>(), passphraseOverride = PASSPHRASE) }
        single { AuditRepository(get<DatabaseFactory>()) }
        single { AuditLogWriter(get<DatabaseFactory>()) }
        single { MagicLinkService() }
        single { NoopEmailSender() }
        single {
            AuthService(
                subjects = get(),
                magicLinks = get(),
                sessions = get(),
                email = get<NoopEmailSender>(),
                audit = get(),
            )
        }
    }

    private fun createSubjectAndCookie(db: DatabaseFactory, sessions: SessionStore): Pair<UUID, String> {
        val email = "ae-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("AuditExport", email)
        return subjectId to sessions.create(subjectId).sessionId
    }

    @Test
    fun `GET me audit format json returns rows + writes audit_export entry`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installAuditExportRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        // Seed an audit row so the export has at least one entry.
        AuditLogWriter(db).write(
            subjectId = subjectId,
            kind = "sign_in",
            extra = JsonObject(mapOf("method" to JsonPrimitive("magic_link"))),
        )
        val resp = client.get("/me/audit?format=json") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.size >= 1)
        // Audit row about the export itself.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' AND kind = 'audit_export'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `GET me audit format pdf returns application pdf bytes`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installAuditExportRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        AuditLogWriter(db).write(subjectId = subjectId, kind = "sign_in")
        val resp = client.get("/me/audit?format=pdf") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val bytes = resp.bodyAsBytes()
        assertTrue(bytes.size > 100, "PDF must be non-trivial size")
        // PDF magic bytes %PDF
        assertEquals(0x25, bytes[0].toInt() and 0xFF) // %
        assertEquals(0x50, bytes[1].toInt() and 0xFF) // P
        assertEquals(0x44, bytes[2].toInt() and 0xFF) // D
        assertEquals(0x46, bytes[3].toInt() and 0xFF) // F
        stopKoinIfRunning()
    }

    @Test
    fun `GET me audit with invalid format returns 400`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installAuditExportRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.get("/me/audit?format=html") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `GET me dsar returns a ZIP with manifest`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installAuditExportRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        AuditLogWriter(db).write(subjectId = subjectId, kind = "sign_in")
        val resp = client.get("/me/dsar") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val bytes = resp.bodyAsBytes()
        // ZIP magic bytes PK\x03\x04
        assertEquals(0x50, bytes[0].toInt() and 0xFF)
        assertEquals(0x4B, bytes[1].toInt() and 0xFF)
        // Walk entries and assert manifest.json is present.
        val entries = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries += e.name
                e = zis.nextEntry
            }
        }
        assertTrue("manifest.json" in entries, "DSAR ZIP must contain manifest.json")
        assertTrue(entries.any { it == "audit_log.jsonl" })
        stopKoinIfRunning()
    }

    @Test
    fun `GET me audit without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installAuditExportRoutes()
        }
        val resp = client.get("/me/audit")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }
}
