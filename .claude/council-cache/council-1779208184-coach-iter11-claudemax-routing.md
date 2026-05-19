# Mini-council — 1779208184 — Coach iter-11 ClaudeMax-on-desktop integration

**Question:** Given user locked (a) keep ClaudeMax CLI in Coach chain on Desktop (Max-20x credit), (b) pending-before + update-after audit pattern, pick ONE option from 5 for desktop ClaudeMax integration.

**Options:**
1. **Sidecar** — desktop `127.0.0.1:NNNN` HTTP wrapping `claude --bare -p`. Server `/coach/stream` callback via Tailscale to desktop.
2. **Gateway carve-out** — client-side `CoachLlmGateway` two-impl. Desktop runs ClaudeMax via `:shared:llm`. Audit/budget HTTP-after.
3. **Reverse-tunnel** — desktop persistent WS to server. Server orchestrates ClaudeMax spawn.
4. **2-phase commit** — desktop POST `/coach/reserve` (server pending audit + budget lock) → desktop ClaudeMax local → desktop POST `/coach/commit` with usage. Server timeout 60s.
5. **Ledger-replay** — ClaudeMax local + ledger events; `/sync/push` drain to audit_log. Synchronous turn-start push for Art 13.

---

## 🔴 Devil's Advocate

```
AGENT: Devil's Advocate (mini-council)
STANCE: REJECT-OPTION-5 | CONDITIONAL-OPTION-4
PICK: 4
REASONING: Option 5 is the trap — it sells "local-first ledger reuse" while Art 13 disclosure-before-call forces a synchronous `/sync/push` on every turn-start, which collapses the entire event-sourcing premise into a fake-async round-trip with worse failure modes than just calling the server directly. Option 1 (sidecar) invents a new process + port + lifecycle for a single-user desktop, and Option 3 (reverse-tunnel) puts a long-lived WS and CPU-burning message router on the server for zero gain over a plain HTTP POST. Option 2 has an audit-gap window (desktop crash between LLM response and audit POST) that directly violates the user's locked pending-before pattern. Option 4 is the only one that maps onto the already-locked pending-before/update-after audit pattern and the already-shipped `consume_or_fail` budget primitive — pick it, with the condition that the 60s server-side timeout-to-release is implemented in V020 alongside reserve/commit endpoints, not deferred.
```

## 📚 Domain Expert

```
AGENT: Domain Expert (mini-council)
STANCE: APPROVE-OPTION-4
PICK: 4
REASONING: 2-phase commit is the only option that honors the locked "pending-before + update-after" audit pattern AND keeps the ClaudeMax CLI subprocess on desktop (where Max-20x subscription credit lives) — same shape as Stripe's auth-hold + capture and S3 multipart resumable uploads, both proven for "expensive operation runs offshore, ledger atomicity stays at the system of record." Option 1 (sidecar) inverts trust by exposing desktop HTTP to server via Tailscale callback — Continue.dev's local-Ollama pattern works because Ollama is the source of audit truth, but here the server owns audit_log V018 + consume_or_fail V019, so callback direction is wrong; Option 2 (gateway carve-out) is the explicit audit-gap risk you flagged and violates the locked pattern; Option 3 (reverse-tunnel) is Cloudflare-Tunnel/ngrok shape but adds a persistent WS stateful dependency that breaks when the desktop sleeps — overkill for single-user single-dev; Option 5 (ledger-replay) is the LangSmith/Helicone/OpenLLMetry post-hoc pattern, but every one of those is explicitly eventually-consistent and NONE enforce pre-call budget atomicity, which directly contradicts consume_or_fail V019 being a pre-call gate. Conditional gate on Option 4: 60s server-timeout must auto-refund the budget reservation (saga compensation step) and emit a `coach_turn_orphaned` audit row so a desktop crash mid-stream doesn't silently burn budget — this is the saga-pattern compensation leg that Stripe's hold-expiry implements as auth-reversal.
```

## ⚙️ Pragmatist

```
AGENT: Pragmatist (mini-council)
STANCE: APPROVE-OPTION-2
PICK: 2
REASONING: Gateway carve-out has the smallest new-state-machine surface (zero — it's a plain two-impl interface) and reuses the most existing infrastructure (`:shared:llm` already runs ClaudeMax CLI subprocess via `ClaudeMaxLlm.kt`, server `LlmRouter` already wired, audit_log V018 already shipped). Debug breakpoint is trivial: one place in `CoachLlmGateway.Desktop` for the local call, one HTTP POST `/audit/after` for the receipt — Victor reads two stack frames, not a connection state machine. Options 1/3/4 add new network surface (sidecar port, WS reconnection, 2-phase pending rows + cleanup job) that costs solo-dev sleep when it flakes at 2am; Option 5 forces synchronous `/sync/push` per turn-start to honor Art 13 timing which inverts the local-first write topology Council 4 locked. Conditional only on idempotency-key being client-generated UUIDv4 per turn so the audit-after retry is safe — otherwise APPROVE flat.
```

