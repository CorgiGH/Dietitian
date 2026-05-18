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
import com.dietician.server.repo.ConsentRepository
import com.dietician.server.repo.CredentialRepository
import com.dietician.server.repo.SubjectRepository
import io.ktor.client.request.cookie
import io.ktor.client.request.delete
import io.ktor.client.request.get
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

@Testcontainers
class MeRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "me_app_test_pw"
        private const val PASSPHRASE = "test-passphrase-12345"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS me_app_test")
                    st.execute("CREATE ROLE me_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO me_app_test")
                    st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO me_app_test")
                }
            }
            System.setProperty("DIETICIAN_CREDENTIAL_PASSPHRASE", PASSPHRASE)
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
        val d = DatabaseFactory(pg.jdbcUrl, "me_app_test", APP_PW)
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
        single { CredentialRepository(get<DatabaseFactory>(), passphraseOverride = PASSPHRASE) }
        single { ConsentRepository(get<DatabaseFactory>()) }
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
        val email = "me-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("MeTest", email)
        return subjectId to sessions.create(subjectId).sessionId
    }

    @Test
    fun `GET me returns subject profile with has_byok false initially`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.get("/me") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val json = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(subjectId.toString(), json["subject_id"]?.jsonPrimitive?.content)
        assertEquals(false, json["has_byok"]?.jsonPrimitive?.content?.toBoolean())
        stopKoinIfRunning()
    }

    @Test
    fun `POST me byok stores a key + GET me reflects has_byok true`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.post("/me/byok") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"openrouter","key":"sk-or-test-123"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val me = Json.parseToJsonElement(
            client.get("/me") { cookie(SESSION_COOKIE, sessionId) }.bodyAsText(),
        ).jsonObject
        assertEquals(true, me["has_byok"]?.jsonPrimitive?.content?.toBoolean())
        // Audit row written.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' AND kind = 'credential_grant'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `DELETE me byok provider revokes credential + audit row`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        client.post("/me/byok") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"openrouter","key":"k-1"}""")
        }
        val del = client.delete("/me/byok/openrouter") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, del.status)
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' AND kind = 'subject_credential_revoked'",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 1)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `POST me byok invalid provider returns 400`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.post("/me/byok") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"provider":"chatgpt-pro","key":"k"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        stopKoinIfRunning()
    }

    @Test
    fun `POST me consent grant + withdraw round-trips`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
        val grant = client.post("/me/consent") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"scope":"process_meal_data","granted":true}""")
        }
        assertEquals(HttpStatusCode.OK, grant.status)
        val withdraw = client.post("/me/consent") {
            cookie(SESSION_COOKIE, sessionId)
            contentType(ContentType.Application.Json)
            setBody("""{"scope":"process_meal_data","granted":false}""")
        }
        assertEquals(HttpStatusCode.OK, withdraw.status)
        // Audit rows.
        DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
            c.createStatement().executeQuery(
                "SELECT count(*) FROM audit_log WHERE subject_id = '$subjectId' " +
                    "AND kind IN ('consent_grant','consent_withdraw')",
            ).use { rs ->
                rs.next()
                assertTrue(rs.getInt(1) >= 2)
            }
        }
        stopKoinIfRunning()
    }

    @Test
    fun `GET me sessions lists the active session`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val (_, sessionId) = createSubjectAndCookie(db, sessions)
        val resp = client.get("/me/sessions") { cookie(SESSION_COOKIE, sessionId) }
        assertEquals(HttpStatusCode.OK, resp.status)
        val arr = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertTrue(arr.size >= 1)
        val first = arr.first().jsonObject
        assertNotNull(first["sessionId"])
        assertNotNull(first["createdAtMs"])
        assertNotNull(first["expiresAtMs"])
        stopKoinIfRunning()
    }

    @Test
    fun `GET me without session returns 401`() = testApplication {
        stopKoinIfRunning()
        val db = freshDb()
        val sessions = SessionStore()
        application {
            install(Koin) { modules(freshModule(db, sessions)) }
            install(ContentNegotiation) { json() }
            installMeRoutes()
        }
        val resp = client.get("/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        stopKoinIfRunning()
    }
}
