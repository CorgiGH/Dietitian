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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

@Testcontainers
class SyncRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "sync_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS sync_app_test")
                    st.execute("CREATE ROLE sync_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO sync_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sync_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "sync_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    /**
     * Helper: create a subject and session using the same Koin graph the routes
     * see, by directly calling SessionStore from the module's singletons via a
     * back-door — tests are allowed to do this because Koin singletons are
     * shared within an application.
     */
    private fun createSubjectAndCookie(
        db: DatabaseFactory,
        sessions: SessionStore,
    ): Pair<UUID, String> {
        val email = "h-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("Test", email)
        val session = sessions.create(subjectId)
        return subjectId to session.sessionId
    }

    @Test
    fun `push inserts valid envelopes and returns accepted`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val mod = module {
            single { db }
            single { sessions }
            single { SubjectRepository(get<DatabaseFactory>()) }
            single { EventRepository(get<DatabaseFactory>()) }
            single { AuditLogWriter(get<DatabaseFactory>()) }
            single { MagicLinkService() }
            single { RateLimiter() }
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
        application {
            install(Koin) { modules(mod) }
            install(ContentNegotiation) { json() }
            installSyncRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        val evt = UUID.randomUUID()
        val sku = UUID.randomUUID()
        val deviceId = "dev-${UUID.randomUUID()}"
        val payload = """{"event_uuid":"$evt","device_id":"$deviceId","originated_at":"2026-05-18T10:00:00Z","synced_at":"2026-05-18T10:00:01Z","sku_uuid":"$sku","delta_qty":1.0,"unit":"g","subject_id":"$subjectId"}"""
        val body = """{"deviceId":"$deviceId","events":[{"tableName":"pantry_events","eventUuid":"$evt","payloadJson":${kotlinx.serialization.json.JsonPrimitive(payload).toString()}}]}"""
        val resp = client.post("/sync/push") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(1, json["accepted"]!!.jsonArray.size)
        assertEquals(0, json["rejected"]!!.jsonArray.size)
        stopKoinIfRunning()
    }

    @Test
    fun `push without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val mod = module {
            single { db }
            single { sessions }
            single { SubjectRepository(get<DatabaseFactory>()) }
            single { EventRepository(get<DatabaseFactory>()) }
            single { AuditLogWriter(get<DatabaseFactory>()) }
            single { MagicLinkService() }
            single { RateLimiter() }
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
        application {
            install(Koin) { modules(mod) }
            install(ContentNegotiation) { json() }
            installSyncRoutes()
        }
        val resp = client.post("/sync/push") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"d","events":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `push duplicate event_uuid is accepted idempotently`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val mod = module {
            single { db }
            single { sessions }
            single { SubjectRepository(get<DatabaseFactory>()) }
            single { EventRepository(get<DatabaseFactory>()) }
            single { AuditLogWriter(get<DatabaseFactory>()) }
            single { MagicLinkService() }
            single { RateLimiter() }
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
        application {
            install(Koin) { modules(mod) }
            install(ContentNegotiation) { json() }
            installSyncRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        val evt = UUID.randomUUID()
        val sku = UUID.randomUUID()
        val deviceId = "dev-${UUID.randomUUID()}"
        val payload = """{"event_uuid":"$evt","device_id":"$deviceId","originated_at":"2026-05-18T10:00:00Z","synced_at":"2026-05-18T10:00:01Z","sku_uuid":"$sku","delta_qty":1.0,"unit":"g","subject_id":"$subjectId"}"""
        val body = """{"deviceId":"$deviceId","events":[{"tableName":"pantry_events","eventUuid":"$evt","payloadJson":${kotlinx.serialization.json.JsonPrimitive(payload).toString()}}]}"""
        // First push.
        client.post("/sync/push") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        // Second push w/ same eventUuid — accepted (idempotent).
        val resp = client.post("/sync/push") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(1, json["accepted"]!!.jsonArray.size, "duplicate must still be reported as accepted (idempotent)")
        assertEquals(0, json["rejected"]!!.jsonArray.size)
        stopKoinIfRunning()
    }

}
