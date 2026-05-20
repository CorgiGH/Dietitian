package com.dietician.shared.llm.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

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
 *  - stdin is written in full before stdout is read. That is safe ONLY because
 *    `claude -p` is request-then-response — it consumes its whole prompt before
 *    emitting output — so the single stdout pipe cannot fill mid-write. Do not
 *    reuse this runner for a CLI that streams output while still reading input.
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
            try {
                runInterruptible {
                    proc.outputStream.use { it.write(stdin.toByteArray(StandardCharsets.UTF_8)) }
                    val out = proc.inputStream.readBytes().toString(StandardCharsets.UTF_8)
                    val exit = proc.waitFor()
                    CliResult(exitCode = exit, stdout = out)
                }
            } finally {
                if (proc.isAlive) {
                    proc.destroy()
                    if (!proc.waitFor(2, TimeUnit.SECONDS)) proc.destroyForcibly()
                }
            }
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
