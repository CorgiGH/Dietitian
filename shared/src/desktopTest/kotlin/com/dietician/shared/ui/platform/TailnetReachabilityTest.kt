package com.dietician.shared.ui.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * RC16 — TailnetReachability desktop actual.
 *
 * Pins the false-on-unreachable path. We point the probe at an unroutable IP
 * (TEST-NET-1, RFC 5737) with a port that won't respond — the actual MUST
 * complete within the 3s timeout and return false rather than throw.
 *
 * The true-path can't be exercised in CI without a real server; the production
 * smoke happens at the platform shell when the user opens the app against
 * their actual VPS.
 */
class TailnetReachabilityTest {

    @Test
    fun `returns false when host unreachable within timeout`() = runTest {
        // 192.0.2.0/24 is TEST-NET-1 — guaranteed not routable. Port 81 picked
        // because :80 might trip a transparent proxy on dev networks.
        val reachable = TailnetReachability.check("http://192.0.2.1:81")
        assertFalse(reachable, "Unreachable host MUST yield false")
    }

    @Test
    fun `returns false on malformed URL`() = runTest {
        val reachable = TailnetReachability.check("not-a-url")
        assertFalse(reachable, "Malformed URL MUST yield false (caught)")
    }
}
