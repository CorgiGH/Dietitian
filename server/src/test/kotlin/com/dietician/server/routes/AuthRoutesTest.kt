package com.dietician.server.routes

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.SessionStore
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.SESSION_COOKIE
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
import kotlin.test.assertTrue

/**
 * E2E tests for /auth routes against a real Testcontainers Postgres + real
 * AuthService + NoopEmailSender (so we can introspect the token without an
 * actual Resend account).
 *
 * Covers RC1 (magic-link only) + RC8 (sign-out-all) per council
 * 1779120000.
 */
@Testcontainers
class AuthRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "auth_routes_app_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS auth_routes_app")
                    st.execute("CREATE ROLE auth_routes_app LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO auth_routes_app")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO auth_routes_app")
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
        val d = DatabaseFactory(pg.jdbcUrl, "auth_routes_app", APP_PW)
        dbRef = d
        return d
    }

    /**
     * Builds a per-test Koin module wrapping the real DB + auth wiring.
     * Koin is stopped in test teardown via [stopKoinIfRunning].
     */
    private fun testModule(
        db: DatabaseFactory,
        mailer: NoopEmailSender,
    ) = module {
        single { db }
        single { mailer }
        single { SubjectRepository(get<DatabaseFactory>()) }
        single { AuditLogWriter(get<DatabaseFactory>()) }
        single { MagicLinkService() }
        single { SessionStore() }
        single { RateLimiter() }
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

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    @Test
    fun `magic-link request returns 202 for unknown email (anti-enumeration)`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val mailer = NoopEmailSender()
        application {
            install(Koin) { modules(testModule(db, mailer)) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        val resp = client.post("/auth/magic-link/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nobody@example.com"}""")
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertEquals(0, mailer.sent.size, "no email must be sent for unknown subjects")
        stopKoinIfRunning()
    }

    @Test
    fun `magic-link request 202 for known email AND triggers send`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val email = "victor-${UUID.randomUUID()}@example.com"
        SubjectRepository(db).create("Victor", email)
        val mailer = NoopEmailSender()
        application {
            install(Koin) { modules(testModule(db, mailer)) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        val resp = client.post("/auth/magic-link/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertEquals(1, mailer.sent.size)
        stopKoinIfRunning()
    }

    @Test
    fun `verify consumes token, sets HttpOnly Secure SameSite cookie, returns session`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val email = "vfy-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("Verify", email)
        val mailer = NoopEmailSender()
        application {
            install(Koin) { modules(testModule(db, mailer)) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        // Request -> get token from mailer body
        client.post("/auth/magic-link/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        val token = extractToken(mailer.sent.last().htmlBody)
        val resp = client.post("/auth/magic-link/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(subjectId.toString(), body["subjectId"]?.jsonPrimitive?.content)
        val sessionId = body["sessionId"]?.jsonPrimitive?.content
        assertNotNull(sessionId)

        // Set-Cookie header must include HttpOnly + Secure + SameSite=Strict + Path=/.
        val setCookie = resp.headers[HttpHeaders.SetCookie]
        assertNotNull(setCookie)
        assertTrue(setCookie.contains(SESSION_COOKIE), "Set-Cookie missing session cookie name")
        assertTrue(setCookie.contains("HttpOnly", ignoreCase = true), "cookie must be HttpOnly")
        assertTrue(setCookie.contains("Secure", ignoreCase = true), "cookie must be Secure")
        assertTrue(setCookie.contains("SameSite=Strict", ignoreCase = true), "cookie must be SameSite=Strict")
        assertTrue(setCookie.contains("Path=/", ignoreCase = true), "cookie path must be /")
        stopKoinIfRunning()
    }

    @Test
    fun `verify with bogus token returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        application {
            install(Koin) { modules(testModule(db, NoopEmailSender())) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        val resp = client.post("/auth/magic-link/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"bogus-no-issue"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `sign-out-all-sessions kills all sessions and writes audit row`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val email = "soa-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("SOA", email)
        val mailer = NoopEmailSender()
        application {
            install(Koin) { modules(testModule(db, mailer)) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        // Issue a session via verify so we have a real cookie.
        client.post("/auth/magic-link/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        val token = extractToken(mailer.sent.last().htmlBody)
        val verify = client.post("/auth/magic-link/verify") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token"}""")
        }
        val sessionId = Json.parseToJsonElement(verify.bodyAsText())
            .jsonObject["sessionId"]?.jsonPrimitive?.content
        assertNotNull(sessionId)

        val resp = client.post("/auth/sign-out-all-sessions") {
            cookie(SESSION_COOKIE, sessionId)
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val killed = Json.parseToJsonElement(resp.bodyAsText())
            .jsonObject["sessionsKilled"]?.jsonPrimitive?.content?.toInt()
        assertEquals(1, killed)

        // Audit row written.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log " +
                    "WHERE subject_id = '$subjectId' AND kind = '${AuditLogActions.SIGN_OUT_ALL_SESSIONS}'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `sign-out-all-sessions without cookie returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        application {
            install(Koin) { modules(testModule(db, NoopEmailSender())) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        val resp = client.post("/auth/sign-out-all-sessions")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `magic-link request rate-limited to 5 per hour per email`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val email = "rl-${UUID.randomUUID()}@example.com"
        SubjectRepository(db).create("RateLimited", email)
        val mailer = NoopEmailSender()
        application {
            install(Koin) { modules(testModule(db, mailer)) }
            install(ContentNegotiation) { json() }
            installAuthRoutes()
        }
        // 5 sends allowed.
        repeat(5) {
            client.post("/auth/magic-link/request") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"$email"}""")
            }
        }
        assertEquals(5, mailer.sent.size)
        // 6th call still returns 202 (anti-enumeration) but does NOT send.
        val resp = client.post("/auth/magic-link/request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email"}""")
        }
        assertEquals(HttpStatusCode.Accepted, resp.status)
        assertEquals(5, mailer.sent.size, "6th call must be throttled (no extra send)")
        stopKoinIfRunning()
    }

    private fun extractToken(htmlBody: String): String {
        val rx = Regex("""token=([A-Za-z0-9_-]+)""")
        return rx.find(htmlBody)?.groupValues?.get(1)
            ?: error("no token found in HTML body")
    }
}

