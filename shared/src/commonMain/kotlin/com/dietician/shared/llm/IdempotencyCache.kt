package com.dietician.shared.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Idempotency + in-flight coalescing cache for LLM calls.
 *
 * Council 1779062699 RC7 (PROMOTED): under burst conditions (UI re-renders, retry loops,
 * concurrent ScreenModels) N identical concurrent calls within [ttlMs] MUST collapse to
 * exactly ONE upstream dispatch. The earlier "cache result + retry on cache miss" design
 * still permitted N concurrent dispatches when none had finished yet.
 *
 * Concurrency model: a single [Mutex] guards both [cache] and [pending]. Inside the lock
 * we resolve either:
 *   1. fresh cached response → return immediately
 *   2. a pending in-flight call → await its result (lock released first)
 *   3. neither → register a [CompletableDeferred], release lock, run `compute()`, then
 *      complete the deferred, populate cache, drop pending entry inside the lock again.
 *
 * On exception: pending entry is dropped + deferred is completed exceptionally so any
 * coalesced awaiters re-throw the same exception (callers wishing to retry must construct
 * a new dedup call — this is intentional; failures should not be silently shared past the
 * retry boundary).
 */
class IdempotencyCache(
    private val ttlMs: Long = 5_000,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Key, CachedResponse>()
    private val pending = mutableMapOf<Key, CompletableDeferred<LlmResponse>>()

    suspend fun dedup(key: Key, compute: suspend () -> LlmResponse): LlmResponse {
        // Phase 1 — fast path under lock: return cached, or await pending, or register self.
        val deferred: CompletableDeferred<LlmResponse>
        val awaitExisting: CompletableDeferred<LlmResponse>?
        mutex.withLock {
            val cached = cache[key]
            if (cached != null && cached.notExpired(clock(), ttlMs)) {
                return cached.response
            }
            val existing = pending[key]
            if (existing != null) {
                awaitExisting = existing
                deferred = existing // unused on this path
            } else {
                awaitExisting = null
                deferred = CompletableDeferred()
                pending[key] = deferred
            }
        }

        if (awaitExisting != null) {
            return awaitExisting.await()
        }

        // Phase 2 — we own the dispatch. Run `compute` OUTSIDE the lock so concurrent
        // awaiters that joined `pending[key]` are not blocked.
        return try {
            val r = compute()
            mutex.withLock {
                cache[key] = CachedResponse(r, clock())
                pending.remove(key)
            }
            deferred.complete(r)
            r
        } catch (t: Throwable) {
            mutex.withLock { pending.remove(key) }
            deferred.completeExceptionally(t)
            throw t
        }
    }

    data class Key(val subjectId: String, val promptHash: String, val model: String)

    private data class CachedResponse(val response: LlmResponse, val cachedAtMs: Long) {
        fun notExpired(nowMs: Long, ttlMs: Long): Boolean = nowMs - cachedAtMs < ttlMs
    }
}
