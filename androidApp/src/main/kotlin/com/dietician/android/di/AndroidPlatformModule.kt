package com.dietician.android.di

import android.content.Context
import com.dietician.android.CameraXCapture
import com.dietician.android.MediaStoreSaver
import com.dietician.shared.data.DataModuleAndroid
import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.llm.AndroidCoachLlmGateway
import com.dietician.shared.llm.CoachLlmGateway
import com.dietician.shared.llm.net.CoachHttpClient
import com.dietician.shared.ui.network.BaseUrlProvider
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android Koin module.
 *
 * Bindings:
 *   - [Context] — the application context.
 *   - [CameraXCapture] / [MediaStoreSaver] — singletons; both need Context.
 *   - [DieticianDatabase] — SQLDelight via AndroidSqliteDriver.
 *   - iter-11 [CoachLlmGateway] — thin SSE consumer of `/coach/stream` (server
 *     handles reserve+commit internally; no ClaudeMax on Android).
 */
fun androidPlatformModule(applicationContext: Context): Module = module {
    single<Context> { applicationContext }
    single { CameraXCapture(get()) }
    single { MediaStoreSaver(get()) }
    single<DieticianDatabase> { DataModuleAndroid.build(get()) }

    // iter-11 Coach plumbing
    single {
        CoachHttpClient(
            http = get<HttpClient>(),
            baseUrl = get<BaseUrlProvider>().baseUrl,
        )
    }
    single<CoachLlmGateway> { AndroidCoachLlmGateway(http = get()) }
}
