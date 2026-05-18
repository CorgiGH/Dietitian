package com.dietician.shared.llm

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Plan-2 Task 29 — SubjectCredentialStore contract tests.
 *
 * In-memory variant is the test/dry-run impl; server-side wraps Plan-3 CredentialRepository.
 * Both must satisfy the same contract: getKey returns the stored plaintext or null;
 * listProviders enumerates active credentials per subject.
 */
class SubjectCredentialStoreTest {
    @Test
    fun `getKey returns stored plaintext for subject + provider`() = runTest {
        val store = InMemorySubjectCredentialStore(
            store = mapOf(
                ("victor" to ProviderId("openrouter")) to "sk-victor-openrouter",
                ("friend" to ProviderId("openrouter")) to "sk-friend-openrouter",
                ("victor" to ProviderId("anthropic")) to "sk-victor-anthropic",
            ),
        )
        store.getKey("victor", ProviderId("openrouter")) shouldBe "sk-victor-openrouter"
        store.getKey("victor", ProviderId("anthropic")) shouldBe "sk-victor-anthropic"
        store.getKey("friend", ProviderId("openrouter")) shouldBe "sk-friend-openrouter"
    }

    @Test
    fun `getKey returns null for unknown subject or provider`() = runTest {
        val store = InMemorySubjectCredentialStore(
            store = mapOf(("victor" to ProviderId("openrouter")) to "k"),
        )
        store.getKey("victor", ProviderId("anthropic")) shouldBe null
        store.getKey("ghost", ProviderId("openrouter")) shouldBe null
    }

    @Test
    fun `listProviders enumerates only the subject's active credentials`() = runTest {
        val store = InMemorySubjectCredentialStore(
            store = mapOf(
                ("victor" to ProviderId("openrouter")) to "a",
                ("victor" to ProviderId("anthropic")) to "b",
                ("friend" to ProviderId("groq")) to "c",
            ),
        )
        store.listProviders("victor") shouldContainExactlyInAnyOrder setOf(
            ProviderId("openrouter"),
            ProviderId("anthropic"),
        )
        store.listProviders("friend") shouldContainExactlyInAnyOrder setOf(ProviderId("groq"))
        store.listProviders("ghost") shouldBe emptySet()
    }
}
