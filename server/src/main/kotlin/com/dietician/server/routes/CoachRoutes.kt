package com.dietician.server.routes

import com.dietician.server.auth.AuthService
import com.dietician.server.coach.CoachCommitRequest
import com.dietician.server.coach.CoachReserveRejected
import com.dietician.server.coach.CoachReserveRequest
import com.dietician.server.coach.CoachService
import com.dietician.server.coach.CoachServiceReserveResult
import com.dietician.server.middleware.requireSubject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

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
        }
    }
}
