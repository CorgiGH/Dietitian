package com.dietician.shared.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Subscribes to ntfy SSE; on any message → invoke onTrigger().
 *
 * @param ntfyUrl SSE endpoint, e.g. "http://100.101.47.77:8082/dietician-v-android-XXX/sse"
 */
class NtfyClient(
    private val ntfyUrl: String,
    private val onTrigger: () -> Unit,
) {
    // readTimeout(0) → long-poll SSE without OkHttp closing the stream.
    private val client =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job =
            scope.launch {
                while (isActive) {
                    // Council #3: runCatching swallows CancellationException -> the listener
                    // becomes unkillable from its parent scope. Explicit try/catch with
                    // re-throw on CancellationException.
                    try {
                        val req = Request.Builder().url(ntfyUrl).build()
                        client.newCall(req).execute().use { resp ->
                            val src = resp.body!!.source()
                            while (!src.exhausted()) {
                                val line = src.readUtf8Line() ?: continue
                                if (line.startsWith("data:")) onTrigger()
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        // Swallow other errors; reconnect after backoff.
                    }
                    delay(5_000) // reconnect backoff
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
