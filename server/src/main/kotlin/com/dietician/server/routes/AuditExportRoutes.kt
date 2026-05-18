package com.dietician.server.routes

import com.dietician.server.audit.AuditLogActions
import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.export.AuditPdfRenderer
import com.dietician.server.export.DsarZipBuilder
import com.dietician.server.middleware.requireSubject
import com.dietician.server.repo.AuditRepository
import com.dietician.server.repo.ConsentRepository
import com.dietician.server.repo.CredentialRepository
import com.dietician.server.repo.EventRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Plan-3 Task 31: subject-facing audit + DSAR exports.
 *
 * - GET /me/audit?format=json|pdf&from=ISO&to=ISO  (Art 12)
 * - GET /me/dsar                                   (Art 15 ZIP)
 *
 * Default window for audit: last 30 days. Caller can widen via from/to
 * (ISO-8601). Hard cap: 10k rows per export (prevents accidental DoS).
 *
 * Every export writes its OWN audit row (`audit_export` / `dsar_export`)
 * so the subject can later see when they pulled their own data.
 */
fun Application.installAuditExportRoutes() {
    val authService: AuthService by inject()
    val auditLog: AuditLogWriter by inject()
    val auditRepo: AuditRepository by inject()
    val eventRepo: EventRepository by inject()
    val consentRepo: ConsentRepository by inject()
    val credentialRepo: CredentialRepository by inject()

    routing {
        route("/me") {
            get("/audit") {
                val subjectId = call.requireSubject(authService) ?: return@get
                val format = call.parameters["format"] ?: "json"
                val from = runCatching {
                    call.parameters["from"]?.let { Instant.parse(it) }
                }.getOrNull()
                    ?: Instant.now().minus(30, ChronoUnit.DAYS)
                val to = runCatching {
                    call.parameters["to"]?.let { Instant.parse(it) }
                }.getOrNull() ?: Instant.now()

                if (format != "json" && format != "pdf") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "format_must_be_json_or_pdf"),
                    )
                    return@get
                }

                val rows = auditRepo.list(subjectId, from, to)
                auditLog.write(
                    subjectId = subjectId,
                    kind = "audit_export",
                    extra = JsonObject(
                        mapOf(
                            "format" to JsonPrimitive(format),
                            "rows" to JsonPrimitive(rows.size),
                            "from" to JsonPrimitive(from.toString()),
                            "to" to JsonPrimitive(to.toString()),
                        ),
                    ),
                )

                when (format) {
                    "json" -> {
                        val arr = JsonArray(
                            rows.map { r ->
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive(r.id.toString()),
                                        "occurred_at" to JsonPrimitive(r.occurredAt.toInstant().toString()),
                                        "kind" to JsonPrimitive(r.kind),
                                        "model" to JsonPrimitive(r.model ?: ""),
                                        "cost_cents" to JsonPrimitive(r.costCents ?: -1),
                                        "request_id" to JsonPrimitive(r.requestId ?: ""),
                                        "extra" to JsonPrimitive(r.extraJson ?: ""),
                                    ),
                                )
                            },
                        )
                        call.respond(HttpStatusCode.OK, arr)
                    }
                    "pdf" -> {
                        val bytes = AuditPdfRenderer.render(subjectId, rows, from, to)
                        call.response.headers.append(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=audit-${subjectId}-${LocalDate.now(ZoneOffset.UTC)}.pdf",
                        )
                        call.respondBytes(bytes, ContentType.Application.Pdf)
                    }
                }
            }

            get("/dsar") {
                val subjectId = call.requireSubject(authService) ?: return@get
                val zipBytes = DsarZipBuilder.build(
                    subjectId = subjectId,
                    eventRepo = eventRepo,
                    consentRepo = consentRepo,
                    credentialRepo = credentialRepo,
                    auditRepo = auditRepo,
                )
                auditLog.write(
                    subjectId = subjectId,
                    kind = AuditLogActions.DSAR_EXPORT,
                    extra = JsonObject(
                        mapOf("size_bytes" to JsonPrimitive(zipBytes.size)),
                    ),
                )
                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=${DsarZipBuilder.filename(subjectId)}",
                )
                call.respondBytes(zipBytes, ContentType.Application.Zip)
            }
        }
    }
}

// Single Json instance for the route — keeps the encoder cached.
@Suppress("unused")
private val JsonCfg = Json { encodeDefaults = true }