## 🧱 First Principles

```
AGENT: First Principles (mini-council)
STANCE: WRONG-FRAMING
PICK: 5
REASONING: The framing treats Coach audit as a special primitive when the locked architecture already says "event-sourced ledger + /sync/push is THE canonical write path for every other domain." Coach should not be the exception — audit_log should be a materialized view (projection) of ledger events of kind `llm.call.pending` / `llm.call.completed`, exactly like pantry/weight/macros. Art 13 pending-before is satisfied by a synchronous ledger append + /sync/push BEFORE the subprocess fires (push is cheap over Tailscale, ~50ms; the LLM call is 5-30s, so push latency is in the noise); budget enforcement is a guard read against the projection, not a row-lock holding across the subprocess (consume_or_fail was solving a concurrency problem that does not exist at n=1 user with desktop+phone — the phone is server-routed and the desktop is the only ClaudeMax caller, so a simple "read projection, append pending, check cap, fire, append completed" sequence is race-free). Options 1-4 all invent new wire shapes (sidecar HTTP, gateway carve-out, reverse-tunnel, 2PC) to preserve a Coach-specific audit primitive that should not exist — Option 5 deletes the special case and makes ClaudeMax-on-desktop look like every other write in the app.
```

## ⚠️ Risk Analyst

```
AGENT: Risk Analyst (mini-council)
STANCE: CONDITIONAL-OPTION-2
PICK: 2
REASONING: Severity ranking by worst-case blast radius: Opt 3 (catastrophic — WS-disconnect mid-stream creates ambiguous double-bill state on a paid CLI quota, plus server-memory pressure against the 512M cap with MC as protected co-tenant) > Opt 1 (high — silent unreachable on 127.0.0.1 misbind + stale-process quota drain with zero UI signal; failure mode is invisible until quota exhausted) > Opt 4 (high-operational — orphan "pending" rows are a permanent Art 13 incompleteness vector and the timeout state-machine is net-new infra to monitor) > Opt 5 (medium — synchronous /sync/push on every turn-start blocks UI on Tailscale latency, and replay-derived audit is new code surface for AI-Act-critical records) > Opt 2 (lowest — single failure mode is desktop crash between LLM response and audit POST, which is bounded, recoverable, and already mitigated by SqlDelight idempotency-key cache + consume_or_fail V019 row-lock). Opt 2 keeps server memory flat (no per-connection state), keeps the audit path on a simple retry-with-idempotency contract that V018/V019 were literally built for, and the Art 13 gap is closeable with a local "audit-pending" outbox table draining on next desktop start. CONDITIONS: (a) idempotency-key persisted in SqlDelight BEFORE ClaudeMax invocation, not after; (b) audit-pending outbox table with replay-on-startup, drained against `/audit/upsert` using the same idempotency key; (c) pending-row written server-side via HTTP-before (not HTTP-after) so the Art 13 turn-start record exists even if desktop dies mid-call — the "update-after" half is what retries safely.
```

---

## Sanity check

```
SANITY [Devil's Advocate]: PASS — concrete attacks on each option, stance committed.
SANITY [Domain Expert]: PASS — named Stripe auth-hold + capture, S3 multipart, Continue.dev local Ollama, Cloudflare Tunnel, ngrok, LangSmith/Helicone/OpenLLMetry. All concrete.
SANITY [Pragmatist]: PASS — concrete debug-cost + reuse argument. Single condition is precise.
SANITY [First Principles]: PASS — rejected user's framing as instructed. Specific physics claim about Tailscale latency vs LLM latency.
SANITY [Risk Analyst]: PASS — severity ranking explicit. Conditions (a)(b)(c) reframe option 2 toward option 4; flag in judge synthesis.
```

---

## Judge

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MINI-COUNCIL VERDICT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

VERDICT: OPTION 4 (2-phase commit) with mitigations

CORE FINDING:
Real consensus on Option 4 once Risk Analyst's conditions are unpacked.
Risk's conditional-OPTION-2 with condition (c) "pending-row written
server-side via HTTP-before" IS option 4 by another name — the
distinction between options 2 and 4 collapses when you require pre-call
server-side pending row, leaving only the "do you call it reserve or
audit-pending?" naming question. Devil + Domain Expert + Risk-conditioned
converge. Pragmatist's pure-Option-2 is the lone defector and would
reintroduce the audit-gap the user explicitly locked out.

