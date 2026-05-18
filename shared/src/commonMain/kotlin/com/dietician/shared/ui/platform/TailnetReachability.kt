package com.dietician.shared.ui.platform

/**
 * Pre-auth reachability probe — verifies the VPS endpoint behind Tailscale is
 * reachable BEFORE the magic-link / Home flow starts.
 *
 * **RC16 (Council 1779120600):** If Tailscale is disconnected on the user's device,
 * every Plan-3 endpoint resolves to a `MagicDNS` short-name that fails DNS or TCP
 * with a generic `UnknownHostException` / `ConnectException`. Without a pre-auth
 * blocker the user sees a sequence of cryptic 30s timeouts at sign-in, food log
 * load, etc. — a UX black hole. The blocker short-circuits with a single
 * "Connect to Tailscale to use Dietician" full-screen modal + a Retry CTA.
 *
 * Implementation per platform:
 *   - **Android** (`TailnetReachability.android.kt`) — pings `<BASE_URL>/health` via
 *     OkHttp with a 3s connect+read timeout. Treats `200..299` + `401` as reachable
 *     (401 means the server SAW the request but rejected auth — Tailscale is OK).
 *     Any DNS/connect/read failure returns `false`.
 *   - **Desktop** (`TailnetReachability.desktop.kt`) — same pattern via CIO.
 *
 * Called from the platform shell BEFORE `DieticianApp` is rendered. Returns null
 * initial state → splash; false → blocker screen; true → app.
 *
 * Network-light by design: a single GET against `/health`, no auth header. The
 * server-side `/health` route (Plan-3 Task 2) returns 200 + `{ok: true}` with no
 * RLS / DB / session checks.
 */
expect object TailnetReachability {
    /**
     * Returns `true` iff a fast probe to the configured base URL succeeded with
     * a status code in `200..299` or `401`. Any throwable / timeout / DNS error
     * yields `false`. MUST complete within ~3s to keep first-paint snappy.
     */
    suspend fun check(baseUrl: String): Boolean
}
