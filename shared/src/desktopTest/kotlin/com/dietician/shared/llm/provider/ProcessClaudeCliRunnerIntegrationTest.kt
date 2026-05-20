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
 * `claude`-binary end-to-end check is the manual live drill in a later task.
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
