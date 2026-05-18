package com.dietician.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dietician.shared.ui.components.AuditLogRow
import com.dietician.shared.ui.components.ConsentRow
import com.dietician.shared.ui.data.ConsentScope
import com.dietician.shared.ui.i18n.strings

/**
 * AuditLog screen — Art 12 viewer + Art 15 DSAR + RC7 deep-link + RC10 emotion
 * badge + RC19 separate Art 9 / SCC+DPF consent rows.
 *
 * Layout:
 *   - Top: kind filter chips (llm_call / subject_redact / consent_grant / sign_in /
 *     planned_cut_activated)
 *   - Below: call_uuid filter text field (RC7 deep-link entry)
 *   - Inline consent panel: TWO separate consent rows (Art 9 health + cross-border)
 *   - Body: list of [AuditLogRow] cards
 *   - FAB row at bottom: Export PDF / Export JSON / DSAR ZIP
 *
 * testTags: `audit-log-screen`, `audit-log-filter-kind-{kind}`,
 *   `audit-log-filter-call-uuid-input`, `audit-log-export-pdf`,
 *   `audit-log-export-json`, `audit-log-dsar-zip`, `audit-log-empty`,
 *   `audit-log-export-saved-toast`,
 *   plus child component tags from [AuditLogRow] and [ConsentRow].
 */
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
) {
    val state by viewModel.state.collectAsState()
    val s = strings()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.errorToast) {
        state.errorToast?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.lastExportPath) {
        state.lastExportPath?.let {
            snackbarHost.showSnackbar("${s.audit_log_export_saved} $it")
            viewModel.clearExportPath()
        }
    }

    val kinds = listOf("llm_call", "subject_redact", "consent_grant", "sign_in", "planned_cut_activated")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("audit-log-screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = s.audit_log_title,
            style = MaterialTheme.typography.titleLarge,
        )

        // Kind filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (kind in kinds) {
                FilterChip(
                    selected = state.kindFilter == kind,
                    onClick = {
                        viewModel.setKindFilter(if (state.kindFilter == kind) null else kind)
                    },
                    label = { Text(kind) },
                    modifier = Modifier.testTag("audit-log-filter-kind-$kind"),
                )
            }
        }

        // call_uuid filter (RC7)
        OutlinedTextField(
            value = state.callUuidFilter.orEmpty(),
            onValueChange = { viewModel.setCallUuidFilter(it.ifBlank { null }) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audit-log-filter-call-uuid-input"),
            label = { Text("call_uuid filter") },
            singleLine = true,
        )

        // Inline consent panel — RC19 two separate rows
        ConsentRow(
            testTag = "consent-row-art9-health",
            label = s.consent_art9_health_label,
            row = state.art9Health(),
            onToggle = { granted ->
                viewModel.setConsent(ConsentScope.ART9_HEALTH_DATA, granted)
            },
        )
        ConsentRow(
            testTag = "consent-row-cross-border-transfer",
            label = s.consent_cross_border_label,
            row = state.crossBorder(),
            onToggle = { granted ->
                viewModel.setConsent(ConsentScope.CROSS_BORDER_TRANSFER, granted)
            },
        )

        // Audit list
        if (state.loaded && state.rows.isEmpty()) {
            Text(
                text = s.audit_log_empty,
                modifier = Modifier.testTag("audit-log-empty"),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .testTag("audit-log-list"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.rows) { index, row ->
                    AuditLogRow(row = row, index = index)
                }
            }
        }

        // Export FAB row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = viewModel::exportPdf,
                modifier = Modifier.testTag("audit-log-export-pdf"),
            ) { Text(s.audit_log_export_pdf) }
            TextButton(
                onClick = viewModel::exportJson,
                modifier = Modifier.testTag("audit-log-export-json"),
            ) { Text(s.audit_log_export_json) }
            TextButton(
                onClick = viewModel::exportDsarZip,
                modifier = Modifier.testTag("audit-log-dsar-zip"),
            ) { Text(s.audit_log_export_dsar) }
        }

        SnackbarHost(hostState = snackbarHost) { data ->
            Snackbar(modifier = Modifier.testTag("audit-log-export-saved-toast")) {
                Text(data.visuals.message)
            }
        }
    }
}
