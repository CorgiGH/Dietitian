package com.dietician.server.routes

import com.dietician.server.auth.AuthService
import com.dietician.server.coach.CoachCommitRequest
import com.dietician.server.coach.CoachReserveRejected
import com.dietician.server.coach.CoachReserveRequest
import com.dietician.server.coach.CoachService
import com.dietician.server.coach.CoachServiceReserveResult
import com.dietician.server.coach.CoachStreamRequest
import com.dietician.server.middleware.requireSubject
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.koin.ktor.ext.inject
import kotlin.coroutines.coroutineContext

private const val SSE_HEARTBEAT_MS = 25_000L
private const val SSE_IDLE_TIMEOUT_MS = 90_000L

/**
 * iter-11 — 2-phase commit Coach surface.
 *
 *   POST /coach/reserve — server inserts a pending audit row + locks budget.
 *   POST /coach/commit  — idempotent finalize w/ token usage + status.
 *   POST /coach/stream  — SSE, lands in T9. This file ships the 2PC skeleton.
 *
 * Auth: every route requires a valid session — [requireSubject] responds 401
 * on missing/invalid session and the handler short-circuits.
 */
fun Application.installCoachRoutes() {
    val coach: CoachService by inject()
    val authService: AuthService by inject()
    routing {
        route("/coach") {
            post("/reserve") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val req = call.receive<CoachReserveRequest>()
                when (val r = coach.reserve(subjectId, req)) {
                    is CoachServiceReserveResult.Reserved ->
                        call.respond(HttpStatusCode.OK, r.envelope)
                    is CoachServiceReserveResult.Rejected ->
                        call.respond(
                            HttpStatusCode.PaymentRequired,
                            CoachReserveRejected(reason = r.reason, capUsd = r.capUsd, spentUsd = r.spentUsd),
                        )
                }
            }
            post("/commit") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val req = call.receive<CoachCommitRequest>()
                val resp = coach.commit(subjectId, req)
                call.respond(HttpStatusCode.OK, resp)
            }
            post("/stream") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val req = call.receive<CoachStreamRequest>()
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    val writeMutex = Mutex()
                    val scope = CoroutineScope(coroutineContext)
                    val heartbeat =
                        scope.launch {
                            while (isActive) {
                                delay(SSE_HEARTBEAT_MS)
                                writeMutex.withLock {
                                    write(": heartbeat\n\n")
                                    flush()
                                }
                            }
                        }
                    try {
                        withTimeout(SSE_IDLE_TIMEOUT_MS) {
                            coach.streamServerRouted(subjectId, req).collect { chunk ->
                                writeMutex.withLock {
                                    write("data: $chunk\n\n")
                                    flush()
                                }
                            }
                        }
                        // gate-1 fix #4 — terminal SSE frame so the client can
                        // distinguish "stream completed cleanly" from "socket
                        // dropped mid-stream". Matches OpenAI Responses API
                        // `response.completed` + Anthropic `message_stop` shape.
                        writeMutex.withLock {
                            write("event: end\ndata: ${req.idempotencyKey}\n\n")
                            flush()
                        }
                    } catch (e: TimeoutCancellationException) {
                        writeMutex.withLock {
                            write("event: timeout\ndata: idle-timeout (${e.message})\n\n")
                            flush()
                        }
                    } finally {
                        heartbeat.cancel()
                    }
                }
            }
        }
    }
}
