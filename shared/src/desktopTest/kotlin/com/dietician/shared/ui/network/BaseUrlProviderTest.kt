package com.dietician.shared.ui.network

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [BaseUrlProvider] desktop actual reads env / -D / fallback. We can set a
 * `-D` system property at runtime here to verify the read path; the env path
 * is exercised in production.
 */
class BaseUrlProviderTest {

    @Test
    fun `respects -Ddietician_host system property`() {
        val prior = System.getProperty("dietician.host")
        try {
            System.setProperty("dietician.host", "https://example.test:9999")
            val provider = BaseUrlProvider()
            assertTrue(provider.baseUrl == "https://example.test:9999")
        } finally {
            if (prior == null) {
                System.clearProperty("dietician.host")
            } else {
                System.setProperty("dietician.host", prior)
            }
        }
    }

    @Test
    fun `falls back to default tailscale magic-DNS hostname`() {
        val prior = System.getProperty("dietician.host")
        try {
            System.clearProperty("dietician.host")
            // Env override is process-level; can't unset reliably from JVM.
            // If env is set, the provider returns that; otherwise default.
            val envOverride = System.getenv(DIETICIAN_HOST_OVERRIDE)
            val provider = BaseUrlProvider()
            val expected = envOverride?.takeIf { it.isNotBlank() } ?: DEFAULT_VPS_HOST
            assertTrue(provider.baseUrl == expected)
        } finally {
            if (prior != null) System.setProperty("dietician.host", prior)
        }
    }
}
