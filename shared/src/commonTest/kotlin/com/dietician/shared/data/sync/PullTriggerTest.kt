package com.dietician.shared.data.sync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PullTriggerTest {
    @Test
    fun `200ms debounce coalesces a burst into one tick`() =
        runTest {
            // `backgroundScope` (kotlinx-coroutines-test idiom): the coalescer's internal
            // debounce-collector AND the outer subscriber both never complete on their own,
            // so they must live on `backgroundScope` (auto-cancelled at test end) — otherwise
            // `runTest` reports `UncompletedCoroutinesError`.
            val coalescer = PullTriggerCoalescer(debounceMs = 200, scope = backgroundScope)
            val emissions = mutableListOf<PullTrigger>()
            backgroundScope.launch { coalescer.coalesced().collect { emissions.add(it) } }

            coalescer.push(PullTrigger.Ws)
            coalescer.push(PullTrigger.Ntfy)
            coalescer.push(PullTrigger.Manual)
            advanceTimeBy(150)
            emissions.size shouldBe 0
            advanceTimeBy(100)
            emissions.size shouldBe 1
        }
}
