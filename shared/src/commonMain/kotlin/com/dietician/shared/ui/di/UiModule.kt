package com.dietician.shared.ui.di

import com.dietician.shared.data.deviceId
import com.dietician.shared.data.local.EventStore
import com.dietician.shared.data.local.PantrySnapshotStore
import com.dietician.shared.llm.AuditEntry
import com.dietician.shared.llm.AuditLogSink
import com.dietician.shared.llm.CoachLlmGateway
import com.dietician.shared.llm.CoachLlmGatewayLlmStream
import com.dietician.shared.llm.CoachLocale
import com.dietician.shared.llm.LlmStream
import com.dietician.shared.ui.auth.OnboardingActions
import com.dietician.shared.ui.auth.OnboardingActionsImpl
import com.dietician.shared.ui.components.PlannedCutController
import com.dietician.shared.ui.components.TodayNutrientsState
import com.dietician.shared.ui.data.PantryReader
import com.dietician.shared.ui.data.PantryWriter
import com.dietician.shared.ui.data.Recipe
import com.dietician.shared.ui.data.RecipeReader
import com.dietician.shared.ui.data.SqlDelightPantryStore
import com.dietician.shared.ui.data.saveExportedFile
import com.dietician.shared.ui.screens.AuditLogViewModel
import com.dietician.shared.ui.screens.CoachChatViewModel
import com.dietician.shared.ui.screens.CookbookViewModel
import com.dietician.shared.ui.screens.FoodLogViewModel
import com.dietician.shared.ui.screens.HomeLoader
import com.dietician.shared.ui.screens.HomeViewModel
import com.dietician.shared.ui.screens.MeProfile
import com.dietician.shared.ui.screens.PantryViewModel
import com.dietician.shared.ui.screens.PaperSearchViewModel
import com.dietician.shared.ui.screens.ReceiptUploadViewModel
import com.dietician.shared.ui.screens.SettingsViewModel
import com.dietician.shared.ui.settings.PersistedSettingsStore
import com.dietician.shared.ui.settings.SettingsPersistence
import com.dietician.shared.ui.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Shared `uiModule` registering ViewModels + the data interfaces they consume.
 *
 * **Iteration 2 scope:** mounts FoodLog + Pantry + CoachChat + AuditLog real
 * screens on top of iteration 1 (Home). Cookbook + Settings stay placeholder
 * in `Routes.kt` until later iterations (Settings has no `SettingsScreen.kt`
 * yet; Cookbook isn't a bottom-nav tab in spec). Stubs return deterministic
 * empty/placeholder data so the screens paint their real empty-states instead
 * of crashing on a missing dep.
 *
 * Wiring map per VM:
 *   - [HomeViewModel] ← [StubHomeLoader]
 *   - [FoodLogViewModel] ← default `coachDisabledProvider = { false }`
 *   - [PantryViewModel] ← [StubPantryReader]
 *   - [CookbookViewModel] ← [StubRecipeReader] + `RecipeIngestClient` from
 *     `networkModule`
 *   - [CoachChatViewModel] ← [StubLlmStream] + [StubAuditLogSink] +
 *     hardcoded subjectId placeholder
 *   - [AuditLogViewModel] ← real `AuditRepository` from `networkModule` +
 *     `saveExportedFile` expect/actual top-level fun
 *
 * Replacements that land in later batches:
 *   - StubHomeLoader → HTTP `/me` adapter + Plan-1 `MealEventStore.todayNutrients`
 *   - StubPantryReader → Plan-1 `PantrySnapshotStore.flowSnapshot`
 *   - StubLlmStream → Plan-2 `LlmRouterStream`
 *   - StubAuditLogSink → `AuditLogWriter` (Plan-3 audit_log table writer)
 *   - StubRecipeReader → Plan-1 `RecipeStore.all` / Plan-7 corpus reader
 */
val uiModule: Module = module {
    // Stubs — replaced by real impls in future iterations / Plan-1/2/3 wires.
    single<HomeLoader> { StubHomeLoader() }
    // Plan-1 SqlDelight-backed pantry — DieticianDatabase comes from the
    // platform module (desktopPlatformModule or androidPlatformModule). The
    // adapter encodes manually-typed display names into the SKU prefix so
    // user-visible names survive a round-trip via pantry_snapshot.
    single { PantrySnapshotStore(db = get()) }
    single { EventStore(db = get(), json = kotlinx.serialization.json.Json) }
    single {
        SqlDelightPantryStore(
            snapshot = get(),
            events = get(),
            deviceId = ::deviceId,
        )
    }
    single<PantryReader> { get<SqlDelightPantryStore>() }
    single<PantryWriter> { get<SqlDelightPantryStore>() }
    // iter-11: Coach is server-routed via a platform-keyed CoachLlmGateway —
    // Desktop runs ClaudeMax CLI locally + 2PC reserve/commit bookend; Android
    // is a thin SSE consumer of `/coach/stream`. CoachLlmGatewayLlmStream
    // bridges the gateway into the existing LlmStream interface read by
    // CoachChatViewModel. The locale provider maps `SettingsStore.locale` →
    // CoachLocale so server-side prompt selection (EN/RO) matches UI.
    single<LlmStream> {
        CoachLlmGatewayLlmStream(
            gateway = get<CoachLlmGateway>(),
            localeProvider = {
                if (get<SettingsStore>().state.value.locale.code == "ro") {
                    CoachLocale.RO
                } else {
                    CoachLocale.EN
                }
            },
        )
    }
    single<AuditLogSink> { StubAuditLogSink() }
    single<RecipeReader> { StubRecipeReader() }
    single { SettingsPersistence() }
    single<SettingsStore> { PersistedSettingsStore(persistence = get()) }
    single<OnboardingActions> {
        OnboardingActionsImpl(
            settingsStore = get(),
            authRepository = getOrNull(),
            scope = get(),
        )
    }

    // UI-side coroutine scope. We use Dispatchers.Default because kotlinx-coroutines
    // doesn't ship Dispatchers.Main for the Compose Desktop JVM (would need
    // kotlinx-coroutines-swing). StateFlow updates are thread-safe so the Compose
    // collectAsState path handles UI-thread crossings on its own.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // ViewModels.
    factory { HomeViewModel(loader = get(), plannedCutController = PlannedCutController()) }
    factory { FoodLogViewModel(coachDisabledProvider = { get<SettingsStore>().state.value.coachDisabled }) }
    factory { PantryViewModel(reader = get(), writer = get()) }
    factory { CookbookViewModel(reader = get(), ingest = get()) }
    factory {
        CoachChatViewModel(
            stream = get(),
            audit = get(),
            subjectIdProvider = { "stub-subject-0000" },
            coachDisabledProvider = { get<SettingsStore>().state.value.coachDisabled },
            coroutineScope = get(),
        )
    }
    factory { SettingsViewModel(store = get()) }
    factory {
        AuditLogViewModel(
            repo = get(),
            saveFile = ::saveExportedFile,
            coroutineScope = get(),
        )
    }
    factory { ReceiptUploadViewModel(repo = get(), coroutineScope = get()) }
    factory { PaperSearchViewModel(repo = get(), coroutineScope = get()) }
}

/* ---------- Stubs ---------- */

private class StubHomeLoader : HomeLoader {
    override suspend fun loadMe(): MeProfile = MeProfile(
        subjectId = "stub-subject-0000",
        displayName = "Victor",
    )

    override suspend fun loadTodayNutrients(subjectId: String): TodayNutrientsState =
        TodayNutrientsState()
}

private class StubAuditLogSink : AuditLogSink {
    override suspend fun write(entry: AuditEntry) {
        // no-op — real AuditLogWriter ships when Plan-3 audit_log writer is bound
    }
}

/**
 * Seeded reader so Cookbook is browsable end-to-end without a deployed Plan-7
 * corpus. Three plausible recipes covering Victor's pantry-friendly meals.
 * Replace with Plan-1 `RecipeStore.all` + Plan-7 corpus reader once those wire in.
 */
private class StubRecipeReader : RecipeReader {
    override suspend fun all(): List<Recipe> = SeedRecipes
}

private val SeedRecipes: List<Recipe> = listOf(
    Recipe(
        id = "seed-chicken-rice-broccoli",
        title = "Chicken + rice + broccoli",
        ingredientsCsv = "Chicken breast 200g,Rice 80g,Broccoli 200g,Olive oil 10ml",
        servings = 1,
    ),
    Recipe(
        id = "seed-oatmeal-banana",
        title = "Oatmeal + banana + peanut butter",
        ingredientsCsv = "Rolled oats 80g,Banana 1pc,Peanut butter 20g,Milk 200ml",
        servings = 1,
    ),
    Recipe(
        id = "seed-ciorba-de-vacuta",
        title = "Ciorbă de văcuță (RO)",
        ingredientsCsv = "Beef shank 300g,Carrot 100g,Onion 50g,Borș 200ml,Parsley 5g",
        servings = 2,
    ),
)
