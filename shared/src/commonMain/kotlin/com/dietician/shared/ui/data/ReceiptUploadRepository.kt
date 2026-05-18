package com.dietician.shared.ui.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * Multipart upload to Plan-3 `POST /receipts/upload` (Task 24).
 *
 * Source codes:
 *   - "camera_ocr" (default) — phone camera or desktop file picker
 *   - "mega_connect" — future Mega CONNECT API path (Plan-6)
 *
 * Returns:
 *   - 202 + {receipt_id} → [UploadResult.Accepted]
 *   - Network / 4xx / 5xx → [UploadResult.Failed]
 *
 * The actual capture pathway (camera / SAF / FileChooser) is platform-specific
 * and lands via [captureImage] expect/actual in Batch E (Task 23 / Task 26).
 * Batch C ships the upload pipe + repo + screen wire; captureImage stub returns
 * null until then.
 */
interface ReceiptUploadRepository {
    suspend fun upload(imageBytes: ByteArray, source: String = "camera_ocr"): UploadResult
}

sealed interface UploadResult {
    data class Accepted(val receiptId: String) : UploadResult
    data class Failed(val reason: String) : UploadResult
}

/**
 * Ktor multipart upload. Field names per Plan-3 Task 24 contract:
 *   - `image` (file) — the receipt JPEG/PNG bytes
 *   - `source` (text) — provenance tag (camera_ocr / mega_connect)
 */
class HttpReceiptUploadRepository(
    private val http: HttpClient,
    private val baseUrl: String,
) : ReceiptUploadRepository {

    @Serializable
    private data class UploadResponse(val receiptId: String)

    override suspend fun upload(imageBytes: ByteArray, source: String): UploadResult = try {
        val response = http.post {
            url("$baseUrl/receipts/upload")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "image",
                            value = imageBytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"receipt.jpg\"",
                                )
                            },
                        )
                        append("source", source)
                    },
                ),
            )
        }
        when (response.status) {
            HttpStatusCode.Accepted, HttpStatusCode.OK -> {
                val body = response.body<UploadResponse>()
                UploadResult.Accepted(body.receiptId)
            }
            else -> UploadResult.Failed("server ${response.status.value}")
        }
    } catch (e: ResponseException) {
        UploadResult.Failed("server ${e.response.status.value}")
    } catch (t: Throwable) {
        UploadResult.Failed(t.message ?: "network error")
    }
}

/**
 * Platform-specific image capture. Android = camera / SAF (Batch E Task 23);
 * Desktop = FileChooser (Batch E Task 26). First-ship actuals return null —
 * Batch C ReceiptUploadScreen tolerates null + shows "Capture failed" toast.
 */
expect fun captureImage(): ByteArray?
