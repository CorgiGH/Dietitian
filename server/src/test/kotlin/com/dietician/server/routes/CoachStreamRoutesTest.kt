package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.auth.MagicLinkService
import com.dietician.server.auth.NoopEmailSender
import com.dietician.server.auth.SessionStore
import com.dietician.server.coach.CoachRepository
import com.dietician.server.coach.CoachService
import com.dietician.server.db.DatabaseFactory
import com.dietician.server.db.runMigrations
import com.dietician.server.middleware.RateLimiter
import com.dietician.server.middleware.SESSION_COOKIE
import com.dietician.server.repo.BudgetRepository
import com.dietician.server.repo.SubjectRepository
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.llm.PiiRedactor
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
class CoachStreamRoutesTest {
    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<*> =
            PostgreSQLContainer("pgvector/pgvector:pg16").withDatabaseName("dietician_stream_test")

        private const val APP_PW = "coach_stream_test_pw"
        private var bootstrapped = false
        private var dbRef: DatabaseFactory? = null

        private fun bootstrap() {
            if (bootstrapped) return
            runMigrations(pg.jdbcUrl, pg.username, pg.password)
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { c ->
                c.createStatement().use { st ->
                    st.execute("DROP ROLE IF EXISTS coach_stream_test")
                    st.execute("CREATE ROLE coach_stream_test LOGIN PASSWORD '$APP_PW'")
                    st.execute("GRANT USAGE ON SCHEMA public TO coach_stream_test")
                    st.execute(
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO coach_stream_test",
                    )
                    st.execute("GRANT EXECUTE ON FUNCTION consume_or_fail(uuid, text, int, int) TO coach_stream_test")
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
        val d = DatabaseFactory(pg.jdbcUrl, "coach_stream_test", APP_PW)
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

    private fun coachKoinModule(
        db: DatabaseFactory,
        sessions: SessionStore,
        mockChunks: List<LlmChunk>,
    ) = module {
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
        single<LlmStream> {
            object : LlmStream {
                override fun streamRoute(request: LlmRequest): Flow<LlmChunk> = flowOf(*mockChunks.toTypedArray())
            }
        }
        single { CoachService(get(), get(), get(), get()) }
    }

    @Test
    fun `POST coach stream emits SSE data frames from mocked LlmStream`() =
        testApplication {
            stopKoinIfRunning()
            val db = freshDb()
            val sessions = SessionStore()
            val chunks =
                listOf(
                    LlmChunk("Hi ", isDone = false),
                    LlmChunk("Victor.", isDone = true, tokenCount = 2),
                )
            application {
                install(Koin) { modules(coachKoinModule(db, sessions, chunks)) }
                install(ContentNegotiation) { json() }
                installCoachRoutes()
            }
            val (_, sessionId) = createSubjectAndCookie(db, sessions)
            val resp =
                client.post("/coach/stream") {
                    cookie(SESSION_COOKIE, sessionId)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"idempotencyKey":"${UUID.randomUUID()}","prompt":"hi","locale":"en"}""",
                    )
                }
            assertEquals(HttpStatusCode.OK, resp.status)
            assertTrue(
                resp.headers[HttpHeaders.ContentType]?.contains("text/event-stream") ?: false,
                "expected SSE content-type, got: ${resp.headers[HttpHeaders.ContentType]}",
            )
            val body = resp.bodyAsText()
            assertTrue("data: Hi" in body, "expected first chunk in SSE body, got: $body")
            assertTrue("data: Victor." in body, "expected terminal chunk in SSE body, got: $body")
            stopKoinIfRunning()
        }

    @Test
    fun `POST coach stream writes audit row to success on completion`() =
        testApplication {
            stopKoinIfRunning()
            val db = freshDb()
            val sessions = SessionStore()
            val chunks = listOf(LlmChunk("ok", isDone = true, tokenCount = 1))
            application {
                install(Koin) { modules(coachKoinModule(db, sessions, chunks)) }
                install(ContentNegotiation) { json() }
                installCoachRoutes()
            }
            val (subjectId, sessionId) = createSubjectAndCookie(db, sessions)
            val key = UUID.randomUUID()
            client.post("/coach/stream") {
                cookie(SESSION_COOKIE, sessionId)
                contentType(ContentType.Application.Json)
                setBody("""{"idempotencyKey":"$key","prompt":"hi","locale":"en"}""")
            }
            val row = CoachRepository(db).findByIdempotencyKey(subjectId, key)!!
            assertEquals("success", row.status)
            stopKoinIfRunning()
        }
}
