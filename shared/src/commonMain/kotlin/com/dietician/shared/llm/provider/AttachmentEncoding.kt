package com.dietician.shared.llm.provider

import com.dietician.shared.llm.AttachmentRef
import com.dietician.shared.llm.LlmAttachment
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Helper used by every HTTP provider (Tasks 10/11/12/13 — OpenRouter, Anthropic, Gemini, Groq)
 * to materialize [LlmAttachment.ref] into a base64-encoded payload for upstream-API vision
 * blocks.
 *
 * RC1 (Council 1779062699 — Plan-2 Task 10/11/12): vision attachments MUST be inlined per
 * provider. The Router (Batch C Task 19) is responsible for resolving [AttachmentRef.FilePath]
 * → [AttachmentRef.Bytes] BEFORE handing the request to a provider, so this helper only
 * encodes the bytes form. [AttachmentRef.Url] is rejected here — Url-form attachments must be
 * fetched + re-wrapped as Bytes by the Router; we never let the provider HTTP layer read from
 * disk or hit the network for attachment fetch (security-boundary cross — Risk Analyst M12).
 *
 * The [readFilePath] hook is provided so desktop integration tests can opt into reading a
 * local file rather than threading the full Router; production Router code always pre-resolves.
 */
object AttachmentEncoding {
    @OptIn(ExperimentalEncodingApi::class)
    fun base64(att: LlmAttachment): String = when (val ref = att.ref) {
        is AttachmentRef.Bytes -> Base64.Default.encode(ref.data)
        is AttachmentRef.FilePath ->
            error(
                "FilePath attachments must be pre-resolved to Bytes by the Router " +
                    "before reaching the provider HTTP layer (path=${ref.path}).",
            )
        is AttachmentRef.Url ->
            error("Url attachments not yet inlined; Router must fetch + re-route as Bytes (url=${ref.url}).")
    }
}
