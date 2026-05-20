package com.dietician.shared.ui.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Ktor plugin that injects auth headers on every outbound request when a [Session]
 * is active:
 *   - `Authorization: Bearer <sessionId>` — the credential the server uses to
 *     authenticate the call.
 *   - `X-Subject-Id: <subjectId>` — the client-asserted principal (RC15).
 *
 * **Why Bearer, not the cookie:** the server's `/auth/magic-link/verify` sets a
 * `Secure` session cookie. Android/Desktop reach the VPS over plain `http://`
 * (Tailscale provides the transport encryption, but the URL scheme is not
 * `https`), so a spec-compliant cookie jar would refuse to store or replay a
 * `Secure` cookie. The server's `extractSessionId` accepts `Authorization: Bearer
 * <sessionId>` as an explicit CLI/mobile fallback — that is the path used here.
 *
 * **RC15 (Council 1779120600):** Plan-3 RLS predicates use both the JWT `sub` claim
 * (server-side) and the `X-Subject-Id` header (client-asserted). If the two drift —
 * for example during a magic-link re-verify race where the singleton was updated
 * mid-flight — the server rejects with 403 but the UI sees a blank state. By asserting
 * `X-Subject-Id == JWT.sub` server-side, Plan-3 can return a typed `403 SUBJECT_MISMATCH`
 * error that the client distinguishes from "no data".
 *
 * The plugin reads from [SessionStore] on every request — no captured state — so a
 * sign-out wipes both headers on the next outbound call without needing to rebuild
 * the [HttpClient].
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
        SessionStore.current.value?.let { session ->
            request.header(HttpHeaders.Authorization, "Bearer ${session.sessionId}")
            request.header(SUBJECT_ID_HEADER, session.subjectId)
        }
    }
}

const val SUBJECT_ID_HEADER: String = "X-Subject-Id"
