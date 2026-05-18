package com.dietician.server.export

import com.dietician.server.repo.AuditRepository
import com.dietician.server.repo.ConsentRepository
import com.dietician.server.repo.CredentialRepository
import com.dietician.server.repo.EventRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Article 15 DSAR (Data Subject Access Request) export builder.
 *
 * Produces a single ZIP byte-array containing:
 *   - `manifest.json`              — top-level metadata
 *   - `events_<table>.jsonl`       — one JSON line per event row, per table
 *   - `consents.jsonl`             — V016 consents history
 *   - `credentials_metadata.jsonl` — V019 byok rows (metadata only; key NOT
 *                                    re-encrypted-out for DSAR — Art 15
 *                                    allows withholding security secrets)
 *   - `audit_log.jsonl`            — last 90 days of audit rows
 *
 * For the friends-tier single-user cohort the total byte count fits in
 * memory comfortably (kilobytes to low MB). When the cohort grows, switch
 * to `respondBytesWriter` streaming + temp-file-backed ZipOutputStream.
 */
object DsarZipBuilder {
    fun build(
        subjectId: UUID,
        eventRepo: EventRepository,
        consentRepo: ConsentRepository,
        credentialRepo: CredentialRepository,
        auditRepo: AuditRepository,
    ): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { zos ->
            // ----- manifest.json -----
            val manifest = JsonObject(
                mapOf(
                    "subject_id" to JsonPrimitive(subjectId.toString()),
                    "generated_at" to JsonPrimitive(Instant.now().toString()),
                    "spec" to JsonPrimitive("Dietician DSAR v1 (GDPR Art 15)"),
                    "schema_version" to JsonPrimitive("plan-3"),
                    "files" to JsonPrimitive(
                        "manifest.json, events_*.jsonl, consents.jsonl, credentials_metadata.jsonl, audit_log.jsonl",
                    ),
                ),
            )
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(Json.encodeToString(JsonObject.serializer(), manifest).toByteArray())
            zos.closeEntry()

            // ----- events_*.jsonl per table -----
            for (table in EventRepository.TABLES) {
                val rows = eventRepo.listSince(subjectId, table, 0L, null, limit = 10_000)
                if (rows.isEmpty()) continue
                zos.putNextEntry(ZipEntry("events_$table.jsonl"))
                for (r in rows) {
                    // payloadJson is already a JSON object string; emit verbatim.
                    zos.write(r.payloadJson.toByteArray())
                    zos.write("\n".toByteArray())
                }
                zos.closeEntry()
            }

            // ----- consents.jsonl -----
            val consents = consentRepo.listForSubject(subjectId)
            zos.putNextEntry(ZipEntry("consents.jsonl"))
            for (c in consents) {
                val line = JsonObject(
                    mapOf(
                        "consent_id" to JsonPrimitive(c.consentId.toString()),
                        "scope" to JsonPrimitive(c.scope),
                        "granted_at" to JsonPrimitive(c.grantedAt.toString()),
                        "withdrawn_at" to JsonPrimitive(c.withdrawnAt?.toString() ?: ""),
                        "version_hash" to JsonPrimitive(c.versionHash),
                    ),
                )
                zos.write(Json.encodeToString(JsonObject.serializer(), line).toByteArray())
                zos.write("\n".toByteArray())
            }
            zos.closeEntry()

            // ----- credentials_metadata.jsonl (NO plaintext keys) -----
            val creds = credentialRepo.listForSubject(subjectId)
            zos.putNextEntry(ZipEntry("credentials_metadata.jsonl"))
            for (c in creds) {
                val line = JsonObject(
                    mapOf(
                        "provider" to JsonPrimitive(c.provider),
                        "created_at" to JsonPrimitive(c.createdAt.toString()),
                        "revoked_at" to JsonPrimitive(c.revokedAt?.toString() ?: ""),
                    ),
                )
                zos.write(Json.encodeToString(JsonObject.serializer(), line).toByteArray())
                zos.write("\n".toByteArray())
            }
            zos.closeEntry()

            // ----- audit_log.jsonl (last 90 days) -----
            val from = Instant.now().minus(90, ChronoUnit.DAYS)
            val to = Instant.now()
            val audit = auditRepo.list(subjectId, from, to)
            zos.putNextEntry(ZipEntry("audit_log.jsonl"))
            for (a in audit) {
                val line = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(a.id.toString()),
                        "occurred_at" to JsonPrimitive(a.occurredAt.toInstant().toString()),
                        "kind" to JsonPrimitive(a.kind),
                        "model" to JsonPrimitive(a.model ?: ""),
                        "cost_cents" to JsonPrimitive(a.costCents ?: -1),
                        "request_id" to JsonPrimitive(a.requestId ?: ""),
                        "extra" to JsonPrimitive(a.extraJson ?: ""),
                    ),
                )
                zos.write(Json.encodeToString(JsonObject.serializer(), line).toByteArray())
                zos.write("\n".toByteArray())
            }
            zos.closeEntry()
        }
        return bo.toByteArray()
    }

    /**
     * Filename helper: `dsar-<subjectShort>-<yyyy-mm-dd>.zip` — same
     * shape as `audit-` for client-side download UX consistency.
     */
    fun filename(subjectId: UUID): String =
        "dsar-${subjectId.toString().take(8)}-${LocalDate.now(ZoneOffset.UTC)}.zip"
}
