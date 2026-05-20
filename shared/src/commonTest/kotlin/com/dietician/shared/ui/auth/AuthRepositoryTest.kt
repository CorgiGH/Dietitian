package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    @AfterTest
    fun cleanup() {
        SessionStore.clear()
    }

    // Mirrors the real HttpClientFactory config exactly — same JSON settings and
    // `expectSuccess = false`. AuthRepository must classify 401/5xx itself by
    // inspecting `response.status`; it cannot rely on Ktor throwing a
    // ResponseException, because the production client never sets expectSuccess.
    private fun mockClient(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        val engine = MockEngine { req -> handler(req) }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            expectSuccess = false
        }
    }

    @Test
    fun `requestMagicLink returns success on 202`() = runTest {
        val client = mockClient {
            respond("", HttpStatusCode.Accepted, headersOf("Content-Type", "application/json"))
        }
        val repo = AuthRepository(client, "http://test")
        val result = repo.requestMagicLink("victor@example.com")
        assertTrue(result.isSuccess, "Expected requestMagicLink to succeed on 202")
    }

    @Test
    fun `requestMagicLink succeeds on 202 for unknown email (anti-enum)`() = runTest {
        // Plan-3 contract: always 202 even on unknown email — no 404 leak.
        val client = mockClient {
            respond("", HttpStatusCode.Accepted, headersOf("Content-Type", "application/json"))
        }
        val repo = AuthRepository(client, "http://test")
        val result = repo.requestMagicLink("nobody@nowhere.example")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `verifyMagicLink stores session on 200`() = runTest {
        // Exact wire shape emitted by the server (AuthRoutes MagicLinkVerifyResponse):
        // `expiresAtMs` is a numeric epoch-millis Long, NOT an ISO-8601 string.
        val client = mockClient {
            respond(
                """{"sessionId":"sess-1","subjectId":"victor-uuid","expiresAtMs":1780358400000}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", "application/json"),
            )
        }
        val repo = AuthRepository(client, "http://test")
        val result = repo.verifyMagicLink("token-abc")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("sess-1", session.sessionId)
        assertEquals("victor-uuid", session.subjectId)
        assertEquals(1780358400000L, session.expiresAtMs)
        // Session must be persisted to SessionStore on success.
        assertEquals(session, SessionStore.current.value)
        assertEquals("victor-uuid", SessionStore.currentSubjectId)
    }

    @Test
    fun `verifyMagicLink returns InvalidToken on 401`() = runTest {
        val client = mockClient {
            respond(
                """{"error":"invalid_token"}""",
                HttpStatusCode.Unauthorized,
                headersOf("Content-Type", "application/json"),
            )
        }
        val repo = AuthRepository(client, "http://test")
        val result = repo.verifyMagicLink("bad-token")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<AuthError.InvalidToken>(error)
        // SessionStore not populated on failure.
        assertNull(SessionStore.current.value)
    }

    @Test
    fun `verifyMagicLink maps 5xx to Server error`() = runTest {
        val client = mockClient {
            respond(
                """{"error":"internal"}""",
                HttpStatusCode.InternalServerError,
                headersOf("Content-Type", "application/json"),
            )
        }
        val repo = AuthRepository(client, "http://test")
        val result = repo.verifyMagicLink("token")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<AuthError.Server>(error)
        assertEquals(500, error.statusCode)
    }
}