AGENT CONSENSUS: 3 effective-4 votes (Devil + Domain Expert + Risk-with-
condition-c), 1 pure-2 vote (Pragmatist), 1 WRONG-FRAMING-5 vote
(First Principles). 0 flagged.

KEY ISSUES:
- (CRITICAL) Saga compensation. 60s server-side timeout MUST release the
  budget reservation AND flip the audit row from "pending" to
  "coach_turn_orphaned" status. Without this, every desktop crash mid-
  ClaudeMax becomes a permanent audit_log incompleteness and a budget
  leak. Stripe auth-reversal pattern.
- (HIGH) Idempotency-key = client-generated UUIDv4 per turn, persisted
  to SqlDelight BEFORE ClaudeMax invocation. Same key for reserve and
  commit. Server dedupes on the key.
- (HIGH) Audit-pending outbox. Local SqlDelight table records reserves
  that haven't received commit ACK. Replay on desktop startup, re-POST
  `/coach/commit` with the persisted idempotency-key. Server is
  idempotent on commit. Covers desktop-crash-mid-call.
- (MEDIUM) Android phone routes through `/coach/stream` SSE which
  internally calls the same reserve+commit pair (server is both
  caller + callee for non-ClaudeMax flows). Single audit code path
  on the server.
- (DEFERRED) First Principles' ledger-projection refactor of audit_log.
  Bigger architectural shift. Note as known debt; revisit when Plan-7
  corpus ingestion needs LLM audit or when a second audit-correctness
  pain surfaces.

RECOMMENDED PATH:
Plan iter-11 = OPTION 4 (2-phase commit) with these endpoints + flows:

Server (Plan-3 extension):
- POST /coach/reserve
    request: { idempotencyKey: UUID, prompt: String, locale: "en"|"ro",
                provider: ProviderId, subjectId: UUID }
    server: PiiRedactor on prompt → consume_or_fail row-lock budget →
            insert audit_log row status="pending" with redacted_prompt
            + provider + locale → return { reservationId, auditId }
    response: 200 { reservationId: UUID, auditId: UUID } OR
              402 { reason: "over_budget", capUsd, spentUsd }
- POST /coach/commit
    request: { idempotencyKey: UUID, status: "success"|"failed"|"aborted",
                usage: { promptTokens, completionTokens, costMicros,
                         provider, latencyMs },
                responseHash: String (sha256 of redacted response) }
    server: dedupe via idempotencyKey → finalize budget consumption →
            update audit_log row status=status + usage + responseHash →
            return { auditId }
    response: 200 { auditId } (idempotent — same response on retry)
- POST /coach/stream (Android + server-routed Desktop fallback)
    SSE response. Server internally calls reserve → LlmRouter →
    commit in same coroutine scope; client sees only chunk stream.
    Heartbeat every 25s, idle timeout 90s.
- Cleanup cron: every 30s scan audit_log for status="pending" where
  installed_on < now() - 60s → flip to "orphaned" + release
  budget reservation (saga compensation).

Client (Plan-4-5 wiring):
- New CoachLlmGateway interface in :shared:llm:
    suspend fun streamCoachTurn(prompt: String, locale: Locale):
        Flow<LlmChunk>
- Desktop impl uses :shared:llm Router for ClaudeMax provider:
    1. Generate idempotencyKey = UUIDv4
    2. Persist to SqlDelight audit_pending_outbox table
    3. POST /coach/reserve → get reservationId
    4. Run ClaudeMaxCliProvider.stream() locally → emit chunks to UI
    5. POST /coach/commit with usage → mark outbox row complete
    6. On desktop startup, replay any outbox rows where commit not ACK'd
- Android impl + Desktop-non-ClaudeMax: thin SSE consumer on
  /coach/stream; server handles reserve+commit internally.
- Replace single<LlmStream> { StubLlmStream() } in UiModule.kt:89
  → single<LlmStream> { CoachLlmGatewayLlmStream(get()) } where
  get() resolves to platform-specific CoachLlmGateway.

New tables (Plan-3 V022):
- audit_log status enum extended: pending, success, failed, aborted, orphaned
- audit_pending_outbox (CLIENT-side SqlDelight only):
    idempotency_key UUID PK, reservation_id UUID, prompt_hash BYTEA,
    started_at TIMESTAMP, last_attempt_at TIMESTAMP, attempts INT

CONFIDENCE: 9
What moves to 10:
- Live smoke verifying 60s orphan-cleanup cron fires on simulated
  desktop crash.
- Confirmation that Android-only fallback (server-routed) gets the
  same idempotency-key contract via internal reserve+commit pair.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Output saved to: `.claude/council-cache/council-1779208184-coach-iter11-claudemax-routing.md`
