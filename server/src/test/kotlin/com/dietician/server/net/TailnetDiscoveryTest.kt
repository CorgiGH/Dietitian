package com.dietician.server.net

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * TailnetDiscovery unit tests via the [TailnetDiscovery.discoverWith]
 * env-lookup overload.
 *
 * The override path is the only host-deterministic surface to exercise in
 * unit tests. The `tailscale ip -4` exec path is host-dependent — the dev
 * machine may have Tailscale installed (would resolve to a Tailnet IP) or
 * not (would throw the operator banner). Either outcome is correct for the
 * exec path; we trust it via live VPS smoke + code review.
 *
 * We don't mutate the JVM env via reflection (JDK 21 forbids it via module
 * boundaries) — instead we pass an `envLookup` lambda.
 */
class TailnetDiscoveryTest {
    @Test
    fun `DIETICIAN_HOST_OVERRIDE returned verbatim`() {
        val out = TailnetDiscovery.discoverWith { name ->
            if (name == "DIETICIAN_HOST_OVERRIDE") "127.0.0.1" else null
        }
        assertEquals("127.0.0.1", out)
    }

    @Test
    fun `DIETICIAN_HOST_OVERRIDE accepts arbitrary host strings for dev`() {
        val out = TailnetDiscovery.discoverWith { name ->
            if (name == "DIETICIAN_HOST_OVERRIDE") "100.64.1.2" else null
        }
        assertEquals("100.64.1.2", out)
    }

    @Test
    fun `non-override env keys ignored`() {
        // Only DIETICIAN_HOST_OVERRIDE short-circuits; passing other vars
        // must not affect the override branch.
        val out = TailnetDiscovery.discoverWith { name ->
            when (name) {
                "DIETICIAN_HOST_OVERRIDE" -> "100.99.99.99"
                "SOMETHING_ELSE" -> "ignored"
                else -> null
            }
        }
        assertEquals("100.99.99.99", out)
    }
}
