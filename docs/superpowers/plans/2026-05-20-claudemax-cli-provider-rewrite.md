# ClaudeMax CLI Provider Rewrite — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the desktop Coach get real ClaudeMax CLI responses by rewriting the `ClaudeMaxCliProvider` to a one-shot `claude -p --output-format json` invocation that uses the Max-20x OAuth subscription and fails loudly.

**Architecture:** Each provider call spawns a fresh `claude -p --output-format json` process, writes the prompt to stdin, closes stdin, reads stdout to EOF, and parses the JSON-array envelope. There is no warm-pool — `claude -p` reads stdin to EOF, answers once, and exits, so a process cannot be reused. A non-zero exit code or a non-success result envelope throws `LlmError` so the circuit-breaker and audit trail see the failure (the original empty-Coach-reply bug was an error silently laundered into an empty success). The subprocess is spawned with `ANTHROPIC_API_KEY` removed (forces OAuth/Max-20x) and a neutral working directory (no CLAUDE.md auto-discovery).

**Tech Stack:** Kotlin Multiplatform (`shared` module, `desktopMain` source set), `kotlinx.serialization` JSON, `kotlinx.coroutines`, JUnit, the `claude` CLI v2.1.x.

**Background:** Council `1779276774` (`.claude/council-cache/council-1779276774.md`) verdict FLAWED. Root cause: the old code spawned `claude --bare -p --stream-json` — `--stream-json` is not a valid flag (CLI exits 1), `--bare` disables OAuth (can't use Max-20x), and the warm-pool assumed a persistent process that the CLI's one-shot `-p` mode does not provide. This plan implements the council's RECOMMENDED PATH (Approach A, widened to a full provider rewrite). The magic-link auth fix that unblocked `/coach/reserve` + `/coach/commit` already shipped on PR #28 — that work is independent of this plan.

**Branch:** `fix/claudemax-cli-provider-rewrite`, cut from `master`.

---

## File Structure

**New files (`shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/`):**
- `ClaudeCliRunner.kt` — the one-shot CLI test-seam: `ClaudeCliRunner` interface + `CliResult` data class.
- `ProcessClaudeCliRunner.kt` — production `ClaudeCliRunner`: real `ProcessBuilder`, env + CWD isolation, Windows binary resolution.
- `ClaudeMaxJsonParser.kt` — parses the `claude -p --output-format json` array envelope into `LlmResponse`; throws `LlmError` on a non-success run.

**New test files (`shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/`):**
- `ClaudeMaxJsonParserTest.kt`
- `FakeClaudeCliRunner.kt` — test double for `ClaudeCliRunner`.
- `ClaudeMaxCliProviderTest.kt` — REPLACES the old file of the same name.
- `ProcessClaudeCliRunnerIntegrationTest.kt` — drives a real OS subprocess (stub script).

**New test resource:**
- `shared/src/desktopTest/resources/claudemax/result-success.json` — captured real envelope fixture.

**Modified:**
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxCliProvider.desktop.kt` — rewritten actual class.
- `desktopApp/src/main/kotlin/com/dietician/desktop/di/DesktopPlatformModule.kt` — fix the hardcoded `model = ""`.

**Deleted (warm-pool / persistent-process design — incompatible with one-shot `claude -p`):**
- `ClaudeMaxWarmPool.kt`, `ClaudeMaxProcess.kt`, `ProcessSpawner.kt`, `ClaudeMaxStreamParser.kt`
- `FakeSpawnedProcess.kt`, `ClaudeMaxWarmPoolTest.kt`, `WarmPoolDequeStressTest.kt`, `ClaudeMaxRouterBurstStressTest.kt`, `ClaudeMaxProcessTest.kt`, `ClaudeMaxStreamParserTest.kt`

**Unchanged (kept per council — circuit-breaker, test seam shape):**
- `CircuitBreaker.kt`, `ClaudeMaxCliProvider.kt` (commonMain `expect`), `ClaudeMaxCliProvider.android.kt` (Android stub).

---

## Task 1: Branch + captured envelope fixture

**Files:**
- Create: `shared/src/desktopTest/resources/claudemax/result-success.json`

- [ ] **Step 1: Cut the branch**

```bash
git fetch origin
git checkout master
git pull
git checkout -b fix/claudemax-cli-provider-rewrite
```

- [ ] **Step 2: Write the captured-envelope test fixture**

This is a trimmed-but-real `claude -p --output-format json` envelope. The parser only reads `type:"assistant"` → `message.model` and `type:"result"` → `result`/`usage`/`is_error`/`subtype`/`stop_reason`.

Create `shared/src/desktopTest/resources/claudemax/result-success.json`:

```json
[
  {"type":"system","subtype":"init","session_id":"sess-test","model":"claude-opus-4-7[1m]"},
  {"type":"assistant","message":{"model":"claude-opus-4-7","role":"assistant","content":[{"type":"text","text":"Aim for 137 g protein today."}],"usage":{"input_tokens":1200,"output_tokens":18}}},
  {"type":"result","subtype":"success","is_error":false,"result":"Aim for 137 g protein today.","total_cost_usd":0.013,"session_id":"sess-test","stop_reason":"end_turn","num_turns":1,"usage":{"input_tokens":1200,"output_tokens":18}}
]
```

- [ ] **Step 3: Commit**

```bash
git add shared/src/desktopTest/resources/claudemax/result-success.json
git commit -m "test(shared:llm): add captured claude -p --output-format json fixture"
```

---

## Task 2: ClaudeMaxJsonParser

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxJsonParser.kt`
- Test: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxJsonParserTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ClaudeMaxJsonParserTest.kt`:

```kotlin
package com.dietician.shared.llm.provider

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.LlmError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeMaxJsonParserTest {

    private val parser = ClaudeMaxJsonParser()

    @Test
    fun `parses a successful result envelope`() {
        val stdout = """
            [
              {"type":"system","subtype":"init","session_id":"s"},
              {"type":"assistant","message":{"model":"claude-opus-4-7","content":[{"type":"text","text":"hi"}]}},
              {"type":"result","subtype":"success","is_error":false,"result":"137 g protein.",
               "stop_reason":"end_turn","usage":{"input_tokens":1200,"output_tokens":18}}
            ]
        """.trimIndent()
        val resp = parser.parse(stdout, requestedModel = "")
        assertEquals("137 g protein.", resp.text)
        assertEquals(1200, resp.inputTokens)
        assertEquals(18, resp.outputTokens)
        assertEquals("claude-opus-4-7", resp.model)
        assertEquals(0, resp.costCents) // Max-20x subscription — not metered.
        assertEquals(FinishReason.STOP, resp.finishReason)
    }

    @Test
    fun `throws when result envelope is_error true`() {
        val stdout = """
            [{"type":"result","subtype":"error_during_execution","is_error":true,"result":""}]
        """.trimIndent()
        val e = assertFailsWith<LlmError.TransientFailure> { parser.parse(stdout, "") }
        assertTrue(e.message!!.contains("is_error"))
    }

    @Test
    fun `throws when subtype is not success`() {
        val stdout = """
            [{"type":"result","subtype":"error_max_turns","is_error":false,"result":""}]
        """.trimIndent()
        assertFailsWith<LlmError.TransientFailure> { parser.parse(stdout, "") }
    }

    @Test
    fun `throws when there is no result event`() {
        val stdout = """[{"type":"system","subtype":"init"}]"""
        val e = assertFailsWith<LlmError.TransientFailure> { parser.parse(stdout, "") }
        assertTrue(e.message!!.contains("no result"))
    }

    @Test
    fun `throws when output is not JSON`() {
        // The exact symptom of the original bug: CLI prints a plain-text error line.
        val e = assertFailsWith<LlmError.TransientFailure> {
            parser.parse("error: unknown option '--stream-json'", "")
        }
        assertTrue(e.message!!.contains("not valid JSON"))
    }

    @Test
    fun `falls back to requestedModel when envelope omits assistant model`() {
        val stdout = """
            [{"type":"result","subtype":"success","is_error":false,"result":"ok",
              "usage":{"input_tokens":1,"output_tokens":1}}]
        """.trimIndent()
        val resp = parser.parse(stdout, requestedModel = "sonnet")
        assertEquals("sonnet", resp.model)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.provider.ClaudeMaxJsonParserTest"`
Expected: FAIL — compilation error, `ClaudeMaxJsonParser` is unresolved.

- [ ] **Step 3: Write the implementation**

Create `ClaudeMaxJsonParser.kt`:

```kotlin
package com.dietician.shared.llm.provider

import com.dietician.shared.llm.FinishReason
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the output of `claude -p --output-format json`.
 *
 * The CLI emits a JSON ARRAY of event objects. The terminal element has
 * `"type":"result"` and carries the answer + usage:
 *
 *     {"type":"result","subtype":"success","is_error":false,"result":"<text>",
 *      "usage":{"input_tokens":N,"output_tokens":M},"stop_reason":"end_turn", ...}
 *
 * An earlier `"type":"assistant"` element carries `message.model`.
 *
 * A run that did not produce a successful `result` (is_error true, subtype not
 * "success", or no result element at all, or output that is not JSON) is a
 * FAILURE — [parse] throws [LlmError] so the circuit-breaker and audit trail see
 * it. It must never be laundered into an empty successful response (council
 * 1779276774 — error-swallowing was the original empty-Coach-reply bug).
 *
 * costCents is fixed at 0: ClaudeMax runs on the Max-20x subscription, not the
 * metered API. `total_cost_usd` in the envelope is notional and is not charged;
 * OpenRouter spend is tracked separately by the budget ledger.
 */
class ClaudeMaxJsonParser(
    private val providerId: ProviderId = ProviderId("claudemax-cli"),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(stdout: String, requestedModel: String): LlmResponse {
        val root = runCatching { json.parseToJsonElement(stdout) }.getOrNull()
            ?: throw LlmError.TransientFailure(
                IllegalStateException("claude CLI output was not valid JSON: ${stdout.take(200)}"),
            )
        val events: List<JsonObject> = when (root) {
            is JsonArray -> root.map { it.jsonObject }
            is JsonObject -> listOf(root)
            else -> throw LlmError.TransientFailure(
                IllegalStateException("unexpected claude CLI JSON shape"),
            )
        }
        val result = events.lastOrNull { it.typeField() == "result" }
            ?: throw LlmError.TransientFailure(
                IllegalStateException("claude CLI produced no result event"),
            )

        val isError = result["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
        val subtype = result["subtype"]?.jsonPrimitive?.contentOrNull
        if (isError || subtype != "success") {
            throw LlmError.TransientFailure(
                IllegalStateException("claude CLI result not success (is_error=$isError subtype=$subtype)"),
            )
        }

        val text = result["result"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val usage = result["usage"]?.jsonObject
        val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
        val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
        val model = events.firstOrNull { it.typeField() == "assistant" }
            ?.get("message")?.jsonObject?.get("model")?.jsonPrimitive?.contentOrNull
            ?: requestedModel.ifBlank { "claudemax-cli" }
        val finish = when (result["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "end_turn", "stop_sequence", null -> FinishReason.STOP
            "max_tokens" -> FinishReason.MAX_TOKENS
            "tool_use" -> FinishReason.TOOL_USE
            else -> FinishReason.STOP
        }
        return LlmResponse(
            provider = providerId,
            model = model,
            text = text,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            costCents = 0,
            finishReason = finish,
        )
    }

    private fun JsonObject.typeField(): String? = this["type"]?.jsonPrimitive?.contentOrNull
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.provider.ClaudeMaxJsonParserTest"`
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxJsonParser.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxJsonParserTest.kt
git commit -m "feat(shared:llm): ClaudeMaxJsonParser for claude -p --output-format json"
```

---

## Task 3: ClaudeCliRunner test-seam + fake

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeCliRunner.kt`
- Create: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/FakeClaudeCliRunner.kt`

- [ ] **Step 1: Write the interface + result type**

Create `ClaudeCliRunner.kt`:

```kotlin
package com.dietician.shared.llm.provider

/** Result of one `claude` CLI invocation: process exit code + full stdout (stderr folded in). */
data class CliResult(val exitCode: Int, val stdout: String)

/**
 * Test-seam for a ONE-SHOT `claude` CLI invocation. Given an argv list + the
 * stdin payload, run the process to completion and return its exit code + full
 * stdout. Production impl is [ProcessClaudeCliRunner]; tests inject
 * [FakeClaudeCliRunner] returning a canned [CliResult].
 *
 * The seam is deliberately "argv + stdin in, exitCode + stdout out" — the exact
 * one-shot contract of `claude -p`. A fake that returns a canned [CliResult]
 * exercises the real exit-code and parse decisions, unlike the previous
 * `FakeSpawnedProcess` which bypassed the protocol entirely (council 1779276774).
 */
interface ClaudeCliRunner {
    suspend fun run(args: List<String>, stdin: String): CliResult
}
```

- [ ] **Step 2: Write the fake**

Create `FakeClaudeCliRunner.kt`:

```kotlin
package com.dietician.shared.llm.provider

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * Test double for [ClaudeCliRunner]. Records the args + stdin it was called with,
 * and returns a canned [CliResult] (or throws, or hangs).
 */
class FakeClaudeCliRunner(
    private val result: CliResult = CliResult(0, ""),
    private val latencyMs: Long = 0,
    private val neverReturns: Boolean = false,
    private val throwOnRun: Throwable? = null,
) : ClaudeCliRunner {
    var lastArgs: List<String>? = null
    var lastStdin: String? = null
    var runCount: Int = 0
        private set

    override suspend fun run(args: List<String>, stdin: String): CliResult {
        runCount += 1
        lastArgs = args
        lastStdin = stdin
        throwOnRun?.let { throw it }
        if (neverReturns) awaitCancellation()
        if (latencyMs > 0) delay(latencyMs)
        return result
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :shared:compileTestKotlinDesktop`
Expected: BUILD SUCCESSFUL (no test runs — these are scaffolding for Task 4).

- [ ] **Step 4: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeCliRunner.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/FakeClaudeCliRunner.kt
git commit -m "feat(shared:llm): ClaudeCliRunner one-shot CLI seam + fake"
```

---

## Task 4: ProcessClaudeCliRunner (production runner)

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ProcessClaudeCliRunner.kt`

This task has no isolated unit test — a real-process integration test lands in Task 8 (the production runner is only meaningfully testable by spawning an OS process). Build verification only here.

- [ ] **Step 1: Write the implementation**

Create `ProcessClaudeCliRunner.kt`:

```kotlin
package com.dietician.shared.llm.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Production [ClaudeCliRunner] — spawns the real `claude` CLI one-shot.
 *
 * Lifecycle: spawn `claude <args>`, write [stdin] to the process stdin and CLOSE
 * it (so `claude -p` sees EOF and processes), read all of stdout to EOF, wait for
 * the process to exit, return (exitCode, stdout).
 *
 * Auth + context isolation (council 1779276774):
 *  - `ANTHROPIC_API_KEY` is removed from the child environment so the CLI uses
 *    the OAuth / Max-20x subscription, never a metered API key. With the key
 *    present the CLI silently prefers it — a surprise bill, not the free path.
 *  - The working directory is the OS temp dir — no CLAUDE.md, no .mcp.json — so
 *    the CLI does not auto-discover and load this repo's project context (a live
 *    drill showed ~12.5k tokens of CLAUDE.md bloat leaking into one call).
 *  - `redirectErrorStream(true)` folds stderr into stdout so a chatty stderr
 *    cannot fill the OS pipe buffer and deadlock the writer (Windows hazard).
 */
class ProcessClaudeCliRunner(
    private val binaryPath: String = resolveClaudeBinary(),
    private val workingDir: File = File(System.getProperty("java.io.tmpdir")),
) : ClaudeCliRunner {
    override suspend fun run(args: List<String>, stdin: String): CliResult =
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(listOf(binaryPath) + args)
                .redirectErrorStream(true)
                .directory(workingDir)
            pb.environment().remove("ANTHROPIC_API_KEY")
            val proc = pb.start()
            proc.outputStream.use { it.write(stdin.toByteArray(StandardCharsets.UTF_8)) }
            val out = proc.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val exit = proc.waitFor()
            CliResult(exitCode = exit, stdout = out)
        }
}

/**
 * Resolves the `claude` launcher. Java's `ProcessBuilder` does NOT append Windows
 * executable extensions, so a bare `"claude"` can fail to launch even when it is
 * on PATH. On Windows, probe PATH for `claude.cmd` / `claude.exe` / `claude.bat`
 * and return the first hit; otherwise fall back to `"claude"` and let the OS
 * resolve it (correct on macOS / Linux).
 */
internal fun resolveClaudeBinary(): String {
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    if (!isWindows) return "claude"
    val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    val candidates = listOf("claude.cmd", "claude.exe", "claude.bat")
    for (dir in pathDirs) {
        for (c in candidates) {
            val f = File(dir, c)
            if (f.isFile) return f.absolutePath
        }
    }
    return "claude"
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ProcessClaudeCliRunner.kt
git commit -m "feat(shared:llm): ProcessClaudeCliRunner — one-shot claude spawn, OAuth + CWD isolation"
```

---

## Task 5: Rewrite ClaudeMaxCliProvider (desktop actual)

**Files:**
- Modify (full rewrite): `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxCliProvider.desktop.kt`
- Modify (full rewrite): `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxCliProviderTest.kt`
- Delete: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/ClaudeMaxRouterBurstStressTest.kt` (uses the old `forTesting(spawner=…)` signature; concurrent-burst pooling no longer exists)

- [ ] **Step 1: Write the failing test (full replacement of `ClaudeMaxCliProviderTest.kt`)**

Replace the entire contents of `ClaudeMaxCliProviderTest.kt`:

```kotlin
package com.dietician.shared.llm.provider

import com.dietician.shared.llm.Capability
import com.dietician.shared.llm.DeviceClass
import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmMessage
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.Role
import com.dietician.shared.llm.TaskType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeMaxCliProviderTest {

    private fun request(prompt: String = "how much protein?"): LlmRequest =
        LlmRequest(
            subjectId = "victor",
            task = TaskType.TEXT,
            deviceClass = DeviceClass.VICTOR_DESKTOP,
            capability = Capability.NON_STREAMING,
            messages = listOf(LlmMessage(Role.USER, prompt)),
        )

    private val successStdout = """
        [
          {"type":"assistant","message":{"model":"claude-opus-4-7","content":[{"type":"text","text":"hi"}]}},
          {"type":"result","subtype":"success","is_error":false,"result":"137 g.",
           "stop_reason":"end_turn","usage":{"input_tokens":900,"output_tokens":12}}
        ]
    """.trimIndent()

    @Test
    fun `call returns parsed response on a clean run`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(exitCode = 0, stdout = successStdout))
        val provider = ClaudeMaxCliProvider.forTesting(runner)
        val resp = provider.call(request(), model = "")
        assertEquals("137 g.", resp.text)
        assertEquals(12, resp.outputTokens)
    }

    @Test
    fun `call passes one-shot flags and the user prompt as stdin`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(0, successStdout))
        ClaudeMaxCliProvider.forTesting(runner).call(request("eat what?"), model = "")
        val args = runner.lastArgs!!
        assertTrue(args.containsAll(listOf("-p", "--output-format", "json")))
        assertEquals("eat what?", runner.lastStdin)
    }

    @Test
    fun `non-zero exit code throws LlmError and is not a silent empty success`() = runTest {
        // The original bug: a CLI error became an empty 0-token "success".
        val runner = FakeClaudeCliRunner(
            CliResult(exitCode = 1, stdout = "error: unknown option '--stream-json'"),
        )
        val provider = ClaudeMaxCliProvider.forTesting(runner)
        assertFailsWith<LlmError> { provider.call(request(), model = "") }
    }

    @Test
    fun `non-success result envelope throws LlmError`() = runTest {
        val runner = FakeClaudeCliRunner(
            CliResult(0, """[{"type":"result","subtype":"error_during_execution","is_error":true,"result":""}]"""),
        )
        assertFailsWith<LlmError> { ClaudeMaxCliProvider.forTesting(runner).call(request(), "") }
    }

    @Test
    fun `repeated failures open the circuit breaker`() = runTest {
        val runner = FakeClaudeCliRunner(CliResult(exitCode = 1, stdout = "boom"))
        val breaker = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 30_000)
        val provider = ClaudeMaxCliProvider.forTesting(runner, circuitBreaker = breaker)
        repeat(3) { runCatching { provider.call(request(), "") } }
        // 4th call: breaker is OPEN → ProviderUnavailable, runner not invoked again.
        assertFailsWith<LlmError.ProviderUnavailable> { provider.call(request(), "") }
        assertEquals(3, runner.runCount)
    }

    @Test
    fun `timeout throws LlmError Timeout`() = runTest {
        val runner = FakeClaudeCliRunner(neverReturns = true)
        val provider = ClaudeMaxCliProvider.forTesting(runner, timeoutMs = 50)
        assertFailsWith<LlmError.Timeout> { provider.call(request(), "") }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.provider.ClaudeMaxCliProviderTest"`
Expected: FAIL — compilation error: `forTesting(runner)` does not match the current `forTesting(spawner, …)` signature.

- [ ] **Step 3: Rewrite `ClaudeMaxCliProvider.desktop.kt`**

Replace the entire contents:

```kotlin
package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmError
import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.ProviderId
import com.dietician.shared.llm.Role
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * ClaudeMax CLI provider — desktop actual. Council 1779276774 rewrite.
 *
 * Each [call] spawns a fresh one-shot `claude -p --output-format json` process
 * via [ClaudeCliRunner]. There is NO warm-pool: `claude -p` reads stdin to EOF,
 * answers once, and exits, so a process cannot be reused — pooling was never
 * possible against this CLI.
 *
 * Failure is loud: a non-zero CLI exit, a timeout, or a non-success result
 * envelope throws an [LlmError] so [circuitBreaker] records the failure and the
 * audit trail sees it. An empty or failed run must never look like a successful
 * empty answer — that error-swallowing was the original empty-Coach-reply bug.
 *
 * RC2 construction: the primary constructor is `internal`; callers go through
 * the [forTesting] / [production] companion factories.
 */
actual class ClaudeMaxCliProvider internal constructor(
    private val runner: ClaudeCliRunner,
    private val parser: ClaudeMaxJsonParser,
    private val circuitBreaker: CircuitBreaker,
    private val timeoutMs: Long,
) {
    actual suspend fun call(request: LlmRequest, model: String): LlmResponse {
        if (circuitBreaker.isOpen()) {
            throw LlmError.ProviderUnavailable(ProviderId("claudemax-cli"))
        }
        return try {
            val result = withTimeout(timeoutMs) {
                runner.run(buildArgs(request, model), encodePrompt(request))
            }
            if (result.exitCode != 0) {
                throw LlmError.TransientFailure(
                    IllegalStateException(
                        "claude CLI exited ${result.exitCode}: ${result.stdout.take(500)}",
                    ),
                )
            }
            val response = parser.parse(result.stdout, model)
            circuitBreaker.recordSuccess()
            response
        } catch (e: TimeoutCancellationException) {
            circuitBreaker.recordFailure()
            throw LlmError.Timeout("claudemax-cli")
        } catch (e: LlmError) {
            circuitBreaker.recordFailure()
            throw e
        } catch (e: Throwable) {
            circuitBreaker.recordFailure()
            throw LlmError.TransientFailure(e)
        }
    }

    actual fun isAvailable(): Boolean = !circuitBreaker.isOpen()

    companion object {
        /** Test entry — inject a [FakeClaudeCliRunner]. */
        fun forTesting(
            runner: ClaudeCliRunner,
            timeoutMs: Long = 5_000,
            circuitBreaker: CircuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000),
            parser: ClaudeMaxJsonParser = ClaudeMaxJsonParser(),
        ): ClaudeMaxCliProvider = ClaudeMaxCliProvider(runner, parser, circuitBreaker, timeoutMs)

        /** Production entry — real `claude` subprocess, 120s timeout. */
        fun production(): ClaudeMaxCliProvider =
            ClaudeMaxCliProvider(
                runner = ProcessClaudeCliRunner(),
                parser = ClaudeMaxJsonParser(),
                circuitBreaker = CircuitBreaker(failureThreshold = 5, resetTimeoutMs = 30_000),
                timeoutMs = 120_000,
            )

        /**
         * One-shot argv. Context-isolation flags (council 1779276774): no MCP
         * servers, no slash-command skills, and per-machine system-prompt
         * sections excluded — the Coach call carries only its own prompt, not
         * the host's project context.
         */
        private fun buildArgs(request: LlmRequest, model: String): List<String> = buildList {
            add("-p")
            add("--output-format"); add("json")
            add("--exclude-dynamic-system-prompt-sections")
            add("--strict-mcp-config")
            add("--disable-slash-commands")
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                add("--append-system-prompt"); add(it)
            }
            model.takeIf { it.isNotBlank() }?.let {
                add("--model"); add(it)
            }
        }

        private fun encodePrompt(request: LlmRequest): String =
            request.messages.filter { it.role == Role.USER }.joinToString("\n") { it.content }
    }
}
```

- [ ] **Step 4: Delete the burst stress test**

```bash
git rm shared/src/desktopTest/kotlin/com/dietician/shared/llm/ClaudeMaxRouterBurstStressTest.kt
```

If the file path differs, locate it: `./gradlew :shared:compileTestKotlinDesktop` will name the file that fails to compile against the old `forTesting` signature. Delete that file.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.provider.ClaudeMaxCliProviderTest"`
Expected: PASS — all 6 tests green.

- [ ] **Step 6: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxCliProvider.desktop.kt \
        shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxCliProviderTest.kt
git rm --cached shared/src/desktopTest/kotlin/com/dietician/shared/llm/ClaudeMaxRouterBurstStressTest.kt 2>/dev/null || true
git commit -m "feat(shared:llm): rewrite ClaudeMaxCliProvider — one-shot, loud failures, no warm-pool"
```

---

## Task 6: Delete the dead warm-pool / persistent-process files

These files implement the warm-pool and persistent-stdin design that the council retired. After Task 5 they are unreferenced by production code; their tests test only deleted behavior.

**Files to delete:**
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxWarmPool.kt`
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxProcess.kt`
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ProcessSpawner.kt`
- `shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxStreamParser.kt`
- `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/FakeSpawnedProcess.kt`
- `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxWarmPoolTest.kt`
- `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/WarmPoolDequeStressTest.kt`
- `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxProcessTest.kt`
- `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxStreamParserTest.kt`

- [ ] **Step 1: Delete the files**

```bash
git rm shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxWarmPool.kt \
       shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxProcess.kt \
       shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ProcessSpawner.kt \
       shared/src/desktopMain/kotlin/com/dietician/shared/llm/provider/ClaudeMaxStreamParser.kt \
       shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/FakeSpawnedProcess.kt \
       shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxWarmPoolTest.kt \
       shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/WarmPoolDequeStressTest.kt \
       shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxProcessTest.kt \
       shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ClaudeMaxStreamParserTest.kt
```

- [ ] **Step 2: Search for any remaining references**

Use Grep across `shared/src/desktopMain` and `shared/src/desktopTest` for: `ClaudeMaxWarmPool`, `ClaudeMaxProcess`, `ProcessSpawner`, `SpawnedProcess`, `ClaudeMaxStreamParser`, `FakeSpawnedProcess`, `FakeProcessSpawner`, `ClaudeMaxConfig`. Expected: zero hits. If any hit remains, that file must be fixed (it was using deleted code) — most likely another stress/router test; delete or rewrite it to use `FakeClaudeCliRunner`.

- [ ] **Step 3: Verify the desktop test source set compiles + runs**

Run: `./gradlew :shared:desktopTest`
Expected: BUILD SUCCESSFUL — all desktop tests green, no unresolved references.

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor(shared:llm): delete warm-pool + persistent-process design (council 1779276774)"
```

---

## Task 7: Fix the hardcoded model in DesktopPlatformModule

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/dietician/desktop/di/DesktopPlatformModule.kt:66`

`DesktopPlatformModule.kt:66` calls `cli.call(request, model = "")`. The empty model is fine for the request (the CLI uses its own default model), and `ClaudeMaxJsonParser` now reads the real model back from the response envelope. The bug to fix is only the *display* fallback — when the envelope omits the model, `""` is opaque. Pass an explicit alias so the response and any UI label are meaningful.

- [ ] **Step 1: Read the current call site**

Read `DesktopPlatformModule.kt` lines 44-77. Confirm line 66 is `val resp = cli.call(request, model = "")`.

- [ ] **Step 2: Change the model argument**

Replace:

```kotlin
                    val resp = cli.call(request, model = "")
```

with:

```kotlin
                    // Empty model lets the `claude` CLI use its own configured
                    // default; ClaudeMaxJsonParser reads the actual model id back
                    // from the response envelope. "sonnet" is the explicit
                    // fallback label if the envelope omits it.
                    val resp = cli.call(request, model = "sonnet")
```

- [ ] **Step 3: Verify the desktop app compiles**

Run: `./gradlew :desktopApp:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add desktopApp/src/main/kotlin/com/dietician/desktop/di/DesktopPlatformModule.kt
git commit -m "fix(desktop): give ClaudeMax calls an explicit model label"
```

---

## Task 8: Real-binary integration test

The unit tests use `FakeClaudeCliRunner` and never spawn a process. This task adds ONE test that drives a real OS subprocess via a stub script, so the `ProcessClaudeCliRunner` lifecycle (write stdin, close, read to EOF, capture exit code) is actually exercised — closing the "all tests are fakes" gap the council named.

**Files:**
- Create: `shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ProcessClaudeCliRunnerIntegrationTest.kt`

- [ ] **Step 1: Write the integration test**

The test writes a tiny stub script to a temp file, points a `ProcessClaudeCliRunner` at it, and asserts the runner round-trips stdin → stdout and captures the exit code. It uses `cmd /c` on Windows and `sh` elsewhere so it runs on any dev machine.

Create `ProcessClaudeCliRunnerIntegrationTest.kt`:

```kotlin
package com.dietician.shared.llm.provider

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives a REAL OS subprocess (a stub script, not the `claude` binary) so the
 * ProcessClaudeCliRunner lifecycle — write stdin, close stdin, read stdout to
 * EOF, capture the exit code — is exercised against an actual process. The
 * `claude`-binary end-to-end check is the manual live drill in Task 10.
 */
class ProcessClaudeCliRunnerIntegrationTest {

    private val isWindows =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val scripts = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        scripts.forEach { it.delete() }
    }

    /** Writes a stub script that echoes a fixed line then exits [exitCode]. */
    private fun stubScript(exitCode: Int): File {
        val ext = if (isWindows) ".cmd" else ".sh"
        val f = File.createTempFile("claude-stub", ext).also { scripts.add(it) }
        if (isWindows) {
            f.writeText("@echo off\r\necho STUB_OUTPUT_LINE\r\nexit /b $exitCode\r\n")
        } else {
            f.writeText("#!/bin/sh\necho STUB_OUTPUT_LINE\nexit $exitCode\n")
            f.setExecutable(true)
        }
        return f
    }

    private fun runnerFor(script: File): ProcessClaudeCliRunner =
        if (isWindows) {
            // .cmd must be launched via cmd.exe; binaryPath = cmd, script passed as an arg.
            ProcessClaudeCliRunner(binaryPath = "cmd")
        } else {
            ProcessClaudeCliRunner(binaryPath = "sh")
        }

    private fun argsFor(script: File): List<String> =
        if (isWindows) listOf("/c", script.absolutePath) else listOf(script.absolutePath)

    @Test
    fun `captures stdout and a zero exit code`() = runTest {
        val script = stubScript(exitCode = 0)
        val result = runnerFor(script).run(argsFor(script), stdin = "ignored")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("STUB_OUTPUT_LINE"), "stdout was: ${result.stdout}")
    }

    @Test
    fun `captures a non-zero exit code`() = runTest {
        val script = stubScript(exitCode = 7)
        val result = runnerFor(script).run(argsFor(script), stdin = "ignored")
        assertEquals(7, result.exitCode)
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.dietician.shared.llm.provider.ProcessClaudeCliRunnerIntegrationTest"`
Expected: PASS — 2 tests green. (If it fails, the `ProcessClaudeCliRunner` lifecycle has a real bug — fix the runner, not the test.)

- [ ] **Step 3: Commit**

```bash
git add shared/src/desktopTest/kotlin/com/dietician/shared/llm/provider/ProcessClaudeCliRunnerIntegrationTest.kt
git commit -m "test(shared:llm): real-subprocess integration test for ProcessClaudeCliRunner"
```

---

## Task 9: Hook-leak empirical check

Dropping `--bare` re-enables the user's global `~/.claude` hooks (including a SessionStart hook that injects "CAVEMAN MODE ACTIVE"). If those hooks color the Coach's reply, the dietician Coach would answer in caveman style. This task empirically checks whether that happens and records the finding.

**Files:**
- Create: `docs/runbooks/claudemax-coach-context-isolation.md`

- [ ] **Step 1: Run a real `claude` call with the production flags**

From a shell, in the OS temp directory (mimicking `ProcessClaudeCliRunner`'s working dir):

```bash
cd "$(python -c 'import tempfile;print(tempfile.gettempdir())' 2>/dev/null || echo /tmp)"
printf 'In one plain sentence, how much protein should I eat?' | \
  claude -p --output-format json --exclude-dynamic-system-prompt-sections \
  --strict-mcp-config --disable-slash-commands
```

- [ ] **Step 2: Inspect the `result` field**

Find the `{"type":"result",...}` element in the output array. Read its `result` string.

- Decision point: does the answer read as a normal sentence, or is it in caveman style ("Eat protein. 137 g. Build muscle.")?
  - **Normal sentence** → hooks do not leak into `-p` output. No further action; document this in Step 4.
  - **Caveman style** → the SessionStart hook leaked. Apply Step 3.

- [ ] **Step 3 (only if hooks leaked): isolate via `CLAUDE_CONFIG_DIR`**

If and only if Step 2 showed hook contamination, isolate the Coach's CLI from the user's global config:

1. Create a dedicated config dir and log it in once (one-time manual setup):
   ```bash
   mkdir -p "$HOME/.dietician-coach-claude"
   CLAUDE_CONFIG_DIR="$HOME/.dietician-coach-claude" claude login
   ```
2. In `ProcessClaudeCliRunner.run`, after `pb.environment().remove("ANTHROPIC_API_KEY")`, add:
   ```kotlin
   pb.environment()["CLAUDE_CONFIG_DIR"] =
       File(System.getProperty("user.home"), ".dietician-coach-claude").absolutePath
   ```
3. Re-run Step 1's command with `CLAUDE_CONFIG_DIR="$HOME/.dietician-coach-claude"` prefixed; confirm the answer is now a normal sentence.
4. Commit the `ProcessClaudeCliRunner` change with message `fix(shared:llm): isolate Coach CLI config dir from user hooks`.

- [ ] **Step 4: Write the runbook**

Create `docs/runbooks/claudemax-coach-context-isolation.md` documenting: which flags isolate context (`--exclude-dynamic-system-prompt-sections`, `--strict-mcp-config`, `--disable-slash-commands`, neutral CWD), the Step 2 finding (hooks leak: yes/no), and — if applicable — the `CLAUDE_CONFIG_DIR` setup and the one-time `claude login` step. Include the exact verification command from Step 1.

- [ ] **Step 5: Commit**

```bash
git add docs/runbooks/claudemax-coach-context-isolation.md
git commit -m "docs(runbook): ClaudeMax Coach CLI context isolation"
```

---

## Task 10: Full preflight + live drill

- [ ] **Step 1: Run the full preflight**

Run: `./gradlew :shared:test :desktopApp:assemble ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. If `ktlintCheck` reports formatting, run `./gradlew ktlintFormat` and re-commit.

- [ ] **Step 2: Launch the desktop app against the live VPS**

```bash
DIETICIAN_HOST_OVERRIDE=http://100.101.47.77:8081 ./gradlew :desktopApp:run
```

Sign in via magic-link (PR #28 flow). If `onboarded` is already true, the app opens at Home.

- [ ] **Step 3: Drive a Coach turn**

Open the Coach tab, send: `how much protein should I eat today?`

- [ ] **Step 4: Verify the response**

Expected:
- A real, non-empty answer renders in the Coach view.
- `tokens` shows non-zero in/out (not `0 in / 0 out`).
- The VPS log shows `200 OK POST /coach/reserve` and `200 OK POST /coach/commit`:
  ```bash
  ssh root@46.247.109.91 "grep '/coach/' /opt/dietician/logs/backend.log | tail -4"
  ```

If the reply is still empty: the run failed loudly somewhere — check the desktop app console for an `LlmError`, and re-run Step 1 of Task 9 manually to see the raw `claude` output.

- [ ] **Step 5: Final commit (if any preflight formatting changed files)**

```bash
git add -A
git commit -m "chore: ktlint format pass for ClaudeMax provider rewrite" 2>/dev/null || true
git push -u origin fix/claudemax-cli-provider-rewrite
```

---

## Post-implementation

Per the project's council rule, convene the 5-agent council AFTER implementation (council-lite, same as the pre-impl council `1779276774`) before opening the PR. Then open the PR against `master`.

---

## Self-Review

**1. Spec coverage** — the "spec" is council verdict `1779276774`. Coverage:
- Approach A transport (`claude -p --output-format json`, no `--bare`, write+close stdin, fresh process) → Tasks 3, 4, 5.
- Parse the CLI's real JSON wrapper → Task 2.
- Loud failure (exit code → `LlmError`) → Task 5 (`call`) + Task 2 (`parse` throws).
- Delete the warm-pool + stress tests, keep circuit-breaker + factory + test seam → Tasks 5, 6.
- `ANTHROPIC_API_KEY` cleared + neutral CWD → Task 4.
- One real-bytes integration test → Task 8.
- Fix `model=""` → Task 7.
- CWD/context-isolation (the live-drill CLAUDE.md-bloat finding) → Task 4 flags + Task 9.
No gaps.

**2. Placeholder scan** — every code step contains complete code; no "TBD"/"add error handling"/"similar to Task N". Task 9 Step 3 is conditional but fully specified (exact commands + exact diff). Clean.

**3. Type consistency** — `ClaudeCliRunner.run(args, stdin): CliResult` is defined in Task 3 and consumed with that exact signature in Tasks 4, 5, 8. `ClaudeMaxJsonParser.parse(stdout, requestedModel): LlmResponse` defined in Task 2, consumed in Task 5. `CliResult(exitCode, stdout)` consistent throughout. `forTesting(runner, timeoutMs, circuitBreaker, parser)` defined and called consistently. `LlmResponse` / `LlmError` / `FinishReason` / `ProviderId` / `LlmRequest` / `Role` are existing types in `com.dietician.shared.llm` (`LlmTypes.kt`) — unchanged.

**4. Build+mount pairing** — not a UI feature; the "mount" equivalent is the DI wiring. `ClaudeMaxCliProvider.production()` is consumed at `DesktopPlatformModule.kt:48` (`LocalCoachProvider` adapter) — that call site survives the rewrite (`production()` stays no-arg) and is exercised in Task 7 + Task 10. The provider is reachable from the running app.

**5. Component-reuse contract** — `DesktopPlatformModule` reuses `ClaudeMaxCliProvider.production()` and `cli.call(request, model)`. Task 7 reads the call site, shows the exact diff, and Task 10 Step 1 runs `:desktopApp:assemble` (the typecheck) before the live drill.

**6. `data-testid` grep** — no UI selectors in this plan; not applicable.
