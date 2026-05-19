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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopOutboxReplayTest {
    @Test
    fun `replayPending posts commit for each outbox row + removes them`() =
        runTest {
            val driver =
                JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                    DieticianDatabase.Schema.create(it)
                }
            val db = DieticianDatabase(driver)
            db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
                "kA", null, "h", 100L, 100L, 0L, "claudemax",
            )
            db.`0009_audit_pending_outboxQueries`.insertOutboxRow(
                "kB", null, "h", 200L, 200L, 0L, "claudemax",
            )

            val commits = mutableListOf<String>()
            val engine =
                MockEngine { req ->
                    // Parse body to extract idempotencyKey
                    val body = req.body.toString()
                    val keyMatch = Regex("\"idempotencyKey\":\"([^\"]+)\"").find(body)
                    keyMatch?.groupValues?.get(1)?.let { commits += it }
                    respond(
                        content = """{"auditId":"a","status":"orphaned"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val http =
                CoachHttpClient(
                    HttpClient(engine) {
                        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                    },
                    baseUrl = "https://test",
                )

            DesktopOutboxReplay(db, http).replayPending()

            assertEquals(setOf("kA", "kB"), commits.toSet())
            assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("kA").executeAsOneOrNull())
            assertNull(db.`0009_audit_pending_outboxQueries`.findByKey("kB").executeAsOneOrNull())
        }
}
