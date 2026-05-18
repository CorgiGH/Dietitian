package com.dietician.shared.llm.provider

import com.dietician.shared.llm.LlmRequest
import com.dietician.shared.llm.LlmResponse
import com.dietician.shared.llm.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Real `claude` CLI subprocess wrapper — Plan-2 Task 16.
 *
 * Spawns `<binaryPath> --bare -p --stream-json` once and keeps stdin/stdout open across calls
 * (warm-pool reuse). Each [send] writes a single JSON-encoded prompt line to stdin, reads
 * lines from stdout until the `message_stop` event, and parses via [ClaudeMaxStreamParser].
 *
 * Windows-hang note (Plan-2 Risk M14): we use `redirectErrorStream(true)` so stderr is folded
 * into stdout — otherwise a chatty stderr can fill the OS pipe buffer and the writer side
 * blocks indefinitely. Timeout enforcement happens in [ClaudeMaxCliProvider.call] via
 * `withTimeout` — this class does NOT block forever on read by itself; if the stream parser
 * runs out of input before `message_stop` it returns the accumulated text with FinishReason.STOP
 * (best-effort).
 */
class ClaudeMaxProcess internal constructor(
    private val process: Process,
    private val parser: ClaudeMaxStreamParser = ClaudeMaxStreamParser(),
) : SpawnedProcess {
    private val writer: BufferedWriter = BufferedWriter(
        OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8),
    )
    private val reader: BufferedReader = BufferedReader(
        InputStreamReader(process.inputStream, StandardCharsets.UTF_8),
    )

    override val isAlive: Boolean
        get() = process.isAlive

    override suspend fun send(request: LlmRequest, model: String): LlmResponse = withContext(Dispatchers.IO) {
        val prompt = encodePrompt(request)
        writer.write(prompt)
        writer.newLine()
        writer.flush()
        val sb = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            sb.append(line).append('\n')
            if (line.contains("\"type\":\"message_stop\"")) break
        }
        parser.parse(sb.toString(), model)
    }

    override fun close() {
        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { process.destroyForcibly() }
    }

    private fun encodePrompt(request: LlmRequest): String =
        request.messages.filter { it.role == Role.USER }
            .joinToString("\n") { it.content }
}

/**
 * Production [ProcessSpawner] — launches the real `claude` binary. Uses `ProcessBuilder`
 * with `redirectErrorStream(true)` to defuse the Windows pipe-buffer hang.
 */
class ProcessSpawnerImpl(private val binaryPath: String) : ProcessSpawner {
    override fun spawn(args: List<String>): SpawnedProcess {
        val pb = ProcessBuilder(listOf(binaryPath) + args)
            .redirectErrorStream(true)
        val proc = pb.start()
        return ClaudeMaxProcess(proc)
    }
}
