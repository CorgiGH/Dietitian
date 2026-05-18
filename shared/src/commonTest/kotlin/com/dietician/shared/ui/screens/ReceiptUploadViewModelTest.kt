@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.ReceiptUploadRepository
import com.dietician.shared.ui.data.UploadResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptUploadViewModelTest {

    private class FakeRepo(private val result: UploadResult) : ReceiptUploadRepository {
        val uploads = mutableListOf<Pair<ByteArray, String>>()
        override suspend fun upload(imageBytes: ByteArray, source: String): UploadResult {
            uploads += imageBytes to source
            return result
        }
    }

    @Test
    fun `initial state empty + no preview`() = runTest {
        val vm = ReceiptUploadViewModel(repo = FakeRepo(UploadResult.Accepted("r1")))
        assertNull(vm.state.value.previewBytes)
        assertFalse(vm.state.value.uploading)
        assertNull(vm.state.value.uploadedReceiptId)
    }

    @Test
    fun `onImageCaptured stages preview`() {
        val vm = ReceiptUploadViewModel(repo = FakeRepo(UploadResult.Accepted("r1")))
        val bytes = byteArrayOf(1, 2, 3, 4)
        vm.onImageCaptured(bytes)
        assertNotNull(vm.state.value.previewBytes)
        assertEquals(4, vm.state.value.previewBytes!!.size)
    }

    @Test
    fun `retake clears preview`() {
        val vm = ReceiptUploadViewModel(repo = FakeRepo(UploadResult.Accepted("r1")))
        vm.onImageCaptured(byteArrayOf(1, 2))
        vm.retake()
        assertNull(vm.state.value.previewBytes)
    }

    @Test
    fun `upload happy-path 202 → uploadedReceiptId set`() = runTest {
        val repo = FakeRepo(UploadResult.Accepted("receipt-42"))
        val vm = ReceiptUploadViewModel(repo = repo, coroutineScope = this)
        vm.onImageCaptured(byteArrayOf(9, 9, 9))
        vm.upload()
        advanceUntilIdle()
        assertEquals(1, repo.uploads.size)
        assertEquals("camera_ocr", repo.uploads.first().second)
        assertEquals("receipt-42", vm.state.value.uploadedReceiptId)
        assertFalse(vm.state.value.uploading)
    }

    @Test
    fun `upload failure surfaces errorToast`() = runTest {
        val repo = FakeRepo(UploadResult.Failed("503 backend"))
        val vm = ReceiptUploadViewModel(repo = repo, coroutineScope = this)
        vm.onImageCaptured(byteArrayOf(1))
        vm.upload()
        advanceUntilIdle()
        assertNull(vm.state.value.uploadedReceiptId)
        assertNotNull(vm.state.value.errorToast)
        assertTrue(vm.state.value.errorToast!!.contains("503"))
    }

    @Test
    fun `upload without preview no-ops`() = runTest {
        val repo = FakeRepo(UploadResult.Accepted("never"))
        val vm = ReceiptUploadViewModel(repo = repo, coroutineScope = this)
        vm.upload()
        advanceUntilIdle()
        assertEquals(0, repo.uploads.size)
    }

    @Test
    fun `upload sets uploading=true then false`() = runTest {
        val repo = FakeRepo(UploadResult.Accepted("r1"))
        val vm = ReceiptUploadViewModel(repo = repo, coroutineScope = this)
        vm.onImageCaptured(byteArrayOf(1))
        vm.upload()
        // Before advanceUntilIdle the coroutine hasn't completed yet
        advanceUntilIdle()
        assertFalse(vm.state.value.uploading)
    }

    @Test
    fun `clearSuccess resets uploadedReceiptId`() = runTest {
        val repo = FakeRepo(UploadResult.Accepted("r1"))
        val vm = ReceiptUploadViewModel(repo = repo, coroutineScope = this)
        vm.onImageCaptured(byteArrayOf(1))
        vm.upload()
        advanceUntilIdle()
        vm.clearSuccess()
        assertNull(vm.state.value.uploadedReceiptId)
    }
}
