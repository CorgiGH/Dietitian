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
import com.dietician.server.repo.EventRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.client.request.cookie
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
class RedactRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "redact_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS redact_app_test")
                    st.execute("CREATE ROLE redact_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO redact_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO redact_app_test")
                    st.execute("GRANT EXECUTE ON FUNCTION subject_redact(uuid) TO redact_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "redact_app_test", APP_PW)
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
        val email = "rd-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("RedactTest", email)
        return subjectId to sessions.create(subjectId).sessionId
    }

    @Test
    fun `DELETE me subject id redacts own subject + cascades events + audit row`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installRedactRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        // Seed one event so the cascade count is non-zero.
        val evt = UUID.randomUUID()
        val sku = UUID.randomUUID()
        val payload = """{"event_uuid":"$evt","device_id":"dev-x","originated_at":"2026-05-18T10:00:00Z","synced_at":"2026-05-18T10:00:01Z","sku_uuid":"$sku","delta_qty":1.0,"unit":"g","subject_id":"$subjectId"}"""
        EventRepository(db).upsert(subjectId, "pantry_events", payload)

        val resp = client.delete("/me/subject/$subjectId") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("redacted", json["status"]?.jsonPrimitive?.content)
        val counts = json["counts"]?.jsonObject
        assertTrue((counts?.get("pantry_events")?.jsonPrimitive?.content?.toInt() ?: 0) >= 1)

        // Audit row (subject_id NULL — system row).
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id IS NULL AND kind = 'subject_redact'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        // Tombstone row exists.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM tombstone_events WHERE subject_id = '$subjectId'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `DELETE me subject id of OTHER subject returns 403`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installRedactRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val otherId = UUID.randomUUID()
        val resp = client.delete("/me/subject/$otherId") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `DELETE me subject id without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installRedactRoutes()
        }
        val resp = client.delete("/me/subject/${UUID.randomUUID()}")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `DELETE me subject id with bogus uuid returns 400`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installRedactRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.delete("/me/subject/not-a-uuid") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        stopKoinIfRunning()
    }
}
