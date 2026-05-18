package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.ReceiptUploadRepository
import com.dietician.shared.ui.data.UploadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ReceiptUploadScreen state holder.
 *
 * Flow:
 *   1. User taps "Take photo" / "Pick file" → platform expect/actual `captureImage()`
 *   2. Returned bytes → [onImageCaptured] → preview rendered
 *   3. User confirms → [upload] POSTs to Plan-3 /receipts/upload (multipart)
 *   4. 202 → uploadedReceiptId surfaces success card with "View in Pantry" deep-link
 *
 * Source code: hardcoded to "camera_ocr" for Batch C; Mega CONNECT path is
 * Plan-6.
 */
class ReceiptUploadViewModel(
    private val repo: ReceiptUploadRepository,
    private val source: String = "camera_ocr",
    private val coroutineScope: CoroutineScope = MainScope(),
) {
    private val _state = MutableStateFlow(ReceiptUploadState())
    val state: StateFlow<ReceiptUploadState> = _state.asStateFlow()

    fun onImageCaptured(bytes: ByteArray) {
        _state.value = _state.value.copy(previewBytes = bytes)
    }

    fun retake() {
        _state.value = _state.value.copy(previewBytes = null)
    }

    fun upload() {
        val bytes = _state.value.previewBytes ?: return
        _state.value = _state.value.copy(uploading = true, errorToast = null)
        coroutineScope.launch {
            when (val result = repo.upload(bytes, source)) {
                is UploadResult.Accepted -> _state.value = _state.value.copy(
                    uploading = false,
                    uploadedReceiptId = result.receiptId,
                    previewBytes = null,
                )
                is UploadResult.Failed -> _state.value = _state.value.copy(
                    uploading = false,
                    errorToast = "Upload failed: ${result.reason}",
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorToast = null)
    }

    fun clearSuccess() {
        _state.value = _state.value.copy(uploadedReceiptId = null)
    }
}

data class ReceiptUploadState(
    val previewBytes: ByteArray? = null,
    val uploading: Boolean = false,
    val uploadedReceiptId: String? = null,
    val errorToast: String? = null,
) {
    @Suppress("RedundantOverride")
    override fun equals(other: Any?): Boolean = super.equals(other)

    @Suppress("RedundantOverride")
    override fun hashCode(): Int = super.hashCode()
}
