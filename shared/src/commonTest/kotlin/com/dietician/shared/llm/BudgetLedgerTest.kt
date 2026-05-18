package com.dietician.shared.llm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class BudgetLedgerTest {
    private val provider = ProviderId("openrouter")

    @Test
    fun `reserve within cap succeeds`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to 1_000))
        val r = ledger.reserve("victor", provider, estimateTokens = 100, estimateCostCents = 50)
        r.reservedCostCents shouldBe 50
        r.subjectId shouldBe "victor"
        ledger.usedCents("victor", provider) shouldBe 50
    }

    @Test
    fun `reserve breaching cap throws BudgetExhausted before increment`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to 100))
        ledger.reserve("victor", provider, 1, 60)
        shouldThrow<LlmError.BudgetExhausted> {
            ledger.reserve("victor", provider, 1, 50) // 60 + 50 > 100
        }
        ledger.usedCents("victor", provider) shouldBe 60
    }

    @Test
    fun `finalize reconciles when actual is less than estimate`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to 1_000))
        val r = ledger.reserve("victor", provider, 100, 200)
        ledger.finalize(r, actualTokens = 80, actualCostCents = 120)
        ledger.usedCents("victor", provider) shouldBe 120
    }

    @Test
    fun `finalize reconciles when actual exceeds estimate (router accepts overrun)`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to 1_000))
        val r = ledger.reserve("victor", provider, 100, 200)
        ledger.finalize(r, actualTokens = 150, actualCostCents = 250)
        ledger.usedCents("victor", provider) shouldBe 250
    }

    @Test
    fun `release reverses reservation`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to 1_000))
        val r = ledger.reserve("victor", provider, 100, 200)
        ledger.release(r)
        ledger.usedCents("victor", provider) shouldBe 0
    }

    @Test
    fun `no cap configured means infinite budget`() = runTest {
        val ledger = InMemoryBudgetLedger()
        ledger.reserve("victor", provider, 1, 1_000_000_000)
        ledger.usedCents("victor", provider) shouldBe 1_000_000_000
    }

    @Test
    fun `separate subjects keep separate ledgers`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("a" to 100, "b" to 100))
        ledger.reserve("a", provider, 1, 60)
        ledger.reserve("b", provider, 1, 60)
        ledger.usedCents("a", provider) shouldBe 60
        ledger.usedCents("b", provider) shouldBe 60
    }

    @Test
    fun `separate providers per subject keep separate ledgers`() = runTest {
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to Int.MAX_VALUE))
        ledger.reserve("victor", ProviderId("openrouter"), 1, 100)
        ledger.reserve("victor", ProviderId("groq"), 1, 200)
        ledger.usedCents("victor", ProviderId("openrouter")) shouldBe 100
        ledger.usedCents("victor", ProviderId("groq")) shouldBe 200
    }

    /**
     * Property: N concurrent reservations against a cap that allows exactly K of them MUST
     * accept exactly K and reject the remaining N-K with BudgetExhausted. This is the same
     * atomicity guarantee Plan-3's V019 `consume_or_fail` PG fn provides server-side.
     */
    @Test
    fun `concurrent reservations respect cap`() = runTest {
        val cap = 500
        val per = 100
        val ledger = InMemoryBudgetLedger(capCentsPerSubject = mapOf("victor" to cap))
        val attempts = 20
        val results = coroutineScope {
            (1..attempts).map {
                async {
                    runCatching { ledger.reserve("victor", provider, 1, per) }
                }
            }.awaitAll()
        }
        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure && it.exceptionOrNull() is LlmError.BudgetExhausted }
        successes shouldBe (cap / per)
        failures shouldBe (attempts - cap / per)
        ledger.usedCents("victor", provider) shouldBe (successes * per)
    }
}
