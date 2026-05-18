package com.dietician.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Thin CameraX wrapper that snaps a single JPEG via `ImageCapture.takePicture(...)`
 * and returns the bytes synchronously to the caller.
 *
 * Lifecycle binding (camera-lifecycle's `ProcessCameraProvider`) requires a
 * `LifecycleOwner` — we accept one as a param rather than smuggling the
 * `MainActivity` reference into shared code. Callers from MainActivity pass
 * `this@MainActivity`.
 *
 * Permission gate: callers MUST check + request `Manifest.permission.CAMERA`
 * before invoking [capture]; this class throws [SecurityException] if the
 * permission has not been granted. The Activity is responsible for
 * `ActivityResultContracts.RequestPermission` plumbing.
 *
 * No preview surface is used — this is a "snap + return bytes" pipe for the
 * ReceiptUpload + FoodLog photo-capture screens. A full preview-then-capture
 * flow will land later when the food-log photo-prompt UX needs framing.
 */
class CameraXCapture(
    private val context: Context,
) {

    /**
     * Returns the bytes of the captured image, or null if the camera failed to
     * bind / take-picture errored / shutdown happened mid-flight.
     */
    suspend fun capture(): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("CAMERA permission not granted — caller must request it first")
        }

        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val executor: Executor = ContextCompat.getMainExecutor(context)
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        return try {
            // No-preview "headless" bind. CameraX requires a LifecycleOwner; in
            // production this class is used from MainActivity (Activity IS a
            // LifecycleOwner). For pure-test paths the actual returns null
            // because the Android lifecycle is unavailable.
            val lifecycleOwner = (context as? androidx.lifecycle.LifecycleOwner)
                ?: return null
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                imageCapture,
            )
            suspendCancellableCoroutine<ByteArray?> { cont ->
                imageCapture.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bytes = image.toJpegBytes()
                            image.close()
                            if (cont.isActive) cont.resume(bytes)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            }
        } catch (t: Throwable) {
            null
        } finally {
            try {
                cameraProvider.unbindAll()
            } catch (_: Throwable) {
                // Best-effort cleanup; CameraX may already be shut down.
            }
        }
    }

    private fun ImageProxy.toJpegBytes(): ByteArray {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (imageInfo.rotationDegrees == 0) return bytes
        // Rotate JPEG to upright orientation. Bitmap decode + matrix + re-encode.
        return try {
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            ByteArrayOutputStream().use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            }
        } catch (_: Throwable) {
            bytes
        }
    }
}
