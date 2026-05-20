package com.dietician.shared.ui.network

import com.dietician.shared.ui.auth.AuthRepository
import com.dietician.shared.ui.data.AuditRepository
import com.dietician.shared.ui.data.ByokRepository
import com.dietician.shared.ui.data.HttpAuditRepository
import com.dietician.shared.ui.data.HttpByokRepository
import com.dietician.shared.ui.data.HttpPaperSearchRepository
import com.dietician.shared.ui.data.HttpReceiptUploadRepository
import com.dietician.shared.ui.data.HttpRecipeIngestClient
import com.dietician.shared.ui.data.PaperSearchRepository
import com.dietician.shared.ui.data.ReceiptUploadRepository
import com.dietician.shared.ui.data.RecipeIngestClient
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that exposes the shared [HttpClient] + every HTTP-backed repo
 * (Audit / Consent — both live on [AuditRepository] — / BYOK / Paper search /
 * Receipt upload / Recipe ingest).
 *
 * Wiring order (matches Plan-3 endpoint coverage):
 *   - [BaseUrlProvider] — single-source-of-truth for the VPS URL.
 *   - [HttpClient] — Ktor with [SessionInterceptor] + WebSockets + JSON.
 *   - Repos bound by their interface contract so screens accept fakes in tests
 *     and the real impl at runtime.
 *
 * The PantryRepository is NOT in this module — it backs onto the local
 * SQLite/EventStore ledger (Plan-1), not an HTTP endpoint. Pantry sync is
 * routed via Plan-3 `/sync/push` + `/sync/pull` at the data layer below.
 */
val networkModule: Module = module {
    single { BaseUrlProvider() }
    single<HttpClient> { HttpClientFactory.create(get()) }

    // Audit + consent — same repo wraps both endpoints (POST /me/consent is
    // exposed via updateConsent / listConsents on AuditRepository per Batch C).
    single<AuditRepository> { HttpAuditRepository(get(), get<BaseUrlProvider>().baseUrl) }

    single<ByokRepository> { HttpByokRepository(get(), get<BaseUrlProvider>().baseUrl) }

    single<PaperSearchRepository> {
        HttpPaperSearchRepository(get(), get<BaseUrlProvider>().baseUrl)
    }

    single<ReceiptUploadRepository> {
        HttpReceiptUploadRepository(get(), get<BaseUrlProvider>().baseUrl)
    }

    single<RecipeIngestClient> {
        HttpRecipeIngestClient(get(), get<BaseUrlProvider>().baseUrl)
    }

    // iter-11 desktop-drill fix: real magic-link auth so the desktop client can
    // obtain a server session (the onboarding "Simulate verify" only set a local
    // onboarded flag — Coach 2PC then 401'd for lack of a real session cookie).
    single { AuthRepository(get(), get<BaseUrlProvider>().baseUrl) }
}
