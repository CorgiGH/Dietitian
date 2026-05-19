package com.dietician.shared.llm.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachHttpClientTest {
    private fun newClient(body: String, ct: String = "application/json"): HttpClient =
        HttpClient(
            MockEngine { _ ->
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ct),
                )
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun `reserve posts JSON and returns parsed envelope`() =
        runTest {
            val http =
                newClient(
                    """{"reservationId":"r","auditId":"a","redactedPromptHash":"h","reservedUntilEpochMs":1780000000000}""",
                )
            val c = CoachHttpClient(http, baseUrl = "https://test")
            val resp =
                c.reserve(
                    idempotencyKey = "k",
                    prompt = "p",
                    locale = "en",
                    provider = "claudemax",
                    estimatedCostCents = 5,
                    reservationTtlSeconds = 60,
                )
            assertEquals("a", resp.auditId)
        }

    @Test
    fun `commit posts JSON and returns response`() =
        runTest {
            val http = newClient("""{"auditId":"a","status":"success"}""")
            val c = CoachHttpClient(http, baseUrl = "https://test")
            val resp = c.commit("k", "success", 10, 20, 4, "claudemax", 2200, "h")
            assertEquals("success", resp.status)
        }

    @Test
    fun `stream parses SSE data frames into chunks and ignores heartbeat + event frames`() =
        runTest {
            val sseBody =
                ": heartbeat\n\n" +
                    "data: hello\n\n" +
                    "data:  world\n\n" +
                    "event: end\ndata: terminal\n\n"
            val http = newClient(sseBody, ct = "text/event-stream")
            val c = CoachHttpClient(http, baseUrl = "https://test")
            val chunks = c.stream("k", "p", "en").toList()
            assertEquals(listOf("hello", " world"), chunks)
        }
}
