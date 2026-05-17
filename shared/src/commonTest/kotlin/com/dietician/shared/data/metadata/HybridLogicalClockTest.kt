package com.dietician.shared.data.metadata

import com.dietician.shared.data.FakeWallClock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.comparables.shouldBeGreaterThan
import kotlin.test.Test

class HybridLogicalClockTest {
    @Test
    fun `now is monotonic even when wall clock goes backward`() {
        val clk = FakeWallClock(1_000L)
        val hlc = HybridLogicalClock(initialDeviceId = "dev-1", wallNow = { clk.nowMillis() })

        val t1 = hlc.now()
        clk.set(500L) // wall went backward
        val t2 = hlc.now()

        t2 shouldBeGreaterThan t1
    }

    @Test
    fun `now advances seq when wall stays the same`() {
        val clk = FakeWallClock(1_000L)
        val hlc = HybridLogicalClock(initialDeviceId = "dev-1", wallNow = { clk.nowMillis() })
        val t1 = hlc.now()
        val t2 = hlc.now()
        (t2.seq - t1.seq) shouldBe 1
        t2.wallMs shouldBe t1.wallMs
    }

    @Test
    fun `recv adopts remote wall if greater`() {
        val clk = FakeWallClock(1_000L)
        val hlc = HybridLogicalClock("dev-1", wallNow = { clk.nowMillis() })
        hlc.recv(HlcTimestamp(wallMs = 5_000L, seq = 7, deviceId = "dev-2"))
        val t = hlc.now()
        t.wallMs shouldBe 5_000L
        t.seq shouldBe 8
    }
}
