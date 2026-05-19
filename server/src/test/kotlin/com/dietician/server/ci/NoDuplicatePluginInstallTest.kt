package com.dietician.server.ci

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Council 1779188964 tracer-bullet deploy regression.
 *
 * The first VPS deploy aborted on `io.ktor.server.application.DuplicatePluginException:
 * Conflicting application plugin is already installed with the same key as
 * 'CallLogging'`. The plugin was installed twice — once in
 * [com.dietician.server.observability.installObservability] (configured with
 * `/metrics` filter) and again bare in `Application.module()`. Existing route-
 * level tests mounted individual `installXxxRoutes()` calls instead of the full
 * `Application.module()`, so the double-install never surfaced in CI.
 *
 * This test enforces that any Ktor plugin appears in at most one `install(...)`
 * call across `:server/src/main/kotlin`. If a future change reintroduces the
 * bug — explicit double-install, or moving an install out of
 * `installObservability` without removing the duplicate in `Application.module()`
 * — this test fires before the JAR ever reaches the VPS.
 *
 * Pairs with the in-repo systemd unit at `infra/systemd/dietician-backend.service`
 * which carries the council's heap-cap / OOMScoreAdjust mitigations.
 */
class NoDuplicatePluginInstallTest {
    /**
     * Server-context (Ktor Application) plugins that must appear exactly once
     * across `server/src/main/kotlin`. Excludes Ktor *client* plugins (also
     * named ContentNegotiation, HttpTimeout, etc.) which are installed on
     * HttpClient blocks in LlmModule and SHOULD appear multiple times (one per
     * client instance).
     */
    private val serverOnlyPlugins = setOf(
        "CallLogging",
        "MicrometerMetrics",
        "StatusPages",
        "WebSockets",
        "Koin",
    )

    @Test
    fun `every server-context Ktor plugin is installed at most once`() {
        val root = Paths.get("src/main/kotlin")
        val occurrences = mutableMapOf<String, MutableList<String>>()
        for (plugin in serverOnlyPlugins) {
            // Match `install(Plugin)` or `install(Plugin) {` but NOT calls
            // inside HttpClient blocks. The whitelist of serverOnlyPlugins
            // ensures the regex only fires on Application-level installs.
            val regex = Regex("""install\(\s*$plugin\s*[){]""")
            Files.walk(root)
                .filter { it.extension == "kt" }
                .forEach { path ->
                    val source = Files.readString(path)
                    if (regex.containsMatchIn(source)) {
                        // Count occurrences per file too — a single file
                        // installing the same plugin twice would still bomb
                        // out at runtime.
                        val matchCount = regex.findAll(source).count()
                        repeat(matchCount) {
                            occurrences.getOrPut(plugin) { mutableListOf() } += path.toString()
                        }
                    }
                }
        }
        val duplicates = occurrences.filterValues { it.size > 1 }
        assertTrue(
            duplicates.isEmpty(),
            buildString {
                appendLine(
                    "[Council 1779188964] One or more server-context Ktor plugins are " +
                        "install()-ed in more than one place under server/src/main/kotlin. " +
                        "This caused DuplicatePluginException to abort the first tracer-bullet " +
                        "VPS deploy on 2026-05-19. Each plugin must have exactly ONE install() " +
                        "site (typically inside installObservability / installXxxRoutes / " +
                        "Application.module).",
                )
                duplicates.forEach { (plugin, sites) ->
                    appendLine("  - $plugin installed in:")
                    sites.forEach { appendLine("      $it") }
                }
            },
        )
    }

    @Test
    fun `CallLogging is installed exactly once and is owned by installObservability`() {
        val root = Paths.get("src/main/kotlin")
        val callLoggingSites =
            Files.walk(root)
                .filter { it.extension == "kt" }
                .filter { Files.readString(it).contains("install(CallLogging") }
                .map { it.toString() }
                .toList()
        assertEquals(
            1,
            callLoggingSites.size,
            "CallLogging must appear in exactly one install() site. Found: $callLoggingSites",
        )
        assertTrue(
            callLoggingSites.single().endsWith("Metrics.kt"),
            "CallLogging install must live inside installObservability (Metrics.kt) so the " +
                "/metrics filter applies. Found at: ${callLoggingSites.single()}",
        )
    }
}
