package com.dietician.shared.ui.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Authenticated session record returned by Plan-3 `POST /auth/magic-link/verify`.
 *
 * `sessionId` is opaque to the client (server-issued); `subjectId` is the principal.
 * Cookie-based auth still works on Android/Desktop (CIO + OkHttp both honour Set-Cookie)
 * but [sessionId] is also held client-side so an offline session can be probed without
 * forcing a network round-trip.
 *
 * Per RC15 (Council 1779120600): every authenticated outbound request MUST carry
 * `X-Subject-Id: <subjectId>` and the server compares to JWT `sub` before applying RLS.
 * [SessionInterceptor] installs that header.
 *
 * Per Batch B brief: `expiresAt` is an ISO-8601 string returned by Plan-3 (Plan-3.5 may
 * upgrade to numeric epoch — kept loose for forward-compat).
 */
@Serializable
data class Session(
    val sessionId: String,
    val subjectId: String,
    val expiresAt: String,
)

/**
 * In-memory session holder with an expect/actual hook for file-backed persistence
 * across process restarts.
 *
 * Batch B ships the in-memory baseline only — Android EncryptedSharedPreferences +
 * Desktop pgp_sym persistence layer lands in Batch E Task 27 platform wiring. Until
 * then the user re-paste/re-clicks the magic-link on app restart (cookie may still
 * survive on Android via OkHttp jar; CIO is in-memory).
 *
 * Thread-safe via [MutableStateFlow]. UI subscribes to [current] to react to
 * sign-in / sign-out.
 */
object SessionStore {
    private val _current = MutableStateFlow<Session?>(null)
    val current: StateFlow<Session?> = _current.asStateFlow()

    /** Convenience accessor used by [SessionInterceptor] on every outbound request. */
    val currentSubjectId: String? get() = _current.value?.subjectId

    fun set(session: Session?) {
        _current.value = session
    }

    fun clear() {
        _current.value = null
    }
}
