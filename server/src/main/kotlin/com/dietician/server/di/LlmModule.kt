package com.dietician.server.di

import com.dietician.server.llm.AuditLogSinkAdapter
import com.dietician.server.llm.BudgetRepositoryAdapter
import com.dietician.server.llm.LlmRouterFactory
import com.dietician.server.llm.PiiReviewQueueImpl
import com.dietician.server.llm.SubjectCredentialStoreImpl
import com.dietician.shared.llm.AuditLogSink
import com.dietician.shared.llm.BudgetLedger
import com.dietician.shared.llm.LlmRouter
import com.dietician.shared.llm.PiiReviewQueue
import com.dietician.shared.llm.SubjectCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Plan-2 Task 28 — Server-side Koin module wiring `:shared:llm` LlmRouter onto Plan-3
 * primitives.
 *
 * Singletons:
 *   - shared CIO [HttpClient] with content-negotiation + per-call timeout plugin. Per-call
 *     timeout overrides at the provider level via [com.dietician.shared.llm.ProviderConfig]
 *     are NOT applied here — that knob is per-request and the providers don't wire it; the
 *     callMs/readMs in [ProviderConfig] are a forward-compat surface for Batch E.
 *   - [BudgetLedger] via [BudgetRepositoryAdapter] over Plan-3 BudgetRepository.
 *   - [AuditLogSink] via [AuditLogSinkAdapter] over Plan-3 AuditLogWriter.
 *   - [SubjectCredentialStore] via [SubjectCredentialStoreImpl] over Plan-3
 *     CredentialRepository.
 *   - [LlmRouter] via [LlmRouterFactory.create] — built once at app startup; env-key
 *     validation fail-fasts here.
 */
val llmModule = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                        explicitNulls = false
                    },
                )
            }
            install(HttpTimeout) {
                // Conservative envelope; per-provider overrides land in Batch E.
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    single<BudgetLedger> { BudgetRepositoryAdapter(get()) }
    single<AuditLogSink> { AuditLogSinkAdapter(get()) }
    single<SubjectCredentialStore> { SubjectCredentialStoreImpl(get()) }
    single<PiiReviewQueue> { PiiReviewQueueImpl(get()) }

    single<LlmRouter> {
        LlmRouterFactory.create(
            httpClient = get(),
            credentialStore = get(),
            budget = get(),
            auditLog = get(),
        )
    }
}

/**
 * Smoke-time variant — used by [LlmModuleSmokeTest] + dev-runs where env keys may be unset.
 *
 * Skips the LlmRouter binding (env-key fail-fast would crash boot) but DOES bind the
 * adapters + credential store so server tests can exercise wiring without LLM upstream.
 */
val llmAdaptersOnlyModule = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) { json() }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 30_000
            }
        }
    }
    single<BudgetLedger> { BudgetRepositoryAdapter(get()) }
    single<AuditLogSink> { AuditLogSinkAdapter(get()) }
    single<SubjectCredentialStore> { SubjectCredentialStoreImpl(get()) }
    single<PiiReviewQueue> { PiiReviewQueueImpl(get()) }
}
