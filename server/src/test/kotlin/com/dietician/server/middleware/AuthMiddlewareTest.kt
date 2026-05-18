package com.dietician.server.middleware

import com.dietician.server.auth.AuthService
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [requireSubject] + [extractSessionId] + [resolveSubject].
 *
 * Backed by mocked [AuthService] so we exercise the middleware in isolation
 * without a DB. The real AuthService surface is integration-tested in
 * [com.dietician.server.auth.AuthServiceTest].
 */
class AuthMiddlewareTest {
    private fun fakeAuth(sessionToSubject: Map<String, UUID>): AuthService {
        val svc = mockk<AuthService>(relaxed = true)
        every { svc.currentSubject(any()) } answers {
            val sid = firstArg<String?>()
            sessionToSubject[sid]
        }
        return svc
    }

    @Test
    fun `requireSubject responds 401 when no cookie or bearer`() = testApplication {
        val auth = fakeAuth(emptyMap())
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/me") {
                    val sub = call.requireSubject(auth) ?: return@get
                    call.respond(mapOf("subject" to sub.toString()))
                }
            }
        }
        val resp = client.get("/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `cookie session resolves to subject id`() = testApplication {
        val subjectId = UUID.randomUUID()
        val auth = fakeAuth(mapOf("session-abc" to subjectId))
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/me") {
                    val sub = call.requireSubject(auth) ?: return@get
                    call.respond(HttpStatusCode.OK, mapOf("subject" to sub.toString()))
                }
            }
        }
        val resp = client.get("/me") { cookie(SESSION_COOKIE, "session-abc") }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains(subjectId.toString()))
    }

    @Test
    fun `bearer header resolves to subject id when cookie absent`() = testApplication {
        val subjectId = UUID.randomUUID()
        val auth = fakeAuth(mapOf("session-xyz" to subjectId))
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/me") {
                    val sub = call.requireSubject(auth) ?: return@get
                    call.respond(HttpStatusCode.OK, mapOf("subject" to sub.toString()))
                }
            }
        }
        val resp = client.get("/me") { header("Authorization", "Bearer session-xyz") }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains(subjectId.toString()))
    }

    @Test
    fun `cookie wins over bearer when both present`() = testApplication {
        val cookieSubject = UUID.randomUUID()
        val bearerSubject = UUID.randomUUID()
        val auth = fakeAuth(
            mapOf(
                "cookie-sid" to cookieSubject,
                "bearer-sid" to bearerSubject,
            ),
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/who") {
                    val sub = call.requireSubject(auth) ?: return@get
                    call.respond(HttpStatusCode.OK, mapOf("subject" to sub.toString()))
                }
            }
        }
        val r = client.get("/who") {
            cookie(SESSION_COOKIE, "cookie-sid")
            header("Authorization", "Bearer bearer-sid")
        }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(
            r.bodyAsText().contains(cookieSubject.toString()),
            "cookie subject should win over bearer subject",
        )
    }

    @Test
    fun `unknown session id rejected with 401`() = testApplication {
        val auth = fakeAuth(emptyMap())
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/me") {
                    val sub = call.requireSubject(auth) ?: return@get
                    call.respond(HttpStatusCode.OK, mapOf("subject" to sub.toString()))
                }
            }
        }
        val resp = client.get("/me") { cookie(SESSION_COOKIE, "ghost") }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
