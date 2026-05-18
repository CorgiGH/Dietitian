@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.screens

import com.dietician.shared.llm.InMemoryAuditLogSink
import com.dietician.shared.ui.data.ByokRepository
import com.dietician.shared.ui.data.ByokSaveOutcome
import com.dietician.shared.ui.data.DieticianClipboardManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ByokViewModelTest {

    private class FakeByokRepo(var outcome: ByokSaveOutcome = ByokSaveOutcome.Ok) : ByokRepository {
        val saves = mutableListOf<Pair<String, String>>()
        val deletes = mutableListOf<String>()
        override suspend fun save(provider: String, key: String): ByokSaveOutcome {
            saves += provider to key
            return outcome
        }
        override suspend fun delete(provider: String): ByokSaveOutcome {
            deletes += provider
            return outcome
        }
    }

    // Common tests rely on the platform actual; on JVM/desktop the clipboard
    // is headless-safe and get() returns null + clear() is a no-op. That means
    // paste-detection negative cases work cross-platform; positive paste-detection
    // assertions live in the desktopTest sibling.
    private fun clipboard(): DieticianClipboardManager = DieticianClipboardManager()

    @Test
    fun `selectProvider updates state`() {
        val sink = InMemoryAuditLogSink()
        val vm = ByokViewModel(
            repo = FakeByokRepo(),
            auditSink = sink,
            clipboard = clipboard(),
        )
        vm.selectProvider(ByokProviders.ANTHROPIC)
        assertEquals(ByokProviders.ANTHROPIC, vm.state.value.provider)
    }

    @Test
    fun `small typing delta does NOT trigger paste detection`() = runTest {
        val sink = InMemoryAuditLogSink()
        val vm = ByokViewModel(
            repo = FakeByokRepo(),
            auditSink = sink,
            clipboard = clipboard(),
            coroutineScope = this,
        )
        vm.onKeyValueChange("s")
        vm.onKeyValueChange("sk")
        vm.onKeyValueChange("sk-")
        advanceUntilIdle()
        assertFalse(vm.state.value.pasteToastVisible)
        assertEquals(0, sink.snapshot().size)
    }

    @Test
    fun `save with blank key no-ops`() = runTest {
        val sink = InMemoryAuditLogSink()
        val repo = FakeByokRepo()
        val vm = ByokViewModel(
            repo = repo,
            auditSink = sink,
            clipboard = clipboard(),
            coroutineScope = this,
        )
        vm.save()
        advanceUntilIdle()
        assertEquals(0, repo.saves.size)
    }

    @Test
    fun `save with provider + key calls repo and clears key on success`() = runTest {
        val sink = InMemoryAuditLogSink()
        val repo = FakeByokRepo(outcome = ByokSaveOutcome.Ok)
        val vm = ByokViewModel(
            repo = repo,
            auditSink = sink,
            clipboard = clipboard(),
            coroutineScope = this,
        )
        vm.selectProvider(ByokProviders.GEMINI)
        // Use small chunks to avoid paste-detection
        for (c in "abcdefg") vm.onKeyValueChange(vm.state.value.keyValue + c)
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repo.saves.size)
        assertEquals(ByokProviders.GEMINI, repo.saves.first().first)
        assertEquals("abcdefg", repo.saves.first().second)
        assertTrue(vm.state.value.saved)
        assertEquals("", vm.state.value.keyValue)
    }

    @Test
    fun `save failure surfaces errorToast`() = runTest {
        val sink = InMemoryAuditLogSink()
        val repo = FakeByokRepo(outcome = ByokSaveOutcome.Failed("500 backend"))
        val vm = ByokViewModel(
            repo = repo,
            auditSink = sink,
            clipboard = clipboard(),
            coroutineScope = this,
        )
        vm.selectProvider(ByokProviders.GROQ)
        for (c in "abcdefg") vm.onKeyValueChange(vm.state.value.keyValue + c)
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.state.value.saved)
        assertNotNull(vm.state.value.errorToast)
        assertTrue(vm.state.value.errorToast!!.contains("500"))
    }

    @Test
    fun `ByokProviders ALL contains expected four`() {
        assertEquals(4, ByokProviders.ALL.size)
        assertTrue(ByokProviders.OPENROUTER in ByokProviders.ALL)
        assertTrue(ByokProviders.ANTHROPIC in ByokProviders.ALL)
        assertTrue(ByokProviders.GEMINI in ByokProviders.ALL)
        assertTrue(ByokProviders.GROQ in ByokProviders.ALL)
    }

    @Test
    fun `dismissPasteToast clears the flag idempotently`() {
        val sink = InMemoryAuditLogSink()
        val vm = ByokViewModel(
            repo = FakeByokRepo(),
            auditSink = sink,
            clipboard = clipboard(),
        )
        vm.dismissPasteToast()
        assertFalse(vm.state.value.pasteToastVisible)
    }

    @Test
    fun `clearError + clearSaved reset transient state`() = runTest {
        val sink = InMemoryAuditLogSink()
        val repo = FakeByokRepo(outcome = ByokSaveOutcome.Ok)
        val vm = ByokViewModel(
            repo = repo,
            auditSink = sink,
            clipboard = clipboard(),
            coroutineScope = this,
        )
        vm.selectProvider(ByokProviders.OPENROUTER)
        for (c in "abcdefg") vm.onKeyValueChange(vm.state.value.keyValue + c)
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        vm.clearSaved()
        assertFalse(vm.state.value.saved)
    }
}
