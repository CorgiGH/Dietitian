package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.AuditListOutcome
import com.dietician.shared.ui.data.AuditRepository
import com.dietician.shared.ui.data.AuditRow
import com.dietician.shared.ui.data.ConsentListOutcome
import com.dietician.shared.ui.data.ConsentOutcome
import com.dietician.shared.ui.data.ConsentRow
import com.dietician.shared.ui.data.ConsentScope
import com.dietician.shared.ui.data.ExportOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AuditLog screen state holder.
 *
 * Wires:
 *   - `GET /me/audit?format=json` (filter by [AuditLogState.callUuidFilter] + kind)
 *   - `GET /me/audit?format=pdf` → save via expect/actual [saveExportedFile]
 *   - `GET /me/dsar` → save via expect/actual [saveExportedFile]
 *   - `POST /me/consent` → grant/withdraw with timestamp
 *
 * RC7: [setCallUuidFilter] applied client-side; deep-link from CoachChat sets
 * it via [initialCallUuidFilter] constructor param.
 * RC19: consent state holds TWO records (Art 9 + cross-border SCC/DPF) — no
 * unified "agree to all" toggle.
 */
class AuditLogViewModel(
    private val repo: AuditRepository,
    private val saveFile: suspend (String, String, ByteArray) -> String?,
    initialCallUuidFilter: String? = null,
    private val nowMs: () -> Long = { 0L },
    private val coroutineScope: CoroutineScope = MainScope(),
) {
    private val _state = MutableStateFlow(
        AuditLogState(callUuidFilter = initialCallUuidFilter),
    )
    val state: StateFlow<AuditLogState> = _state.asStateFlow()

    /** Trigger a refresh of audit rows (applies current callUuid/kind filter). */
    fun refresh() {
        coroutineScope.launch {
            when (
                val out = repo.listJson(
                    callUuidFilter = _state.value.callUuidFilter,
                    kindFilter = _state.value.kindFilter,
                )
            ) {
                is AuditListOutcome.Rows -> _state.value = _state.value.copy(
                    rows = out.rows,
                    loaded = true,
                    errorToast = null,
                )
                is AuditListOutcome.Failed -> _state.value = _state.value.copy(
                    loaded = true,
                    errorToast = "Audit load failed: ${out.reason}",
                )
            }
        }
        coroutineScope.launch {
            when (val out = repo.listConsents()) {
                is ConsentListOutcome.Rows -> _state.value = _state.value.copy(
                    consents = out.rows,
                )
                is ConsentListOutcome.Failed -> _state.value = _state.value.copy(
                    errorToast = "Consent load failed: ${out.reason}",
                )
            }
        }
    }

    fun setCallUuidFilter(uuid: String?) {
        _state.value = _state.value.copy(callUuidFilter = uuid)
        refresh()
    }

    fun setKindFilter(kind: String?) {
        _state.value = _state.value.copy(kindFilter = kind)
        refresh()
    }

    fun exportPdf() {
        coroutineScope.launch {
            when (val out = repo.exportPdf()) {
                is ExportOutcome.Bytes -> {
                    val path = saveFile("audit-${nowMs()}.pdf", out.mime, out.bytes)
                    _state.value = _state.value.copy(lastExportPath = path, errorToast = null)
                }
                is ExportOutcome.Failed -> _state.value = _state.value.copy(
                    errorToast = "PDF export failed: ${out.reason}",
                )
            }
        }
    }

    fun exportJson() {
        coroutineScope.launch {
            // JSON path reuses the list response → serialize via the current rows.
            // For first-ship we re-fetch + save the raw bytes via a small inline-built JSON.
            // The simplest path: call listJson + write a JSON document.
            when (
                val out = repo.listJson(
                    callUuidFilter = _state.value.callUuidFilter,
                    kindFilter = _state.value.kindFilter,
                )
            ) {
                is AuditListOutcome.Rows -> {
                    val json = buildJsonString(out.rows)
                    val path = saveFile(
                        "audit-${nowMs()}.json",
                        "application/json",
                        json.encodeToByteArray(),
                    )
                    _state.value = _state.value.copy(lastExportPath = path, errorToast = null)
                }
                is AuditListOutcome.Failed -> _state.value = _state.value.copy(
                    errorToast = "JSON export failed: ${out.reason}",
                )
            }
        }
    }

    fun exportDsarZip() {
        coroutineScope.launch {
            when (val out = repo.exportDsarZip()) {
                is ExportOutcome.Bytes -> {
                    val path = saveFile("dsar-${nowMs()}.zip", out.mime, out.bytes)
                    _state.value = _state.value.copy(lastExportPath = path, errorToast = null)
                }
                is ExportOutcome.Failed -> _state.value = _state.value.copy(
                    errorToast = "DSAR export failed: ${out.reason}",
                )
            }
        }
    }

    fun setConsent(scope: String, granted: Boolean) {
        coroutineScope.launch {
            when (val out = repo.updateConsent(scope, granted)) {
                ConsentOutcome.Ok -> refresh()
                is ConsentOutcome.Failed -> _state.value = _state.value.copy(
                    errorToast = "Consent update failed: ${out.reason}",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorToast = null)
    }

    fun clearExportPath() {
        _state.value = _state.value.copy(lastExportPath = null)
    }

    /** Stable JSON serializer for the local rows snapshot (no kotlinx.serialization
     *  round-trip needed — we already have the structured rows in memory and want
     *  byte-identical output for the user-saved file). */
    private fun buildJsonString(rows: List<AuditRow>): String = buildString {
        append("[")
        for ((i, r) in rows.withIndex()) {
            if (i > 0) append(",")
            append("{")
            append("\"id\":\"").append(r.id).append("\",")
            append("\"occurredAtMs\":").append(r.occurredAtMs).append(",")
            append("\"kind\":\"").append(r.kind).append("\"")
            r.model?.let { append(",\"model\":\"").append(it).append("\"") }
            r.costCents?.let { append(",\"costCents\":").append(it) }
            r.callUuid?.let { append(",\"callUuid\":\"").append(it).append("\"") }
            r.summary?.let { append(",\"summary\":\"").append(it.replace("\"", "\\\"")).append("\"") }
            if (r.extra.isNotEmpty()) {
                append(",\"extra\":{")
                var first = true
                for ((k, v) in r.extra) {
                    if (!first) append(",")
                    append("\"").append(k).append("\":\"").append(v).append("\"")
                    first = false
                }
                append("}")
            }
            append("}")
        }
        append("]")
    }
}

data class AuditLogState(
    val rows: List<AuditRow> = emptyList(),
    val consents: List<ConsentRow> = emptyList(),
    val callUuidFilter: String? = null,
    val kindFilter: String? = null,
    val loaded: Boolean = false,
    val lastExportPath: String? = null,
    val errorToast: String? = null,
) {
    /** Convenience: Art 9 health-data consent row if known. */
    fun art9Health(): ConsentRow? = consents.firstOrNull { it.scope == ConsentScope.ART9_HEALTH_DATA }

    /** Convenience: cross-border SCC/DPF consent row if known. */
    fun crossBorder(): ConsentRow? = consents.firstOrNull { it.scope == ConsentScope.CROSS_BORDER_TRANSFER }
}
