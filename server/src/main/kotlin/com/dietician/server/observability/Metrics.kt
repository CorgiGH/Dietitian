package com.dietician.server.observability

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.event.Level

/**
 * Singleton Prometheus registry shared between [installObservability]
 * (per-request timers via Ktor MicrometerMetrics) and [MetricsExporter]
 * (HTTP scrape endpoint on `:9091/metrics`).
 *
 * Counters/timers populated automatically by `MicrometerMetrics`:
 *   - `ktor.http.server.requests` per route + method + status
 *   - JVM defaults (heap, GC pauses, thread counts)
 *
 * Custom counters defined in [Counters]; Plan-3 hand-emitters
 * (AuditLogWriter, AuthService, etc.) increment them.
 */
object Metrics {
    val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

/**
 * Named counters surfaced for in-app emission. Names mirror the dotted
 * convention in plan-3 §5.5 (`dietician.<surface>.<event>.total`).
 */
object Counters {
    val auditLogWritesTotal = Metrics.registry.counter("dietician.audit_log.writes.total")
    val authSignInTotal = Metrics.registry.counter("dietician.auth.signin.total")
    val authSignOutTotal = Metrics.registry.counter("dietician.auth.signout.total")
    val authSignOutAllTotal = Metrics.registry.counter("dietician.auth.signout_all.total")
    val rlsContextSetTotal = Metrics.registry.counter("dietician.rls_context.set.total")
}

/**
 * Installs Ktor-side observability: MicrometerMetrics plugin + call logging.
 * Per-route latency timers + status counters land on [Metrics.registry] and
 * are scraped via [MetricsExporter].
 *
 * `/metrics` requests are EXCLUDED from call-logging to avoid log spam.
 */
fun Application.installObservability() {
    install(MicrometerMetrics) {
        registry = Metrics.registry
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            !call.request.local.uri.startsWith("/metrics")
        }
    }
}
