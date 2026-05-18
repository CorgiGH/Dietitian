package com.dietician.server.observability

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

/**
 * A SECOND HTTP server on `:9091` (Tailnet IP only) exposing
 * `GET /metrics` in Prometheus text-exposition format from
 * [Metrics.registry].
 *
 * Separated from the main app server so:
 *   - `/metrics` is on a distinct port the operator can firewall separately.
 *   - Scrape traffic never appears in the main server's access log.
 *   - A main-server crash doesn't take metrics with it (and vice-versa).
 *
 * Caller MUST `.stop(...)` the returned [EmbeddedServer] on shutdown.
 */
object MetricsExporter {
    private val log = LoggerFactory.getLogger(MetricsExporter::class.java)

    /**
     * Starts the metrics server. Returns the embedded server so the caller
     * can wire a shutdown hook.
     *
     * @param host bind address — caller passes the Tailnet IP discovered by
     *   [com.dietician.server.net.TailnetDiscovery]. NEVER pass `0.0.0.0`
     *   in production.
     */
    fun start(host: String, port: Int = 9091): EmbeddedServer<*, *> {
        log.info("MetricsExporter starting on {}:{}/metrics", host, port)
        val server = embeddedServer(CIO, port = port, host = host) {
            routing {
                get("/metrics") {
                    call.respondText(
                        Metrics.registry.scrape(),
                        contentType = ContentType.parse("text/plain; version=0.0.4; charset=utf-8"),
                    )
                }
            }
        }
        server.start(wait = false)
        return server
    }
}
