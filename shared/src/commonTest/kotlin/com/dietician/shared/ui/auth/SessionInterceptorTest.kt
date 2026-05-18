package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Per RC15 (Council 1779120600), SessionInterceptor MUST attach `X-Subject-Id`
 * to every outbound authenticated request — and MUST omit it when no session is
 * active.
 *
 * This is the client side of the SubjectIdConsistencyTest contract — the server
 * side (Plan-3.5 JWT validation) is out of scope for Batch B.
 */
class SessionInterceptorTest {

    @BeforeTest
    fun setup() {
        SessionStore.clear()
    }

    @AfterTest
    fun cleanup() {
        SessionStore.clear()
    }

    @Test
    fun `attaches X-Subject-Id header when session active`() = runTest {
        SessionStore.set(
            Session(
                sessionId = "sess-1",
                subjectId = "victor-uuid",
                expiresAt = "2026-06-01T00:00:00Z",
            ),
        )
        var capturedSubjectId: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedSubjectId = request.headers[SUBJECT_ID_HEADER]
            respond("ok", HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
        }) {
            install(SessionInterceptor)
        }
        client.get("http://test/me")
        assertEquals("victor-uuid", capturedSubjectId)
    }

    @Test
    fun `omits header when no session`() = runTest {
        // SessionStore.clear() ran in BeforeTest.
        var capturedSubjectId: String? = "preset"
        val client = HttpClient(MockEngine { request ->
            capturedSubjectId = request.headers[SUBJECT_ID_HEADER]
            respond("ok", HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
        }) {
            install(SessionInterceptor)
        }
        client.get("http://test/health")
        assertNull(capturedSubjectId)
    }

    @Test
    fun `header tracks SessionStore on subsequent requests`() = runTest {
        SessionStore.set(
            Session(
                sessionId = "sess-a",
                subjectId = "subject-a",
                expiresAt = "2026-06-01T00:00:00Z",
            ),
        )
        var captured: String? = null
        val client = HttpClient(MockEngine { request ->
            captured = request.headers[SUBJECT_ID_HEADER]
            respond("ok", HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
        }) {
            install(SessionInterceptor)
        }
        client.get("http://test/me")
        assertEquals("subject-a", captured)

        // Rotate session → next request carries the new subjectId without rebuilding the client.
        SessionStore.set(
            Session(
                sessionId = "sess-b",
                subjectId = "subject-b",
                expiresAt = "2026-06-01T00:00:00Z",
            ),
        )
        client.get("http://test/me")
        assertEquals("subject-b", captured)

        // Sign-out wipes the header on the next call.
        SessionStore.clear()
        client.get("http://test/me")
        assertNull(captured)
    }
}

/**
 * SubjectIdConsistencyTest stub (RC15). Plan-3.5 will ship a JWT-issuance flow and
 * the server-side `X-Subject-Id == JWT.sub` enforcement; for Batch B we assert the
 * client invariant: when a session is set, the header is attached, and when cleared,
 * it is not — guaranteeing no stale `X-Subject-Id` ever leaks after sign-out.
 */
class SubjectIdConsistencyTest {

    @AfterTest
    fun cleanup() {
        SessionStore.clear()
    }

    @Test
    fun `clearing session removes header on next request`() = runTest {
        SessionStore.set(
            Session(
                sessionId = "s",
                subjectId = "victor",
                expiresAt = "2026-06-01T00:00:00Z",
            ),
        )
        var captured: String? = null
        val client = HttpClient(MockEngine { request ->
            captured = request.headers[SUBJECT_ID_HEADER]
            respond("ok", HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
        }) {
            install(SessionInterceptor)
        }
        client.get("http://test/me")
        assertEquals("victor", captured)
        SessionStore.clear()
        captured = "should-be-cleared"
        client.get("http://test/me")
        assertNull(captured, "X-Subject-Id must NOT leak after SessionStore.clear()")
    }
}
