package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.SessionStore
import com.dietician.server.coach.CoachRepository
import com.dietician.server.coach.CoachReserveResponse
import com.dietician.server.coach.CoachService
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.SESSION_COOKIE
import com.dietician.server.repo.BudgetRepository
import com.dietician.server.repo.SubjectRepository
import com.dietician.shared.llm.PiiRedactor
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
class CoachRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_test")

        private const val APP_PW = "coach_app_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS coach_app_test")
                    st.execute("CREATE ROLE coach_app_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO coach_app_test")
                    st.execute(
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO coach_app_test",
                    )
                    st.execute("GRANT EXECUTE ON FUNCTION refund_orphaned(INT) TO coach_app_test")
                    st.execute("GRANT EXECUTE ON FUNCTION consume_or_fail(uuid, text, int, int) TO coach_app_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "coach_app_test", APP_PW)
        dbRef = d
        return d
    }

    private fun stopKoinIfRunning() {
        if (GlobalContext.getOrNull() != null) GlobalContext.stopKoin()
    }

    private fun createSubjectAndCookie(
        db: DatabaseFactory,
        sessions: SessionStore,
    ): Pair<UUID, String> {
        val email = "h-${UUID.randomUUID()}@example.com"
        val subjectId = SubjectRepository(db).create("Test", email)
        val session = sessions.create(subjectId)
        return subjectId to session.sessionId
    }

    private fun coachKoinModule(db: DatabaseFactory, sessions: SessionStore) =
        module {
            single { db }
            single { sessions }
            single { SubjectRepository(get<DatabaseFactory>()) }
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
            single { CoachRepository(get<DatabaseFactory>()) }
            single { BudgetRepository(get<DatabaseFactory>()) }
            single { PiiRedactor() }
            single { CoachService(get(), get(), get()) }
        }

    @Test
    fun `POST coach reserve without session returns 401`() =
        testApplication {
            stopKoinIfRunning()
            val db = freshDb()
            val sessions = SessionStore()
            application {
                install(Koin) { modules(coachKoinModule(db, sessions)) }
                install(ContentNegotiation) { json() }
                installCoachRoutes()
            }
            val resp =
                client.post("/coach/reserve") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en",""" +
                            """"provider":"claudemax","estimatedCostCents":5,"reservationTtlSeconds":60}""",
                    )
                }
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
            stopKoinIfRunning()
        }

    @Test
    fun `POST coach reserve returns envelope on valid session`() =
        testApplication {
            stopKoinIfRunning()
            val db = freshDb()
            val sessions = SessionStore()
            application {
                install(Koin) { modules(coachKoinModule(db, sessions)) }
                install(ContentNegotiation) { json() }
                installCoachRoutes()
            }
            val (_, sessionId) = createSubjectAndCookie(db, sessions)
            val resp =
                client.post("/coach/reserve") {
                    cookie(SESSION_COOKIE, sessionId)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en",""" +
                            """"provider":"claudemax","estimatedCostCents":5,"reservationTtlSeconds":60}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = Json.decodeFromString(CoachReserveResponse.serializer(), resp.bodyAsText())
            assertTrue(body.auditId.isNotBlank())
            stopKoinIfRunning()
        }

    @Test
    fun `POST coach commit updates audit row and returns response`() =
        testApplication {
            stopKoinIfRunning()
            val db = freshDb()
            val sessions = SessionStore()
            application {
                install(Koin) { modules(coachKoinModule(db, sessions)) }
                install(ContentNegotiation) { json() }
                installCoachRoutes()
            }
            val (_, sessionId) = createSubjectAndCookie(db, sessions)
            val key = UUID.randomUUID().toString()
            client.post("/coach/reserve") {
                cookie(SESSION_COOKIE, sessionId)
                contentType(ContentType.Application.Json)
                setBody(
                    """{"idempotencyKey":"$key","prompt":"hi","locale":"en",""" +
                        """"provider":"claudemax","estimatedCostCents":5,"reservationTtlSeconds":60}""",
                )
            }
            val resp =
                client.post("/coach/commit") {
                    cookie(SESSION_COOKIE, sessionId)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"idempotencyKey":"$key","status":"success","promptTokens":10,""" +
                            """"completionTokens":20,"costCents":4,"provider":"claudemax",""" +
                            """"latencyMs":2200,"responseHash":"abc"}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            stopKoinIfRunning()
        }
}
