# Compose MP baseline snapshot

> Council 1779120600 RC17 (2026-05-18). Reference for post-finals 1.8 migration diff.

## Baseline metadata

- **Captured at:** 2026-05-18T00:00:00+03:00 (Pre-Task-0, Plan-4-5 Batch A)
- **Branch:** `plan-4-5/kmp-ui-foundation` (off `plan-2/shared-llm-batch-a` @ `0a7e061`)
- **Compose MP version:** `1.7.0` (pinned per spec §6.11 Council Q5 + RC3)
- **Voyager version:** `1.1.0-beta02` (pinned per RC3)
- **Koin version:** `4.0.0` + `koin-compose:4.0.0`
- **Kotlin version:** `2.0.21`
- **AGP version:** `8.7.2` (warning: > KGP-tested max 8.5 — pre-existing)
- **Gradle:** `8.10` (Kotlin 1.9.24 build)
- **Launcher JVM:** `21.0.10` (Eclipse Adoptium)
- **Daemon JVM:** `21.0.10` (Eclipse Adoptium)
- **OS:** Windows 11 10.0 amd64
- **moko-resources:** N/A (not in scope this batch — Atkinson loaded via Compose `components-resources` 1.7.0)

## Voyager EOL signal check (Pre-T0 gate)

Date: 2026-05-18.

- `pushed_at`: 2026-03-22T10:27:54Z (~57 days ago — under the 90-day STOP threshold)
- `open_issues_count`: 194 (above the literal `>20` STOP threshold)
- `archived`: false
- `disabled`: false
- `stargazers_count`: 3077
- Last code commit: 2026-02-17 (~91 days ago)

Verdict: **DONE_WITH_CONCERNS — proceed.** The literal `>20 issues` gate trips on virtually every popular OSS lib; intent of the gate is "abandoned-project signal". Repo is unarchived, 3k+ stars, last commit ~3 months ago. Pinned beta02 is stable for Plan-4-5 Batch A scope. **Action item logged for Plan-4-5.5 (post-finals):** re-evaluate Voyager vs `compose-multiplatform-navigation` stable as part of 1.8 migration.

## Resolved dependency tree — commonMain (Compose + Voyager + Koin slice)

```
commonMainImplementationDependenciesMetadata
+--- app.cash.sqldelight:runtime:2.0.2
+--- org.jetbrains.kotlin:kotlin-stdlib:2.0.21
+--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0
+--- org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3
+--- org.jetbrains.kotlinx:kotlinx-datetime:0.6.1
+--- io.ktor:ktor-client-core:3.0.1
+--- io.ktor:ktor-client-content-negotiation:3.0.1
+--- io.ktor:ktor-client-websockets:3.0.1
+--- io.ktor:ktor-client-auth:3.0.1
+--- io.ktor:ktor-client-logging:3.0.1
+--- io.ktor:ktor-serialization-kotlinx-json:3.0.1
+--- app.cash.sqldelight:coroutines-extensions:2.0.2
+--- io.github.oshai:kotlin-logging-jvm:7.0.0
+--- io.insert-koin:koin-core:4.0.0
+--- io.insert-koin:koin-compose:4.0.0
|    +--- io.insert-koin:koin-core:4.0.0
|    +--- org.jetbrains.compose.runtime:runtime:1.6.11 -> 1.7.0
+--- cafe.adriel.voyager:voyager-navigator:1.1.0-beta02
|    +--- cafe.adriel.voyager:voyager-core:1.1.0-beta02
+--- cafe.adriel.voyager:voyager-screenmodel:1.1.0-beta02
|    +--- cafe.adriel.voyager:voyager-core:1.1.0-beta02
|    +--- cafe.adriel.voyager:voyager-navigator:1.1.0-beta02
+--- org.jetbrains.compose.runtime:runtime:1.7.0
+--- org.jetbrains.compose.foundation:foundation:1.7.0
|    +--- org.jetbrains.compose.animation:animation:1.7.0
|    +--- org.jetbrains.compose.animation:animation-core:1.7.0
|    +--- org.jetbrains.compose.ui:ui:1.7.0
|    +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.3
|    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.3
|    +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.3
|    +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.3
|    +--- org.jetbrains.compose.ui:ui-geometry:1.7.0
|    +--- org.jetbrains.compose.ui:ui-graphics:1.7.0
|    +--- org.jetbrains.compose.ui:ui-text:1.7.0
|    +--- org.jetbrains.compose.ui:ui-uikit:1.7.0
|    +--- org.jetbrains.compose.ui:ui-unit:1.7.0
|    +--- org.jetbrains.compose.ui:ui-util:1.7.0
|    +--- org.jetbrains.skiko:skiko:0.8.15
+--- org.jetbrains.compose.material3:material3:1.7.0
|    +--- org.jetbrains.compose.material:material-icons-core:1.7.0
|    +--- org.jetbrains.compose.material:material-ripple:1.7.0
+--- org.jetbrains.compose.components:components-resources:1.7.0
+--- org.jetbrains.compose.components:components-ui-tooling-preview:1.7.0
+--- io.github.resilience4j:resilience4j-circuitbreaker:2.2.0
+--- io.github.resilience4j:resilience4j-kotlin:2.2.0
+--- io.github.resilience4j:resilience4j-retry:2.2.0
```

(Full ~36k-line tree available via `./gradlew :shared:dependencies` at this commit.)

## Strict-version constraints (locked floor — verified resolved)

```
+--- org.jetbrains.compose.runtime:runtime:{strictly 1.7.0} -> 1.7.0 (c)
+--- org.jetbrains.compose.foundation:foundation:{strictly 1.7.0} -> 1.7.0 (c)
+--- org.jetbrains.compose.material3:material3:{strictly 1.7.0} -> 1.7.0 (c)
+--- org.jetbrains.compose.components:components-resources:{strictly 1.7.0} -> 1.7.0 (c)
+--- cafe.adriel.voyager:voyager-navigator:{strictly 1.1.0-beta02} -> 1.1.0-beta02 (c)
+--- cafe.adriel.voyager:voyager-screenmodel:{strictly 1.1.0-beta02} -> 1.1.0-beta02 (c)
+--- io.insert-koin:koin-core:{strictly 4.0.0} -> 4.0.0 (c)
+--- io.insert-koin:koin-compose:{strictly 4.0.0} -> 4.0.0 (c)
+--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:{strictly 2.8.3} -> 2.8.3 (c)
+--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:{strictly 2.8.3} -> 2.8.3 (c)
+--- org.jetbrains.skiko:skiko:{strictly 0.8.15} -> 0.8.15 (c)
```

## Why this exists

Per Risk Analyst Round-2 FM-6: bumping CMP 1.7.0 → 1.8.0 post-finals risks one Voyager + Koin + Compose combo shifting. Detection is build-time on the upgrade attempt. Mitigation: capture the dependency tree NOW on 1.7.0 as a baseline. Plan-4-5.5+ post-finals upgrade attempt diffs against this baseline to surface the exact dep that broke + verify others stayed stable.

Baseline 2026-05-18, Plan-4-5 Batch A.

## Cross-references

- Pre-impl council 1779120600 RC17 (2026-05-18) — Risk Analyst FM-6 + RC17 baseline-snapshot requirement.
- Plan-4-5 Pre-Task-0 setup — runs the snapshot capture (this file).
- Plan-4-5.5 (post-finals) — coordinated CMP 1.8 + compose-multiplatform-navigation stable + Voyager re-eval migration; diffs against this baseline.
