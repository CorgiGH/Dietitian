# JARVIS_MERGE.md

Adapter contract for folding Dietician into `jarvis-kotlin` life-OS as a Subsystem.

## Verified jarvis contract (must not break)

```kotlin
// jarvis-kotlin/src/main/kotlin/jarvis/subsystem/Subsystem.kt
interface Subsystem {
    val name: String
    val description: String
    suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput
}

data class SubsystemInput(
    val activity: List<ActivityEntry>,
    val wiki: String,
    val recentChat: List<ChatMessage> = emptyList(),
    val userQuery: String? = null,
)

data class SubsystemOutput(
    val text: String,
    val wikiEntry: String? = null,
)
```

`SubsystemOutput` has NO structured-data slot — only `text: String + wikiEntry: String?`. Dietician outputs (meal plan + shopping list + budget breakdown) are structured. **Side-channel via `state/meal-plan-current.json`** with `plan_id: UUID + generated_at: timestamptz` cross-stamped into both the JSON and the markdown header `<!-- plan_id=X generated_at=Y -->`.

## Merge steps

1. `cp -r Desktop/Dietician/src/jvmMain/kotlin/dietician/ jarvis-kotlin/src/main/kotlin/jarvis/dietician/`
2. Rename `package dietician` → `package jarvis.dietician` (sed across files)
3. Drop `:shared:llm.LlmProvider` adapter, wire to `jarvis.Llm` directly. The `LlmRouter` stays — it operates over `LlmProvider` interface; jarvis's `Llm` becomes one of the impls.
4. Drop ClaudeMax CLI subprocess — use jarvis's existing `ClaudeMaxLlm.kt` directly (same subprocess pattern, code already battle-tested in jarvis).
5. Drop OpenRouter HTTP client — use jarvis's existing `OpenRouterChatLlm.kt`.
6. Register in `Subsystems.kt`:
   ```kotlin
   object Subsystems {
       val all: List<Subsystem> by lazy {
           listOf(
               JudgmentSubsystem(),
               DotConnectorSubsystem(),
               // ...
               DieticianSubsystem(),
           )
       }
   }
   ```
7. `DieticianSubsystem` impl:
   ```kotlin
   class DieticianSubsystem(
       private val planner: DieticianPlanner,
       private val resultRenderer: DieticianResultRenderer
   ) : Subsystem {
       override val name = "diet"
       override val description = "Diet planning + shopping + macro tracking"
       override suspend fun run(client: Llm, input: SubsystemInput): SubsystemOutput {
           val query = input.userQuery ?: "what should I eat now?"
           val result: DieticianResult = planner.handle(query, client)
           // Write structured channel atomically
           writeJsonAtomic(File("state/meal-plan-current.json"), result.toStructuredJson(planId = UUID.randomUUID()))
           return SubsystemOutput(
               text = resultRenderer.toMarkdown(result),   // includes <!-- plan_id=X generated_at=Y -->
               wikiEntry = result.rationaleMarkdown        // narrative only, NOT the numbers
           )
       }
   }
   ```
8. Web surface (optional): add `jarvis/web/DieticianRoutes.kt` mirroring `TutorRoutes.kt` pattern. UI either stays as standalone Compose Desktop OR re-renders in React (tutor-web pattern) — accepted debt either way.
9. Database: jarvis-kotlin currently uses sqlite-jdbc + Exposed (per `build.gradle.kts` deps). Dietician uses Postgres on VPS. After merge: **Dietician Postgres stays on VPS, jarvis backend connects via Tailscale**. No fold of tables into jarvis-kotlin's SQLite. SubsystemInput's `wiki: String` for Dietician = concatenated `wiki/recipes/*.md + wiki/prefs/*.md + wiki/body/*.md` snippets relevant to query (separate from jarvis tutor wiki).
10. Tailscale ACL stays — both jarvis-web and Dietician backend run on VPS, both on Tailscale, but only Dietician-tagged clients reach Dietician backend.

## Upstream proposal (defer until needed)

Add optional structured slot to `SubsystemOutput`:

```kotlin
data class SubsystemOutput(
    val text: String,
    val wikiEntry: String? = null,
    val structured: Map<String, Any>? = null,    // NEW, default null → existing subsystems unchanged
)
```

`ChatTools` can route on `structured != null`. Additive, no breaking changes. Until merged: `state/meal-plan-current.json` IS the structured channel — jarvis side reads file at known path.

## Verified at brainstorm time (2026-05-17)

- `Subsystem.kt:8` defines the interface
- `Subsystems.kt:5` is the registration list
- `ClaudeMaxLlm.kt` shells out to `claude --print` CLI (verified by reading source) — Dietician's ClaudeMax wrapper uses identical pattern
- `OpenRouterChatLlm.kt` exists for OpenRouter routing
- `SubsystemInput` does NOT carry pantry/budget/profile state — Dietician owns its own storage post-merge. Confirmed intended.
- jarvis-web runs on VPS port 8080, Dietician backend will run on port 8081 (Tailscale-bound, no nginx route). No collision.
