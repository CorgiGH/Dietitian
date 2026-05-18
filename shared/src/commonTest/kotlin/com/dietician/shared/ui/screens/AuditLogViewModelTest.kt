@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.AuditListOutcome
import com.dietician.shared.ui.data.AuditRepository
import com.dietician.shared.ui.data.AuditRow
import com.dietician.shared.ui.data.ConsentListOutcome
import com.dietician.shared.ui.data.ConsentOutcome
import com.dietician.shared.ui.data.ConsentRow
import com.dietician.shared.ui.data.ConsentScope
import com.dietician.shared.ui.data.ExportOutcome
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuditLogViewModelTest {

    private class FakeRepo(
        var audit: AuditListOutcome = AuditListOutcome.Rows(emptyList()),
        var consents: ConsentListOutcome = ConsentListOutcome.Rows(emptyList()),
        var pdf: ExportOutcome = ExportOutcome.Bytes(byteArrayOf(1, 2, 3), "application/pdf"),
        var dsar: ExportOutcome = ExportOutcome.Bytes(byteArrayOf(4, 5), "application/zip"),
        var consentUpdate: ConsentOutcome = ConsentOutcome.Ok,
    ) : AuditRepository {
        val listJsonCalls = mutableListOf<Pair<String?, String?>>()
        val consentCalls = mutableListOf<Pair<String, Boolean>>()
        override suspend fun listJson(callUuidFilter: String?, kindFilter: String?): AuditListOutcome {
            listJsonCalls += callUuidFilter to kindFilter
            // Apply client filter manually for the fake when given Rows()
            return when (val a = audit) {
                is AuditListOutcome.Rows -> AuditListOutcome.Rows(
                    a.rows.filter { row ->
                        (callUuidFilter == null || row.callUuid == callUuidFilter) &&
                            (kindFilter == null || row.kind == kindFilter)
                    },
                )
                is AuditListOutcome.Failed -> a
            }
        }
        override suspend fun exportPdf(): ExportOutcome = pdf
        override suspend fun exportDsarZip(): ExportOutcome = dsar
        override suspend fun updateConsent(scope: String, granted: Boolean): ConsentOutcome {
            consentCalls += scope to granted
            return consentUpdate
        }
        override suspend fun listConsents(): ConsentListOutcome = consents
    }

    private class SaveCapture {
        val saves = mutableListOf<Triple<String, String, ByteArray>>()
        suspend fun save(name: String, mime: String, bytes: ByteArray): String? {
            saves += Triple(name, mime, bytes)
            return "/tmp/$name"
        }
    }

    @Test
    fun `refresh loads rows + consents from repo`() = runTest {
        val rows = listOf(
            AuditRow(id = "1", occurredAtMs = 100L, kind = "llm_call", model = "claude"),
            AuditRow(id = "2", occurredAtMs = 200L, kind = "consent_grant"),
        )
        val consents = listOf(
            ConsentRow(scope = ConsentScope.ART9_HEALTH_DATA, granted = true, grantedAtMs = 50L, versionHash = "v1"),
        )
        val repo = FakeRepo(
            audit = AuditListOutcome.Rows(rows),
            consents = ConsentListOutcome.Rows(consents),
        )
        val save = SaveCapture()
        val vm = AuditLogViewModel(
            repo = repo,
            saveFile = save::save,
            coroutineScope = this,
        )
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.rows.size)
        assertEquals(1, vm.state.value.consents.size)
        assertNotNull(vm.state.value.art9Health())
        assertNull(vm.state.value.crossBorder())
    }

    @Test
    fun `RC7 callUuid filter applied client-side`() = runTest {
        val rows = listOf(
            AuditRow(id = "1", occurredAtMs = 100L, kind = "llm_call", callUuid = "uuid-A"),
            AuditRow(id = "2", occurredAtMs = 200L, kind = "llm_call", callUuid = "uuid-B"),
            AuditRow(id = "3", occurredAtMs = 300L, kind = "llm_call", callUuid = "uuid-A"),
        )
        val repo = FakeRepo(audit = AuditListOutcome.Rows(rows))
        val save = SaveCapture()
        val vm = AuditLogViewModel(
            repo = repo,
            saveFile = save::save,
            initialCallUuidFilter = "uuid-A",
            coroutineScope = this,
        )
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.rows.size)
        assertTrue(vm.state.value.rows.all { it.callUuid == "uuid-A" })
    }

    @Test
    fun `RC10 emotionInferenceDisabled flag surfaces`() {
        val rowFlagTrue = AuditRow(
            id = "x",
            occurredAtMs = 0L,
            kind = "llm_call",
            extra = mapOf("emotion_inference_disabled" to "true"),
        )
        val rowFlagMissing = AuditRow(id = "y", occurredAtMs = 0L, kind = "llm_call")
        assertTrue(rowFlagTrue.emotionInferenceDisabled)
        assertTrue(!rowFlagMissing.emotionInferenceDisabled)
    }

    @Test
    fun `RC19 art9 + cross-border are separate consent records`() = runTest {
        val repo = FakeRepo(
            consents = ConsentListOutcome.Rows(
                listOf(
                    ConsentRow(scope = ConsentScope.ART9_HEALTH_DATA, granted = true, grantedAtMs = 1L),
                    ConsentRow(scope = ConsentScope.CROSS_BORDER_TRANSFER, granted = false, withdrawnAtMs = 2L),
                ),
            ),
        )
        val save = SaveCapture()
        val vm = AuditLogViewModel(repo = repo, saveFile = save::save, coroutineScope = this)
        vm.refresh()
        advanceUntilIdle()
        val art9 = vm.state.value.art9Health()
        val xb = vm.state.value.crossBorder()
        assertNotNull(art9)
        assertNotNull(xb)
        assertTrue(art9.granted)
        assertTrue(!xb.granted)
    }

    @Test
    fun `setConsent calls repo with separate scope`() = runTest {
        val repo = FakeRepo()
        val save = SaveCapture()
        val vm = AuditLogViewModel(repo = repo, saveFile = save::save, coroutineScope = this)
        vm.setConsent(ConsentScope.CROSS_BORDER_TRANSFER, granted = true)
        advanceUntilIdle()
        assertEquals(1, repo.consentCalls.size)
        assertEquals(ConsentScope.CROSS_BORDER_TRANSFER, repo.consentCalls.first().first)
        assertTrue(repo.consentCalls.first().second)
    }

    @Test
    fun `exportPdf saves bytes via saveFile`() = runTest {
        val repo = FakeRepo()
        val save = SaveCapture()
        val vm = AuditLogViewModel(
            repo = repo,
            saveFile = save::save,
            nowMs = { 42L },
            coroutineScope = this,
        )
        vm.exportPdf()
        advanceUntilIdle()
        assertEquals(1, save.saves.size)
        assertEquals("audit-42.pdf", save.saves.first().first)
        assertEquals("application/pdf", save.saves.first().second)
        assertNotNull(vm.state.value.lastExportPath)
    }

    @Test
    fun `exportDsarZip saves bytes via saveFile`() = runTest {
        val repo = FakeRepo()
        val save = SaveCapture()
        val vm = AuditLogViewModel(
            repo = repo,
            saveFile = save::save,
            nowMs = { 7L },
            coroutineScope = this,
        )
        vm.exportDsarZip()
        advanceUntilIdle()
        assertEquals("dsar-7.zip", save.saves.first().first)
        assertEquals("application/zip", save.saves.first().second)
    }

    @Test
    fun `exportJson serializes rows to JSON and saves`() = runTest {
        val rows = listOf(
            AuditRow(id = "row1", occurredAtMs = 100L, kind = "llm_call", model = "claude"),
        )
        val repo = FakeRepo(audit = AuditListOutcome.Rows(rows))
        val save = SaveCapture()
        val vm = AuditLogViewModel(
            repo = repo,
            saveFile = save::save,
            nowMs = { 99L },
            coroutineScope = this,
        )
        vm.exportJson()
        advanceUntilIdle()
        assertEquals("audit-99.json", save.saves.first().first)
        val written = save.saves.first().third.decodeToString()
        assertTrue(written.contains("row1"))
        assertTrue(written.contains("llm_call"))
    }

    @Test
    fun `repo failure surfaces errorToast`() = runTest {
        val repo = FakeRepo(audit = AuditListOutcome.Failed("503"))
        val save = SaveCapture()
        val vm = AuditLogViewModel(repo = repo, saveFile = save::save, coroutineScope = this)
        vm.refresh()
        advanceUntilIdle()
        assertNotNull(vm.state.value.errorToast)
        assertTrue(vm.state.value.errorToast!!.contains("503"))
    }
}
