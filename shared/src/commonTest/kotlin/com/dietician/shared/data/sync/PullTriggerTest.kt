package com.dietician.shared.data.sync

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PullTriggerTest {
    @Test
    fun `200ms debounce coalesces a burst into one tick`() = runTest {
        val coalescer = PullTriggerCoalescer(debounceMs = 200, scope = this)
        val emissions = mutableListOf<PullTrigger>()
        // `backgroundScope` (kotlinx-coroutines-test idiom): SharedFlow collectors never
        // complete, so they must NOT live on the test scope or `runTest` reports
        // `UncompletedCoroutinesError`. backgroundScope is auto-cancelled at test end.
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
