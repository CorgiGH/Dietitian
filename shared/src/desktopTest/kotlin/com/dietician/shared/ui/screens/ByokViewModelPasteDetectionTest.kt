@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.screens

import com.dietician.shared.llm.InMemoryAuditLogSink
import com.dietician.shared.ui.data.ByokRepository
import com.dietician.shared.ui.data.ByokSaveOutcome
import com.dietician.shared.ui.data.DieticianClipboardManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop-only paste detection test for [ByokViewModel].
 *
 * Lives in desktopTest (not commonTest) because the AWT clipboard API is
 * JVM-only. The assertion path:
 *   1. Seed system clipboard with a long secret-looking string
 *   2. Drive ByokViewModel.onKeyValueChange with a single 20+ char value
 *   3. Assert the viewmodel saw it as a paste, cleared the clipboard, and
 *      emitted `subject_credential_paste_detected` audit (RC13).
 *
 * Skips when running headless (CI without DISPLAY).
 */
class ByokViewModelPasteDetectionTest {

    private class FakeRepo : ByokRepository {
        override suspend fun save(provider: String, key: String): ByokSaveOutcome = ByokSaveOutcome.Ok
        override suspend fun delete(provider: String): ByokSaveOutcome = ByokSaveOutcome.Ok
    }

    private fun headless(): Boolean = try {
        GraphicsEnvironment.isHeadless()
    } catch (_: Throwable) {
        true
    }

    private fun setClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            StringSelection(text),
            null,
        )
    }

    @Test
    fun `RC13 large paste detected → clipboard cleared + audit emitted`() = runTest {
        if (headless()) return@runTest
        val sink = InMemoryAuditLogSink()
        val clipboard = DieticianClipboardManager()
        val secret = "sk-ant-test-1234567890ABCDEFGH"
        setClipboard(secret)
        val vm = ByokViewModel(
            repo = FakeRepo(),
            auditSink = sink,
            clipboard = clipboard,
            coroutineScope = this,
        )
        vm.selectProvider(ByokProviders.OPENROUTER)
        vm.onKeyValueChange(secret)
        advanceUntilIdle()
        assertTrue(vm.state.value.pasteToastVisible)
        val rows = sink.snapshot()
        assertEquals(1, rows.size)
        assertEquals("subject_credential_paste_detected", rows.first().kind)
        // Clipboard should be cleared (empty string contents).
        val now = clipboard.get()
        assertTrue(now.isNullOrEmpty(), "expected empty clipboard, got '$now'")
    }
}
