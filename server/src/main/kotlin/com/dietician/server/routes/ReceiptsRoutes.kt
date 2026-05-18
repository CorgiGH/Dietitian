package com.dietician.server.routes

import com.dietician.server.audit.AuditLogWriter
import com.dietician.server.auth.AuthService
import com.dietician.server.middleware.requireSubject
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.ktor.ext.inject
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

/**
 * Plan-3 Task 24 second half: receipts upload.
 *
 * Per Council A20 (Plan-3 doc §5.3): the receipt-upload route accepts BOTH
 *   - camera-OCR (`source=camera_ocr`) — phone-snapped jpeg/png raw bytes
 *   - Mega CONNECT (`source=mega_connect`) — receipt webhook payload bytes
 *
 * Pipeline (V1 first-ship):
 *   1. Save raw bytes immutably to `/storage/llm-raw/<rawId>.bin` per the
 *      spec's "always-store-raw-LLM-input" rule. Path is overridable via
 *      `DIETICIAN_LLM_RAW_DIR` env (tests point at a tmp dir).
 *   2. Audit-log row `receipt_upload` with `{receipt_id, raw_ref, source, bytes}`.
 *   3. Respond 202 + `{receipt_id, status: "queued_for_ocr"}`.
 *
 * NOTE: OCR + meal_event extraction is a Plan-2 + Plan-6 deliverable. This
 * route stops at "receive + persist + announce queued". The eventual worker
 * picks up rows from the receipts_queue table (deferred to Plan-6 schema add).
 * For first-ship the audit-log entry IS the queue — Plan-6 will sweep
 * `kind = 'receipt_upload'` rows newer than its high-water mark.
 */
fun Application.installReceiptsRoutes() {
    val auditLog: AuditLogWriter by inject()
    val authService: AuthService by inject()

    // Override resolution: env > system-property > default. The system-property
    // path is for test injection (`@TempDir` cooperation) since JVM doesn't
    // expose `setenv`. Both knobs share the same name for operator clarity.
    val rawDirOverride = System.getenv("DIETICIAN_LLM_RAW_DIR")
        ?: System.getProperty("DIETICIAN_LLM_RAW_DIR")
    val rawDir = (rawDirOverride ?: "/storage/llm-raw").let { Paths.get(it) }

    routing {
        route("/receipts") {
            post("/upload") {
                val subjectId = call.requireSubject(authService) ?: return@post
                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                var source = "camera_ocr"
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> imageBytes = part.provider().toByteArray()
                        is PartData.FormItem -> if (part.name == "source") source = part.value
                        else -> {}
                    }
                    part.dispose()
                }
                if (imageBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing_image"))
                    return@post
                }
                if (source !in ALLOWED_SOURCES) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "invalid_source",
                            "allowed" to ALLOWED_SOURCES.joinToString(","),
                        ),
                    )
                    return@post
                }
                val rawId = UUID.randomUUID()
                val rawPath = rawDir.resolve("$rawId.bin")
                Files.createDirectories(rawPath.parent)
                Files.write(rawPath, imageBytes!!)

                val receiptId = UUID.randomUUID()
                auditLog.write(
                    subjectId = subjectId,
                    kind = "receipt_upload",
                    extra = JsonObject(
                        mapOf(
                            "receipt_id" to JsonPrimitive(receiptId.toString()),
                            "raw_ref" to JsonPrimitive(rawId.toString()),
                            "source" to JsonPrimitive(source),
                            "bytes" to JsonPrimitive(imageBytes!!.size),
                        ),
                    ),
                )
                call.respond(
                    HttpStatusCode.Accepted,
                    mapOf(
                        "receipt_id" to receiptId.toString(),
                        "status" to "queued_for_ocr",
                    ),
                )
            }
        }
    }
}

/** A20 council guard: explicit allow-list of receipt provenance. */
private val ALLOWED_SOURCES = setOf("camera_ocr", "mega_connect")
