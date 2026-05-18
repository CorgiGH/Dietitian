package com.dietician.shared.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * UI-layer audit + consent + DSAR repository.
 *
 * Wraps Plan-3 endpoints:
 *   - GET /me/audit?format=json   → list of [AuditRow]
 *   - GET /me/audit?format=pdf    → ByteArray (PDF)
 *   - GET /me/dsar                → ByteArray (ZIP per Art 15)
 *   - POST /me/consent            → grant/withdraw
 *
 * RC7: call_uuid filter is applied CLIENT-SIDE on the JSON list (server-side
 * `?filter=<uuid>` not guaranteed in Plan-3 first-ship; friends-only scale tolerates
 * full-list-then-filter).
 *
 * RC10: [AuditRow.extra] contains `emotion_inference_disabled=true` for the
 * AI Act Art 5(1)(f) hard-banned compliance evidence; the UI surfaces a badge.
 *
 * RC19: consent scopes are TWO separate records:
 *   - `process_health_data` (Art 9 health-data basis)
 *   - `cross_border_transfer` (SCC + DPF for Anthropic/Google/OpenRouter US)
 */
interface AuditRepository {
    suspend fun listJson(callUuidFilter: String? = null, kindFilter: String? = null): AuditListOutcome
    suspend fun exportPdf(): ExportOutcome
    suspend fun exportDsarZip(): ExportOutcome
    suspend fun updateConsent(scope: String, granted: Boolean): ConsentOutcome
    suspend fun listConsents(): ConsentListOutcome
}

/** Single audit row mirrored from V018 server schema. */
@Serializable
data class AuditRow(
    val id: String,
    val occurredAtMs: Long,
    val kind: String,
    val model: String? = null,
    val costCents: Int? = null,
    val callUuid: String? = null,
    val summary: String? = null,
    val extra: Map<String, String> = emptyMap(),
) {
    /** RC10 — true when audit row carries emotion-inference-disabled evidence. */
    val emotionInferenceDisabled: Boolean
        get() = extra["emotion_inference_disabled"]?.lowercase() == "true"
}

@Serializable
data class ConsentRow(
    val scope: String,
    val granted: Boolean,
    val grantedAtMs: Long? = null,
    val withdrawnAtMs: Long? = null,
    val versionHash: String? = null,
)

sealed interface AuditListOutcome {
    data class Rows(val rows: List<AuditRow>) : AuditListOutcome
    data class Failed(val reason: String) : AuditListOutcome
}

sealed interface ConsentListOutcome {
    data class Rows(val rows: List<ConsentRow>) : ConsentListOutcome
    data class Failed(val reason: String) : ConsentListOutcome
}

sealed interface ExportOutcome {
    data class Bytes(val bytes: ByteArray, val mime: String) : ExportOutcome {
        @Suppress("RedundantOverride")
        override fun equals(other: Any?): Boolean = super.equals(other)

        @Suppress("RedundantOverride")
        override fun hashCode(): Int = super.hashCode()
    }
    data class Failed(val reason: String) : ExportOutcome
}

sealed interface ConsentOutcome {
    data object Ok : ConsentOutcome
    data class Failed(val reason: String) : ConsentOutcome
}

/** Known RC19 consent scope names. */
object ConsentScope {
    const val ART9_HEALTH_DATA: String = "process_health_data"
    const val CROSS_BORDER_TRANSFER: String = "cross_border_transfer"
}

/** Ktor-backed default impl. Koin-wired in Batch E. */
class HttpAuditRepository(
    private val http: HttpClient,
    private val baseUrl: String,
) : AuditRepository {

    @Serializable
    private data class AuditListResponse(val rows: List<AuditRow>)

    @Serializable
    private data class ConsentListResponse(val rows: List<ConsentRow>)

    @Serializable
    private data class ConsentRequest(val scope: String, val granted: Boolean)

    override suspend fun listJson(
        callUuidFilter: String?,
        kindFilter: String?,
    ): AuditListOutcome = try {
        val response = http.get { url("$baseUrl/me/audit?format=json") }
        val parsed = response.body<AuditListResponse>()
        val filtered = parsed.rows.filter { row ->
            (callUuidFilter == null || row.callUuid == callUuidFilter) &&
                (kindFilter == null || row.kind == kindFilter)
        }
        AuditListOutcome.Rows(filtered)
    } catch (e: ResponseException) {
        AuditListOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        AuditListOutcome.Failed(t.message ?: "network error")
    }

    override suspend fun exportPdf(): ExportOutcome = try {
        val response = http.get { url("$baseUrl/me/audit?format=pdf") }
        if (response.status == HttpStatusCode.OK) {
            ExportOutcome.Bytes(response.bodyAsBytes(), "application/pdf")
        } else {
            ExportOutcome.Failed("server ${response.status.value}")
        }
    } catch (e: ResponseException) {
        ExportOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        ExportOutcome.Failed(t.message ?: "network error")
    }

    override suspend fun exportDsarZip(): ExportOutcome = try {
        val response = http.get { url("$baseUrl/me/dsar") }
        if (response.status == HttpStatusCode.OK) {
            ExportOutcome.Bytes(response.bodyAsBytes(), "application/zip")
        } else {
            ExportOutcome.Failed("server ${response.status.value}")
        }
    } catch (e: ResponseException) {
        ExportOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        ExportOutcome.Failed(t.message ?: "network error")
    }

    override suspend fun updateConsent(scope: String, granted: Boolean): ConsentOutcome = try {
        val response = http.post {
            url("$baseUrl/me/consent")
            contentType(ContentType.Application.Json)
            setBody(ConsentRequest(scope = scope, granted = granted))
        }
        if (response.status.value in 200..299) ConsentOutcome.Ok
        else ConsentOutcome.Failed("server ${response.status.value}")
    } catch (e: ResponseException) {
        ConsentOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        ConsentOutcome.Failed(t.message ?: "network error")
    }

    override suspend fun listConsents(): ConsentListOutcome = try {
        val response = http.get { url("$baseUrl/me/consent") }
        val parsed = response.body<ConsentListResponse>()
        ConsentListOutcome.Rows(parsed.rows)
    } catch (e: ResponseException) {
        ConsentListOutcome.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        ConsentListOutcome.Failed(t.message ?: "network error")
    }
}

/**
 * Platform expect for "save downloaded bytes somewhere the user can find".
 * First-ship desktop returns the destination File path; Android stubs to MediaStore
 * (Batch E Task 23/25 finalize). Returns null if the user cancels.
 */
expect suspend fun saveExportedFile(name: String, mime: String, bytes: ByteArray): String?
