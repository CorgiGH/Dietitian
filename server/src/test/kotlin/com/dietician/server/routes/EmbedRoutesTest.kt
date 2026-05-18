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
import com.dietician.server.repo.BudgetRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.client.request.cookie
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import kotlin.test.assertNotNull

@Testcontainers
class EmbedRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "embed_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS embed_app_test")
                    st.execute("CREATE ROLE embed_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO embed_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO embed_app_test")
                    st.execute("GRANT EXECUTE ON FUNCTION consume_or_fail(uuid, text, int, int) TO embed_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "embed_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    private fun freshModule(db: DatabaseFactory, sessions: SessionStore, rl: RateLimiter = RateLimiter()) =
        module {
            single { db }
            single { sessions }
            single { rl }
            single { SubjectRepository(get<DatabaseFactory>()) }
            single { BudgetRepository(get<DatabaseFactory>()) }
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
        val email = "e-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("Embed", email)
        return subjectId to sessions.create(subjectId).sessionId
    }

    @Test
    fun `embed returns 501 stub when authenticated within budget`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installEmbedRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.post("/embed") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"text":"hello world","corpus":"meals"}""")
        }
        assertEquals(HttpStatusCode.NotImplemented, resp.status)
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("stub", json["status"]?.jsonPrimitive?.content)
        assertNotNull(json["message"])
        stopKoinIfRunning()
    }

    @Test
    fun `embed without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installEmbedRoutes()
        }
        val resp = client.post("/embed") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"x","corpus":"y"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `embed enforces 30 per minute non-Victor`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        val rl = RateLimiter()
        application {
            install(Koin) { modules(freshModule(db, sessions, rl)) }
            install(ContentNegotiation) { json() }
            installEmbedRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        // 30 allowed.
        repeat(30) {
            val r = client.post("/embed") {
                cookie(SESSION_COOKIE, sessionId)
                contentType(ContentType.Application.Json)
                setBody("""{"text":"hi","corpus":"meals"}""")
            }
            assertEquals(HttpStatusCode.NotImplemented, r.status)
        }
        // 31st throttles.
        val throttled = client.post("/embed") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"text":"hi","corpus":"meals"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, throttled.status)
        assertEquals("60", throttled.headers[HttpHeaders.RetryAfter])
        stopKoinIfRunning()
    }

    @Test
    fun `embed returns 402 on budget cap`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installEmbedRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        // Seed a 0¢ cap so the first call fails budget.
        db.withSubject(subjectId) { conn ->
            conn.prepareStatement(
                "INSERT INTO llm_budget(subject_id, provider, period_starts_at, period_ends_at, cost_cents_cap) " +
                    "VALUES (?, 'voyage', date_trunc('month', now())::DATE, (date_trunc('month', now()) + INTERVAL '1 month - 1 day')::DATE, 0) " +
                    "ON CONFLICT DO NOTHING",
            ).use { ps ->
                ps.setObject(1, subjectId)
                ps.executeUpdate()
            }
        }
        val resp = client.post("/embed") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"text":"hi","corpus":"meals"}""")
        }
        assertEquals(HttpStatusCode.PaymentRequired, resp.status)
        stopKoinIfRunning()
    }
}
