package com.dietician.shared.data.metadata

/**
 * Hybrid Logical Clock timestamp. Tuple (wallMs, seq, deviceId) is totally ordered.
 * Council BREAK #2 uses this tuple as the LWW key for pantry_metadata so concurrent
 * writes from skew-affected clocks still merge deterministically.
 */
data class HlcTimestamp(val wallMs: Long, val seq: Int, val deviceId: String) : Comparable<HlcTimestamp> {
    override fun compareTo(other: HlcTimestamp): Int {
        val w = wallMs.compareTo(other.wallMs)
        if (w != 0) return w
        val s = seq.compareTo(other.seq)
        if (s != 0) return s
        return deviceId.compareTo(other.deviceId)
    }
}

/**
 * Hybrid Logical Clock (Kulkarni 2014). Monotonic across wall-clock skew.
 *
 * Concurrency contract: single-writer per device. Caller MUST ensure now()/recv()/restore()
 * are not invoked concurrently (typically: confine to a single coroutine context, or wrap
 * with a kotlinx.coroutines.sync.Mutex at the call site). This class deliberately does not
 * use JVM `synchronized` because commonMain stdlib does not expose it; pulling in
 * kotlinx.atomicfu.locks just for a single counter would be overkill given the
 * single-writer-per-device invariant.
 *
 * Plan-deviation note: plan body wrapped both methods with `synchronized(lock)`. That
 * compiles on JVM but not on commonMain. Dropped, contract documented above.
 */
class HybridLogicalClock(
    private val initialDeviceId: String,
    private val wallNow: () -> Long,
) {
    private var lastWall: Long = 0L
    private var lastSeq: Int = 0

    fun now(): HlcTimestamp {
        val w = wallNow()
        if (w > lastWall) {
            lastWall = w
            lastSeq = 0
        } else {
            lastSeq += 1
        }
        return HlcTimestamp(lastWall, lastSeq, initialDeviceId)
    }

    /**
     * Absorb a remote timestamp without ticking. Subsequent now() does the +1.
     *
     * Plan-deviation note: plan body added +1 in every recv branch which double-ticks
     * across the recv->now() pair. Tests expect recv to "merge state" and now() to advance.
     * Standard HLC variants split this two ways; this codebase picks the merge-only variant
     * because it's what makes the property tests deterministic at the call site.
     */
    fun recv(remote: HlcTimestamp) {
        val w = wallNow()
        val newWall = maxOf(lastWall, remote.wallMs, w)
        val newSeq =
            when {
                newWall == lastWall && newWall == remote.wallMs -> maxOf(lastSeq, remote.seq)
                newWall == lastWall -> lastSeq
                newWall == remote.wallMs -> remote.seq
                else -> 0
            }
        lastWall = newWall
        lastSeq = newSeq
    }

    fun snapshot(): Pair<Long, Int> = lastWall to lastSeq

    fun restore(
        wall: Long,
        seq: Int,
    ) {
        lastWall = wall
        lastSeq = seq
    }
}
