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
        assertEquals(0, resp.costCents)
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

    @Test
    fun `throws LlmError not a raw exception when array has non-object elements`() {
        val stdout = """[1, "junk", true]"""
        assertFailsWith<LlmError.TransientFailure> { parser.parse(stdout, "") }
    }
}
