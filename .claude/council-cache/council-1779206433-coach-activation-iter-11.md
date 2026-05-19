# Council review — 1779206433

**Problem:** CoachChat UI ships with `StubLlmStream` returning a static "AI coach offline" message (`shared/.../ui/di/UiModule.kt:89`). Goal: activate real Coach with per-call audit (GDPR Art 13 disclosure) + budget enforcement + PII redaction + EN/RO locale. Server keys (`OPENROUTER_API_KEY` + `GROQ_API_KEY`) verified live (OpenRouter Gemini 2.0 Flash + Groq Llama 3.1 8B Instant both 200). Both Android + Desktop need Coach.

**Proposed approaches:**

- **A — Client-direct `LlmRouterStream`**: swap `StubLlmStream` → real `LlmRouterStream` in `UiModule.kt`. Keys client-side (desktop env / Android EncryptedSharedPreferences / BYOK-fetched from server). Providers called direct from client. Audit + budget via new HTTP routes `/audit/append` + `/budget/consume`.
- **B — Server-side `/coach/stream` SSE route**: new Ktor route uses the existing server-side `LlmRouter` (already wired post-key-set, 25 Koin defs). Client `LlmStream` = thin SSE consumer. Keys server-only. Audit + budget in-process with the LLM call. ClaudeMax CLI absent server-side.
- **C — Hybrid**: desktop = A (so ClaudeMax CLI works), Android = B.
- **D — Drop ClaudeMax from Coach** + both platforms = B.

**Project context:**
Personal AI dietician for one user (Victor). KMP Compose Android + Windows Desktop + VPS Ktor server + Postgres 16 + pgvector + Tailscale (`100.101.47.77:8081`). LOCAL-FIRST writes + event-sourced ledger. Plan-2 `:shared:llm` LlmRouter fully built client-side (provider sealed iface, RC3/RC4 chains, IdempotencyCache, BudgetLedger, AuditLogSink, PiiRedactor). Plan-3 server LlmRouter also wired (`LlmRouterFactory` + `LlmModule`, 25 Koin defs post-keys). Plan-4-5 UI shipped. ClaudeMax CLI = desktop-only subprocess (`claude --bare -p`). Audit `audit_log` V018. Budget `consume_or_fail` V019. VPS heap caps 128m/384m/512m; 2.1 Gi available headroom; MC at 4G is OOMScoreAdjust-protected co-tenant.

**Timestamp:** 2026-05-19T~14:00Z

---

## 🔴 Devil's Advocate

```
AGENT: Devil's Advocate
STANCE: REJECT
REASONING: Approach A is a security disaster — distributing OPENROUTER_API_KEY + GROQ_API_KEY to an Android APK means the keys leak the moment the device is rooted, decompiled, or backed up; EncryptedSharedPreferences is not a HSM and "manual entry" is a UX joke for the user who explicitly does not want friction. The per-call BYOK fetch is even worse: you've just built a key-vending oracle that a stolen Tailscale auth-key turns into unlimited free credits on Victor's bill, with no rate-limit moat because the budget enforcement also lives client-side and is trivially bypassed by skipping the `/budget/consume` call. Hybrid C inherits A's entire blast radius on desktop while doubling the code surface (two streaming impls, two audit paths, two failure modes) for one user — that is the worst possible trade. D is the correct choice but the framing hides the real failure of B-as-proposed: putting ClaudeMax in the spec chain "as no-op on server" silently violates spec §7 routing semantics and will rot — either amend the spec to remove ClaudeMax from `VICTOR_DESKTOP_TEXT` for Coach explicitly, or build the desktop-local ClaudeMax fall-through as a first-class server-coordinated handoff (server returns a "route locally via ClaudeMax" sentinel, desktop honors it, audit row still written server-side via callback). Anything else is drift wearing a spec costume.
KEY CONCERN: Approach A and Hybrid C both ship long-lived provider API keys to a mobile binary and decentralize budget enforcement — REJECT both outright; B is acceptable ONLY if the ClaudeMax-on-desktop question is resolved by spec amendment (drop from Coach chain) or by a server-coordinated local-route handoff with server-side audit, not by silently no-op'ing the spec.
```

## 📚 Domain Expert

