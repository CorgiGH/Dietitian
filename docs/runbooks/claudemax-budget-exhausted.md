# Runbook 2 — ClaudeMax Agent SDK budget exhausted

**Symptom:** `/diag` shows ClaudeMax budget at 100%. Vision OCR requests stalling or routing through Gemini Vision fallback.

**Cause:**
- $200/mo Agent SDK credit (Max 20x tier, post-June-15-2026 split) consumed by `claude --print` daemon calls
- Monthly reset hasn't fired yet (resets per Anthropic billing cycle)

**User action:**
1. Verify in `/diag`: ClaudeMax budget shows exact `used_cents / ceiling_cents`.
2. Confirm reset date: check Anthropic account billing page. Reset is typically on subscription anniversary date.
3. Options:
   - **Wait for reset:** queued Vision auto-routes to Gemini Vision until ClaudeMax bucket refills. Cheaper, slightly higher numeric hallucination — manual review queue catches anomalies.
   - **Enable "extra usage" on Anthropic account:** SDK spills to API rates after $200, billed per-token. Higher accuracy maintained but cost grows.
   - **Manual rate-limit Dietician daemon:** lower `flyer Vision pages / week` cap in `state/llm-router.toml` to spread budget further.

**Where Dietician handles this automatically:**
- Council 3 BREAK #5 fix: two-phase budget reserve + queue-time provider re-eval
- When `claudemax-sdk used_cents / ceiling_cents > 0.95`, queued Vision jobs auto-route to OpenRouter Gemini Vision regardless of chain order
- 80% threshold = yellow alert in `/diag`; 95% = red + auto-fallback

**Prevention:**
- Weekly review of `/diag` LLM budget meter
- Cap most expensive workloads (flyer Vision) on a per-week budget within the $200 envelope
- Use OpenRouter for non-critical Vision (e.g., bulk-recipe-screenshot ingestion) to leave ClaudeMax for high-stakes (price-extraction)
