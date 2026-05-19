package com.dietician.shared.llm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.llm.net.CoachHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopCoachLlmGatewayTest {
    private fun newDb(): DieticianDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { DieticianDatabase.Schema.create(it) }
        return DieticianDatabase(driver)
    }

    private fun newHttp(): CoachHttpClient {
        val engine =
            MockEngine { req ->
                when {
                    req.url.encodedPath.endsWith("/coach/reserve") ->
                        respond(
                            content = """{"reservationId":"r","auditId":"a","redactedPromptHash":"h","reservedUntilEpochMs":1}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    req.url.encodedPath.endsWith("/coach/commit") ->
                        respond(
                            content = """{"auditId":"a","status":"committed"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    else -> error("unexpected path: ${req.url.encodedPath}")
                }
            }
        val http =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return CoachHttpClient(http, baseUrl = "https://test")
    }

    @Test
    fun `gateway writes outbox row, runs provider, posts commit, deletes outbox`() =
        runTest {
            val db = newDb()
            val provider =
                object : LocalCoachProvider {
                    override fun run(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
                        flowOf(
                            LlmChunk("Eat ", isDone = false),
                            LlmChunk("chicken.", isDone = true, tokenCount = 2),
                        )
                }
            val gw = DesktopCoachLlmGateway(db = db, http = newHttp(), provider = provider, uuid = { "k1" })
            val chunks = gw.streamCoachTurn("hi", CoachLocale.EN).toList()
            assertEquals(listOf("Eat ", "chicken."), chunks.map { it.text })
            assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("k1").executeAsOneOrNull())
        }

    @Test
    fun `gateway commits status=failed if provider throws mid-stream`() =
        runTest {
            val db = newDb()
            val failingProvider =
                object : LocalCoachProvider {
                    override fun run(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
                        flow {
                            emit(LlmChunk("Eat ", isDone = false))
                            throw RuntimeException("ClaudeMax crashed")
                        }
                }
            val gw = DesktopCoachLlmGateway(db = db, http = newHttp(), provider = failingProvider, uuid = { "k2" })
            runCatching { gw.streamCoachTurn("hi", CoachLocale.EN).toList() }
            // outbox row was deleted in finally — commit succeeded with failed status
            assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("k2").executeAsOneOrNull())
        }

    @Test
    fun `gateway writes outbox row BEFORE invoking provider (council saga contract)`() =
        runTest {
            val db = newDb()
            var providerCalled = false
            val provider =
                object : LocalCoachProvider {
                    override fun run(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
                        flow {
                            // At this point the outbox row MUST already exist.
                            val row = db.`0009_audit_pending_outboxQueries`.findByKey("k3").executeAsOneOrNull()
                            assertNotNull(row, "outbox row must exist before provider runs")
                            providerCalled = true
                            emit(LlmChunk("ok", isDone = true))
                        }
                }
            val gw = DesktopCoachLlmGateway(db = db, http = newHttp(), provider = provider, uuid = { "k3" })
            gw.streamCoachTurn("hi", CoachLocale.EN).toList()
            assertEquals(true, providerCalled)
        }
}
