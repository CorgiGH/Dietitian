package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.util.AttributeKey

/**
 * Ktor plugin that injects `X-Subject-Id: <subjectId>` on every outbound request when
 * a [Session] is active.
 *
 * **RC15 (Council 1779120600):** Plan-3 RLS predicates use both the JWT `sub` claim
 * (server-side) and the `X-Subject-Id` header (client-asserted). If the two drift —
 * for example during a magic-link re-verify race where the singleton was updated
 * mid-flight — the server rejects with 403 but the UI sees a blank state. By asserting
 * `X-Subject-Id == JWT.sub` server-side, Plan-3 can return a typed `403 SUBJECT_MISMATCH`
 * error that the client distinguishes from "no data".
 *
 * Plan-3.5 will add the JWT-issuance path; until then [SessionInterceptor] is the
 * authoritative source of the header (the cookie carries the session id).
 *
 * The plugin reads from [SessionStore] on every request — no captured state — so a
 * sign-out wipes the header on the next outbound call without needing to rebuild the
 * [HttpClient].
 *
 * Use:
 * ```kotlin
 * HttpClient(engine) {
 *     install(SessionInterceptor)
 * }
 * ```
 */
val SessionInterceptor = createClientPlugin(name = "SessionInterceptor") {
    onRequest { request, _ ->
        SessionStore.currentSubjectId?.let { subjectId ->
            request.header(SUBJECT_ID_HEADER, subjectId)
        }
    }
}

const val SUBJECT_ID_HEADER: String = "X-Subject-Id"