```
AGENT: Domain Expert
STANCE: CONDITIONAL (lean B with carve-out for ClaudeMax CLI matching C's desktop branch)
REASONING: Production AI clients split cleanly along the BYOK axis: ChatGPT/Claude.ai mobile are pure server-routed SSE (zero keys on device, audit + Stripe-style budget atomic in the same Postgres tx as the provider call via `consume_or_fail` SELECT…FOR UPDATE — exactly your V019 pattern); Cursor/Continue.dev/Cline are BYOK-direct because the user owns the keys and wants to bypass middlemen, with audit being best-effort fire-and-forget. The canonical reference architecture here is **LiteLLM proxy** or **Cloudflare AI Gateway** in front of Anthropic/OpenAI/Gemini — both are server-side fan-out with SSE passthrough, unified audit, and budget caps enforced atomically before the upstream call (Gateway's "rate limiting" and LiteLLM's `max_budget` work this way). Approach A breaks atomicity (audit-then-call-then-budget across an HTTP boundary on a flaky Tailscale link = double-bill + missing Art 13 disclosure rows on partial failures, the exact failure mode LiteLLM was built to eliminate) and leaks the keys-live-on-server invariant Plan-3 already locked in. SSE over WebSocket is correct for unidirectional token streaming (OpenAI Responses API, Anthropic Messages streaming, and Vercel AI SDK all default to SSE because it auto-reconnects, traverses corporate proxies, and needs no framing protocol) — WS only wins when you need client→server mid-stream interrupts, which Coach doesn't. ClaudeMax CLI is the genuine exception: it's a desktop-bound subprocess wrapper around an interactive session that cannot be proxied through the VPS, so the C-hybrid carve-out (Desktop calls `ClaudeMaxCliProvider` locally, everything else through `/coach/stream`) is the only honest pattern — Continue.dev does the same thing with local Ollama (direct) vs cloud providers (proxy-eligible).
KEY CONCERN: B-with-ClaudeMax-carve-out means the client's `LlmRouterStream` must still exist on Desktop with provider selection logic that routes ClaudeMax local and everything else to `/coach/stream` — do NOT delete `:shared:llm` router; expose it through a `CoachLlmGateway` interface with two impls (`ServerSseGateway` for Android + Desktop-non-Claude, `LocalRouterGateway` only for Desktop ClaudeMax CLI calls), otherwise you'll dual-maintain provider chains on both sides and the RC3/RC4 fallback logic will drift.
```

## ⚙️ Pragmatist

```
AGENT: Pragmatist
STANCE: CONDITIONAL
REASONING: For a solo-dev single-user app, D is the cheapest to maintain by a wide margin — one code path, one place to set a breakpoint, one rotate-key operation, and audit/budget land in the same process as the LLM call (no extra HTTP routes, no client-side ledger sync bugs). C looks "best of both" but is the worst trap: doubled SSE consumers, doubled redaction logic, doubled budget enforcement (or a race between them), platform-divergent failure modes, and you debug every Coach bug twice. ClaudeMax-via-CLI on desktop sounds nice but is a desktop-only quirk that breaks symmetry; if it's load-bearing, expose it via a tiny local `127.0.0.1` desktop sidecar that the unified `/coach/stream` consumer can hit instead of forking the whole flow. PII-up-wire is a real concern but Tailscale + single-user + server-side redaction-before-log mitigates it; the maintenance cost of A's distributed audit/budget over N devices is the bigger ongoing tax. Offline-mode for a Coach that needs an internet LLM anyway is a non-feature — degrade gracefully, don't architect for it.
KEY CONCERN: C (hybrid) is the seductive wrong answer — it doubles every surface (SSE, redaction, audit, budget, tests, debug path) for one developer maintaining one user's app; pick D and treat ClaudeMax as an optional desktop-local sidecar, not an architectural fork.
```

## 🧱 First Principles

```
AGENT: First Principles
STANCE: WRONG-PROBLEM
REASONING: The real goal is "Victor types into Coach, gets a streamed reply, and the system has a tamper-resistant record of every LLM call." The A/B/C/D dichotomy is a transport-layer distraction that inherits a deeper category error: an LlmRouter exists on BOTH client and server with parallel-but-not-identical IdempotencyCache + BudgetLedger + AuditLogSink + PiiRedactor surfaces, which means every "wire it up" choice silently picks which router is canonical and orphans the other. The actual physics constraints are (a) ClaudeMax CLI MUST run on desktop (subprocess to local binary — non-negotiable), (b) Android has no local CLI so SOMETHING must proxy, and (c) the event-sourced ledger + `/sync/push` pipe is already the canonical write path for every other domain — Coach turns are just another event class (`coach_turn_started`, `coach_chunk_appended`, `coach_turn_completed`) that replays into `audit_log` server-side, making per-call same-tx audit a non-requirement (eventually-consistent via the ledger you already built is the correct primitive). The from-scratch design: client-side LlmRouter is the ONLY router, ClaudeMax stays desktop-local, Android's ClaudeMax provider is a thin HTTPS adapter to a server endpoint that just shells out (key-secrecy preserved, no router duplication), and every turn emits ledger events that `/sync/push` drains into `audit_log` — server "router" becomes a CLI-shim, not a parallel stack. Compared to A/B/C/D: this is closest to C-hybrid but rejects the framing that server needs a full LlmRouter — server is just a CLI-subprocess endpoint + ledger drain, which collapses the duplication that all four options inherit.
KEY CONCERN: Dual LlmRouter (client + server) with parallel idempotency/budget/audit/PII surfaces is a category error — every A/B/C/D option locks in which copy is canonical without addressing why two exist; the event ledger already solves the audit-completeness physics, so the server's job is CLI-proxy + ledger drain, not a second router.
```

