package com.dietician.shared.ui.network

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
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Koin wiring for [networkModule] — asserts that the bound repo interfaces
 * resolve to their concrete Http* implementations (RC: Plan-3 endpoint
 * integration verification — see Batch E task brief).
 *
 * Tests use an isolated KoinApplication rather than the global context so we
 * don't conflict with a parallel test that might also call startKoin.
 */
class NetworkModuleTest {

    @AfterTest
    fun cleanup() {
        runCatching { stopKoin() }
    }

    @Test
    fun `networkModule wires every Http repository`() {
        val app = koinApplication { modules(networkModule) }
        val koin = app.koin
        // HttpClient + BaseUrlProvider both resolve.
        koin.get<BaseUrlProvider>()
        koin.get<HttpClient>()
        // Each interface resolves to its Http* concrete class.
        assertTrue(koin.get<AuditRepository>() is HttpAuditRepository)
        assertTrue(koin.get<ByokRepository>() is HttpByokRepository)
        assertTrue(koin.get<PaperSearchRepository>() is HttpPaperSearchRepository)
        assertTrue(koin.get<ReceiptUploadRepository>() is HttpReceiptUploadRepository)
        assertTrue(koin.get<RecipeIngestClient>() is HttpRecipeIngestClient)
        koin.close()
    }

    @Test
    fun `networkModule starts cleanly via global startKoin`() {
        // Smoke: regression guard against double-binding / circular dep panic.
        startKoin { modules(networkModule) }
        // No assertion needed — startKoin throws on collision.
    }
}
