package com.dietician.server.llm

import com.dietician.shared.llm.AuditEntry
import com.dietician.shared.llm.AuditLogSink
import com.dietician.shared.llm.BudgetLedger
import com.dietician.shared.llm.InMemorySubjectCredentialStore
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Reservation
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull

/**
 * Plan-2 Task 28 — LlmRouterFactory contract tests.
 *
 * No DB needed — uses in-memory stubs for BudgetLedger / AuditLogSink / CredentialStore.
 * Validates env-key fail-fast + happy-path construction.
 */
class LlmRouterFactoryTest {

    private val stubBudget = object : BudgetLedger {
        override suspend fun reserve(
            subjectId: String,
            provider: ProviderId,
            estimateTokens: Int,
            estimateCostCents: Int,
        ): Reservation = Reservation("r", subjectId, provider, estimateTokens, estimateCostCents)
        override suspend fun finalize(reservation: Reservation, actualTokens: Int, actualCostCents: Int) = Unit
        override suspend fun release(reservation: Reservation) = Unit
    }

    private val stubAudit = object : AuditLogSink {
        override suspend fun write(entry: AuditEntry) = Unit
    }

    // The factory only holds onto the HttpClient — never invokes it during construction —
    // so any client instance suffices. We never call route() in this test, just verify the
    // wiring contract + env-key fail-fast.
    private fun mockClient(): HttpClient = HttpClient(CIO)

    @Test
    fun `create with all keys present builds a router`() {
        val router = LlmRouterFactory.create(
            httpClient = mockClient(),
            credentialStore = InMemorySubjectCredentialStore(),
            budget = stubBudget,
            auditLog = stubAudit,
            env = { key ->
                when (key) {
                    "OPENROUTER_API_KEY" -> "sk-or-test"
                    "ANTHROPIC_API_KEY" -> "sk-an-test"
                    "GEMINI_API_KEY" -> "sk-ge-test"
                    "GROQ_API_KEY" -> "sk-gq-test"
                    else -> null
                }
            },
        )
        assertNotNull(router)
        // Audit sink is exposed publicly so callers can write extra rows (e.g. moderator).
        assertNotNull(router.auditLog)
    }

    @Test
    fun `create fails fast when OPENROUTER_API_KEY missing`() {
        val ex = assertThrows<IllegalArgumentException> {
            LlmRouterFactory.create(
                httpClient = mockClient(),
                credentialStore = InMemorySubjectCredentialStore(),
                budget = stubBudget,
                auditLog = stubAudit,
                env = { key -> if (key == "GROQ_API_KEY") "sk-gq-test" else null },
            )
        }
        assert(ex.message!!.contains("OPENROUTER_API_KEY"))
    }

    @Test
    fun `create fails fast when GROQ_API_KEY missing`() {
        val ex = assertThrows<IllegalArgumentException> {
            LlmRouterFactory.create(
                httpClient = mockClient(),
                credentialStore = InMemorySubjectCredentialStore(),
                budget = stubBudget,
                auditLog = stubAudit,
                env = { key -> if (key == "OPENROUTER_API_KEY") "sk-or-test" else null },
            )
        }
        assert(ex.message!!.contains("GROQ_API_KEY"))
    }
}
