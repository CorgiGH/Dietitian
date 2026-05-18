package com.dietician.server.net

import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Resolves the Tailscale IPv4 address this VPS should bind to.
 *
 * Lookup order (first match wins):
 *   1. Env `DIETICIAN_HOST_OVERRIDE` (explicit override — dev / CI / unit tests).
 *   2. `tailscale ip -4` subprocess output (first `100.x.y.z` line).
 *   3. Refuse to start with a banner-printable error (council 1779120000 RC5).
 *
 * Refusing to start on missing Tailscale is intentional: the Dietician backend
 * MUST never bind to a public IP. If Tailscale is down on the VPS, the operator
 * sees the banner + fixes Tailscale before re-launching.
 *
 * `bindAddress()` is process-startup-only — never invoked per-request.
 */
object TailnetDiscovery {
    private val log = LoggerFactory.getLogger(TailnetDiscovery::class.java)
    private val TAILNET_IPV4 = Regex("""^100\.\d+\.\d+\.\d+$""")

    /**
     * Returns the bind address. Throws [IllegalStateException] with an
     * operator-friendly banner if no Tailnet IP is discoverable AND no
     * override is set.
     */
    fun discover(): String = discoverWith { name -> System.getenv(name) }

    /**
     * Test-friendly overload accepting an env-lookup function. Production
     * callers use [discover].
     */
    fun discoverWith(envLookup: (String) -> String?): String {
        val devOverride = envLookup("DIETICIAN_HOST_OVERRIDE")
        if (!devOverride.isNullOrBlank()) {
            log.info("TailnetDiscovery: using DIETICIAN_HOST_OVERRIDE='{}'", devOverride)
            return devOverride
        }
        val result = runCatching {
            val proc = ProcessBuilder("tailscale", "ip", "-4")
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                error("tailscale ip -4 timed out after 5s")
            }
            val out = proc.inputStream.bufferedReader().readText().trim()
            require(proc.exitValue() == 0) { "tailscale ip -4 exited ${proc.exitValue()}: $out" }
            out.lines().firstOrNull { it.trim().matches(TAILNET_IPV4) }?.trim()
                ?: error("tailscale ip -4 returned no 100.x.y.z address (got: '$out')")
        }
        return result.getOrElse { e ->
            throw IllegalStateException(
                """

                ════════════════════════════════════════════════════════════════════
                BANNER: Dietician backend refused to start.
                Cause: cannot discover Tailscale IP.
                  Reason: ${e.message}
                Fix (pick one):
                  A. Set DIETICIAN_HOST_OVERRIDE=127.0.0.1 (dev / CI).
                  B. On the VPS:
                     1. sudo systemctl status tailscaled
                     2. sudo tailscale up --advertise-tags=tag:dietician-backend
                     3. tailscale ip -4   # confirm 100.x.y.z address returns
                     4. systemctl restart dietician-backend.service
                ════════════════════════════════════════════════════════════════════
                """.trimIndent(),
                e,
            )
        }
    }
}
