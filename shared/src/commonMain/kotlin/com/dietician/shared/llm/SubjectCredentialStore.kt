package com.dietician.shared.llm

/**
 * Plan-2 Task 29 — BYOK credential lookup interface.
 *
 * Each subject can bring-their-own API key per provider. The Router calls [getKey] at
 * request-time to build a per-subject [ProviderConfig]; if the subject has no BYOK entry
 * for the provider the Router falls back to the operator-provided default key (env var on
 * server, hard-coded `null` on desktop dry-runs).
 *
 * Server-side implementation wraps Plan-3 `CredentialRepository.read` which decrypts via
 * pgcrypto `pgp_sym_decrypt` keyed by the master passphrase on tmpfs.
 *
 * Defense-in-depth: implementations MUST NOT log decrypted key material — neither into
 * structured logs nor into stack traces. Callers should pass the key directly into the HTTP
 * `Authorization: Bearer <key>` header and let it go out of scope as fast as possible.
 */
interface SubjectCredentialStore {
    /**
     * Returns the plaintext API key for [subjectId] + [provider], or null if the subject
     * has no active credential for that provider. Revoked credentials return null too.
     */
    suspend fun getKey(subjectId: String, provider: ProviderId): String?

    /**
     * Lists the providers for which [subjectId] has an active (non-revoked) credential.
     * Used by `/me` to render the BYOK widget.
     */
    suspend fun listProviders(subjectId: String): Set<ProviderId>
}

/**
 * In-memory store for tests + desktop dry-runs. Thread-safety isn't critical — the map is
 * populated at fixture-setup and read-only thereafter.
 */
class InMemorySubjectCredentialStore(
    private val store: Map<Pair<String, ProviderId>, String> = emptyMap(),
) : SubjectCredentialStore {
    override suspend fun getKey(subjectId: String, provider: ProviderId): String? =
        store[subjectId to provider]

    override suspend fun listProviders(subjectId: String): Set<ProviderId> =
        store.keys.filter { it.first == subjectId }.map { it.second }.toSet()
}