## ⚠️ Risk Analyst

```
AGENT: Risk Analyst
STANCE: CONDITIONAL
REASONING: CRITICAL risk is the audit-gap in A/C: client crashes between provider response and HTTP `/audit/append` POST leave a GDPR Art 13 violation with no recovery path — the LLM call already happened, money was spent, the user was disclosed-to per policy, but the legally-required audit row never lands. B atomically writes audit + budget in the same Postgres tx as `consume_or_fail` (V018+V019 already shipped), making this a non-issue. HIGH risk is VPS OOM under B: 512M cap with MC at 4G protected, 475Mi free, SSE streams + in-process router + Hikari pool — but this is bounded (single user, max 2 concurrent streams, ktor-cio handles SSE cheaply) and recoverable via OOMScoreAdjust=500 + restart, vs. the audit-gap which is unrecoverable and regulator-visible. Budget double-count from 2-device concurrent streams is solved server-side by `consume_or_fail` row-lock; client-side it requires a distributed lock the spec doesn't have. ClaudeMax-on-desktop is a real loss under B/D but Coach is a chat surface where OpenRouter/Anthropic API parity is fine — vision OCR (the actual ClaudeMax-primary surface) is unaffected.
KEY CONCERN: Audit-gap in A/C is CRITICAL and unrecoverable (GDPR Art 13); pick B with a streaming-token-budget check (decrement on each chunk inside tx, abort stream on overage) and a 90s SSE idle-timeout + heartbeat to dodge ktor-cio buffering. D only if Coach truly needs ClaudeMax parity, which it doesn't.
```

---

## Sanity Check

```
SANITY [Devil's Advocate]: PASS — clean. Stance REJECT consistent with persona, named concrete failure modes (key exfil, BYOK-vending oracle, hybrid-doubles-surfaces, no-op-spec-drift) without overreach.
SANITY [Domain Expert]: PASS — named LiteLLM, Cloudflare AI Gateway, ChatGPT/Claude.ai mobile, Cursor/Continue.dev/Cline, OpenAI Responses API, Vercel AI SDK, SELECT…FOR UPDATE. Concrete comparisons, no vague best-practices appeals.
SANITY [Pragmatist]: PASS — clean. Named delta costs (doubled SSE consumers, doubled tests, key-rotation cost), proposed 127.0.0.1 sidecar concretely.
SANITY [First Principles]: PASS — actively rejected the user's framing as instructed, named the dual-router category error and the ledger-as-audit-primitive substitute.
SANITY [Risk Analyst]: PASS — ranked CRITICAL/HIGH explicitly, concrete failure modes (mid-call client crash, VPS OOM at 512M cap, SSE idle-timeout), proposed mitigations.
```

---

## Judge

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: FLAWED

CORE FINDING:
A and C are dead — they ship long-lived provider keys to a mobile binary
AND open an unrecoverable GDPR Art 13 audit-gap (client-crash between
provider response and HTTP audit POST). B is the right transport, but the
A-vs-B framing hides the real binding question: where does ClaudeMax CLI
live, and is the audit row truly required atomically with the LLM call.
Pick **D-with-server-side-router**: server `/coach/stream` SSE consumes
the existing in-process `LlmRouter`, ClaudeMax is dropped from the Coach
chain entirely (kept for Vision OCR only per spec §7 amendment), and audit
+ budget land in the same Postgres tx as the provider call. ClaudeMax
parity for Coach is genuinely not load-bearing — Coach is a chat surface
where OpenRouter (Gemini 2.0 Flash, Claude 3.5 Sonnet via OpenRouter)
gives full text capability; ClaudeMax stays Vision-OCR-primary as the
spec already prescribes.

