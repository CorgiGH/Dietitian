package com.dietician.shared.data

/** Wall clock indirection so tests can inject deterministic times. */
expect class WallClock() {
    fun nowMillis(): Long
}

/** Test fake. Use only in tests. */
class FakeWallClock(private var t: Long = 0L) {
    fun nowMillis(): Long = t
    fun advance(deltaMs: Long) { t += deltaMs }
    fun set(absoluteMs: Long) { t = absoluteMs }
}
