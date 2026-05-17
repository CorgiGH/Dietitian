package com.dietician.shared.data.metadata

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.test.Test

class LwwClockSkewPropertyTest {
    @Test
    fun `merge is deterministic and ±24h skew never flips the winner inconsistently`() {
        kotlinx.coroutines.runBlocking {
        val day = 24L * 3600 * 1000
        checkAll(100, Arb.long(-day..day), Arb.long(-day..day)) { skewA, skewB ->
            val clkTrue = 1_000_000L
            val hlcA = HybridLogicalClock("dev-A") { clkTrue + skewA }
            val hlcB = HybridLogicalClock("dev-B") { clkTrue + skewB }

            val tA = hlcA.now()
            val tB = hlcB.now()

            val winner = LwwMerge.pick(
                Lww(value = "A", hlc = tA, serverRecvAt = null),
                Lww(value = "B", hlc = tB, serverRecvAt = null),
            )

            // Determinism: same call yields same winner.
            val winner2 = LwwMerge.pick(
                Lww(value = "A", hlc = tA, serverRecvAt = null),
                Lww(value = "B", hlc = tB, serverRecvAt = null),
            )
            winner shouldBe winner2

            // Symmetry under argument swap: same winner picked.
            val winnerSwapped = LwwMerge.pick(
                Lww(value = "B", hlc = tB, serverRecvAt = null),
                Lww(value = "A", hlc = tA, serverRecvAt = null),
            )
            winnerSwapped shouldBe winner
        }
        }
    }
}
