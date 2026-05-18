package com.dietician.server.llm

import com.dietician.server.repo.CredentialRepository
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.SubjectCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Plan-2 Task 29 — Server-side adapter from `:shared:llm` [SubjectCredentialStore] to
 * Plan-3 [CredentialRepository].
 *
 * `getKey` decrypts via pgcrypto `pgp_sym_decrypt` keyed by the master passphrase on tmpfs.
 * `listProviders` returns ProviderId-wrapped strings filtered to active (non-revoked)
 * credentials.
 *
 * Defense: the decrypted plaintext NEVER touches a log line or stack trace. The Router
 * passes it directly into an HTTP `Authorization: Bearer <key>` header.
 */
class SubjectCredentialStoreImpl(
    private val credRepo: CredentialRepository,
) : SubjectCredentialStore {
    override suspend fun getKey(subjectId: String, provider: ProviderId): String? {
        val uuid = parseSubjectUuid(subjectId) ?: return null
        return withContext(Dispatchers.IO) {
            credRepo.read(uuid, provider.raw)
        }
    }

    override suspend fun listProviders(subjectId: String): Set<ProviderId> {
        val uuid = parseSubjectUuid(subjectId) ?: return emptySet()
        return withContext(Dispatchers.IO) {
            credRepo.listForSubject(uuid)
                .filter { it.isActive }
                .map { ProviderId(it.provider) }
                .toSet()
        }
    }

    private fun parseSubjectUuid(s: String): UUID? = runCatching { UUID.fromString(s) }.getOrNull()
}
