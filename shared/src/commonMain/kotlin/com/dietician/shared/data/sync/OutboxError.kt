package com.dietician.shared.data.sync

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * Transport vs permanent classification for outbox-drain push failures.
 *
 * Council #2 fix: previously `OutboxDrainWorker.drainOnce` caught all `Throwable` on
 * `client.push`, incremented attempts, and promoted to `outbox_dead` at 10. A single
 * 20-second WAN blip with `HttpTimeout` configured to fire would dead-letter the entire
 * batch on one failure. Now:
 *
 *   * Transient (IOException, all timeout flavors, 5xx) → DO NOT increment attempts; sleep
 *     with backoff; retry next tick.
 *   * Permanent (4xx, parse error, declared `PermanentSyncException`, `IllegalStateException`)
 *     → increment attempts, dead-letter at 10.
 *   * Anything else → conservative Transient (log + retry).
 *
 * `CancellationException` is re-thrown by the worker before calling [classifyError]; it must
 * never be classified as either Transient or Permanent — see the worker's catch block.
 */
sealed class OutboxError {
    data object Transient : OutboxError()

    data object Permanent : OutboxError()
}

/**
 * Marker exception for deliberate "do not retry, dead-letter immediately on attempt 10"
 * cases that don't have a natural HTTP shape (e.g. payload too large after compression,
 * unparseable local row, schema-version mismatch).
 */
class PermanentSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Map a caught `Throwable` to a retry verdict. Callers MUST re-throw [CancellationException]
 * BEFORE invoking this — passing it here would route to Transient and the worker would
 * become unkillable from its parent scope.
 */
fun classifyError(t: Throwable): OutboxError {
    if (t is CancellationException) {
        // Defensive: callers should re-throw before calling; this is a last-resort guard.
        throw t
    }
    return when (t) {
        is PermanentSyncException -> OutboxError.Permanent
        is ClientRequestException -> OutboxError.Permanent // 4xx
        is ServerResponseException -> OutboxError.Transient // 5xx — server-side hiccup
        is HttpRequestTimeoutException -> OutboxError.Transient
        is ConnectTimeoutException -> OutboxError.Transient
        is SocketTimeoutException -> OutboxError.Transient
        is IOException -> OutboxError.Transient // ConnectException et al. extend IOException
        is IllegalStateException -> OutboxError.Permanent // bad local state — won't fix on retry
        // Parse errors from kotlinx-serialization surface as SerializationException; route
        // through reflection-free class-name check to avoid adding a hard dep.
        else -> if (t::class.simpleName?.contains("SerializationException") == true) {
            OutboxError.Permanent
        } else {
            OutboxError.Transient // conservative default — better to retry than dead-letter wrongly
        }
    }
}
