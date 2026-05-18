package com.dietician.server

import com.dietician.server.net.TailnetDiscovery
import com.dietician.server.observability.MetricsExporter
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory

/**
 * Dietician VPS backend entry point.
 *
 * Bind address (Tailnet-only):
 *   1. `DIETICIAN_HOST_OVERRIDE` env (dev / CI / unit tests).
 *   2. `tailscale ip -4` subprocess output (prod).
 *   3. Refuse to start with operator banner (Council 1779120000 RC5).
 *
 * Port: `DIETICIAN_PORT` env, default 8081.
 *
 * Metrics: a SEPARATE Ktor server on `:9091/metrics` bound to the same
 * Tailnet IP (NOT public). See [MetricsExporter].
 *
 * Endpoints — see [Application.module] in `Application.kt`.
 */
fun main() {
    val log = LoggerFactory.getLogger("com.dietician.server.Main")
    val bindHost = TailnetDiscovery.discover()
    val bindPort = System.getenv("DIETICIAN_PORT")?.toIntOrNull() ?: 8081
    val metricsPort = System.getenv("DIETICIAN_METRICS_PORT")?.toIntOrNull() ?: 9091

    log.info("Dietician backend binding {}:{} (metrics :{})", bindHost, bindPort, metricsPort)

    // Start the metrics exporter first so the main server's startup latency
    // is observable from t=0.
    val metricsServer = MetricsExporter.start(host = bindHost, port = metricsPort)

    val mainServer = embeddedServer(
        factory = CIO,
        port = bindPort,
        host = bindHost,
        module = Application::module,
    )
    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Shutdown hook: stopping main + metrics servers")
            runCatching { mainServer.stop(2_000, 5_000) }
            runCatching { metricsServer.stop(2_000, 5_000) }
        },
    )
    mainServer.start(wait = true)
}
