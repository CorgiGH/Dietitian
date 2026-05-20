package com.dietician.desktop.di

import com.dietician.shared.data.DataModuleDesktop
import com.dietician.shared.data.sql.DieticianDatabase
import com.dietician.shared.llm.CoachLlmGateway
import com.dietician.shared.llm.CoachLocale
import com.dietician.shared.llm.DesktopCoachLlmGateway
import com.dietician.shared.llm.DesktopOutboxReplay
import com.dietician.shared.llm.LlmChunk
import com.dietician.shared.llm.LocalCoachProvider
import com.dietician.shared.llm.net.CoachHttpClient
import com.dietician.shared.llm.provider.ClaudeMaxCliProvider
import com.dietician.shared.ui.network.BaseUrlProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop Koin module.
 *
 * Provides the [DieticianDatabase] singleton via [DataModuleDesktop.build] which
 * opens the JDBC SQLite driver at `%APPDATA%/Dietician/dietician.db` (Win) or
 * `~/Dietician/dietician.db`, applies the SQLDelight schema on first launch,
 * and toggles WAL pragmas per Plan-1 RC5.
 *
 * iter-11: wires [DesktopCoachLlmGateway] + [DesktopOutboxReplay] for the Coach
 * 2-phase commit pipeline. ClaudeMax CLI subprocess is bridged into a single-
 * chunk Flow via the inline [LocalCoachProvider] adapter (the CLI does not
 * stream — entire response emits as one terminal chunk). Outbox replay runs
 * once on bootstrap before any UI mounts.
 */
val desktopPlatformModule: Module = module {
    single<DieticianDatabase> { DataModuleDesktop.build() }

    // iter-11 Coach plumbing
    single {
        CoachHttpClient(
            http = get<HttpClient>(),
            baseUrl = get<BaseUrlProvider>().baseUrl,
        )
    }
    single<LocalCoachProvider> {
        // Production: bridge ClaudeMaxCliProvider.call() (single LlmResponse) into
        // a Flow<LlmChunk> that emits exactly one terminal chunk. Real per-token
        // streaming via the CLI's --stream mode is a future enhancement.
        val cli = ClaudeMaxCliProvider.production()
        object : LocalCoachProvider {
            override fun run(prompt: String, locale: CoachLocale): Flow<LlmChunk> =
                flow {
                    val request =
                        com.dietician.shared.llm.LlmRequest(
                            subjectId = "victor",
                            task = com.dietician.shared.llm.TaskType.TEXT,
                            deviceClass = com.dietician.shared.llm.DeviceClass.VICTOR_DESKTOP,
                            capability = com.dietician.shared.llm.Capability.NON_STREAMING,
                            messages =
                            listOf(
                                com.dietician.shared.llm.LlmMessage(
                                    com.dietician.shared.llm.Role.USER,
                                    prompt,
                                ),
                            ),
                        )
                    // Empty model lets the `claude` CLI use its own configured
                    // default; ClaudeMaxJsonParser reads the actual model id back
                    // from the response envelope. "sonnet" is the explicit
                    // fallback label if the envelope omits it.
                    val resp = cli.call(request, model = "sonnet")
                    emit(
                        LlmChunk(
                            text = resp.text,
                            tokenCount = resp.outputTokens,
                            isDone = true,
                            finalResponse = resp,
                        ),
                    )
                }
        }
    }
    single<CoachLlmGateway> {
        DesktopCoachLlmGateway(
            db = get(),
            http = get(),
            provider = get(),
        )
    }
    single { DesktopOutboxReplay(db = get(), http = get()) }
}