AGENT CONSENSUS: 1 REJECT, 3 CONDITIONAL (all lean B/D), 1 WRONG-PROBLEM — 0 agents flagged.

KEY ISSUES:
- (CRITICAL) A/C audit-gap on partial network failure is unrecoverable
  Art 13 violation. B's `consume_or_fail` + `audit_log` in same tx
  (V018 + V019 already shipped) closes this. Non-negotiable.
- (HIGH) A/C ship keys to mobile + decentralize budget — bypass-by-skip-
  HTTP. B keeps the keys-server-only invariant Plan-3 locked in.
- (HIGH) ClaudeMax CLI in Coach forces a hybrid that doubles every
  surface for ~$2-5/month of saved Max-20x credit usage. Drop from Coach
  chain via explicit spec §7 amendment; preserve in Vision OCR chain.
- (MEDIUM) First Principles' dual-LlmRouter category error is real but
  the rewrite cost exceeds this slice; revisit when a second-router pain
  point surfaces (e.g., RC3/RC4 chain drift between client and server).
  This slice picks server-side router as canonical for Coach; client
  router becomes Vision-OCR + Plan-7 corpus-only.
- (MEDIUM) SSE under ktor-cio needs an explicit 90s idle-timeout + ~25s
  heartbeat ping to dodge proxy buffering and detect dead streams; without
  it, half-closed connections will hang ViewModel state.

RECOMMENDED PATH:
Plan iter-11 around **D + server-side router + Risk's mitigations**:

1. **Spec §7 amendment** (commit before plan): ClaudeMax CLI moves
   from `VICTOR_DESKTOP_TEXT` chain to Vision-OCR-only chain. Coach
   text chain = OpenRouter → Groq fallback.
2. **Server route** `POST /coach/stream`: SSE response, request body
   `{message, locale, idempotencyKey}`. Auth = standard Plan-3 session
   cookie. Wraps existing `LlmRouter.routeStream(...)` from the
   server-side router (post-key-set 25 Koin defs).
3. **Atomicity**: open Postgres tx → `consume_or_fail` row-lock →
   insert pending `audit_log` row → stream provider response →
   update audit_log row with response metadata + token usage →
   commit tx. On provider failure mid-stream, tx rollback releases
   budget reservation; pending audit row stays as failure record.
4. **Streaming-token-budget**: token-count each chunk server-side,
   abort stream + commit partial audit row if cumulative exceeds the
   per-turn soft cap (configurable, default e.g. 4096 tokens).
5. **Idle-timeout + heartbeat**: ktor-cio SSE response has explicit
   90s idle-timeout, ~25s server-sent `:heartbeat\n\n` keepalive.
6. **PII redaction server-side**: `PiiRedactor` from `:shared:llm`
   runs on the `message` BEFORE the provider call. Server-side
   redaction also catches the case where the client forgets to
   redact (defense in depth).
7. **Client wire-up**: new `HttpCoachLlmStream` impl of `LlmStream`
   in `shared/.../net/HttpCoachLlmStream.kt` consuming SSE via ktor
   client. Swap `single<LlmStream> { StubLlmStream() }` →
   `single<LlmStream> { HttpCoachLlmStream(get(), get()) }` in
   `UiModule.kt:89`. Remove `StubLlmStream` private class.
8. **EN/RO locale**: server holds two system-prompt variants; `locale`
   field from client picks which. CoachChatScreen Art 13 disclosure
   copy already EN+RO from Plan-4-5.
9. **Tailscale-disconnected path** (spec RC16): client surfaces
   "Coach unavailable — Tailscale required" banner when
   `TailnetReachability` reports disconnected. Already wired Plan-4-5.
10. **Tests**: integration test against Testcontainer-backed server
    asserting (a) audit_log row written on success, (b) budget row
    debited via consume_or_fail, (c) provider mock failure mid-stream
    rolls back the tx, (d) idle-timeout fires on a stalled mock
    provider.

Plan-2-Vision-OCR + Plan-7-corpus retain the client-side router as-is;
those paths legitimately need ClaudeMax CLI desktop-local + ONNX/Ollama
embeddings client-local respectively. No router code is deleted.

CONFIDENCE: 8
What would move this to 9-10:
- Confirm with user that Max-20x credit not being used for Coach
  is acceptable cost (saving ClaudeMax for Vision OCR only).
- Decide whether the V018 `audit_log` row should write BEFORE or
  AFTER the LLM call response for true Art 13 atomicity — current
  recommendation is pending-row-before + completed-update-after,
  which gives Art 13 disclosure-tracking even on provider crash.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```
