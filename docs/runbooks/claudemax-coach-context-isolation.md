# Runbook — ClaudeMax Coach CLI context isolation

**Scope:** how the desktop Coach's `claude` CLI subprocess is kept isolated from the
host machine's project context, and how to re-verify it. Origin: council
`1779276774` (the ClaudeMax CLI provider rewrite).

## Why this matters

The desktop Coach answers by shelling out to the locally-installed `claude` CLI
(`ProcessClaudeCliRunner` → `claude -p --output-format json`). That CLI, run
normally, auto-discovers a lot of host context: the working directory's
`CLAUDE.md`, `.mcp.json` MCP servers, slash-command skills, and per-machine
system-prompt sections. A live drill showed a bare `claude -p` call pulling
~12.5k tokens of this repo's `CLAUDE.md` into a single trivial prompt. None of
that belongs in a nutrition-coaching answer — it bloats token counts and can
colour the reply.

`--bare` would strip all of it, but `--bare` also disables OAuth/keychain reads,
so it cannot use the Max-20x subscription (the locked free-path). It is therefore
NOT used.

## How isolation is achieved (no `--bare`)

`ClaudeMaxCliProvider.buildArgs` + `ProcessClaudeCliRunner` apply, on every call:

| Mechanism | Effect |
|---|---|
| `--exclude-dynamic-system-prompt-sections` | drops cwd / env / git / memory-path sections from the system prompt |
| `--strict-mcp-config` | loads ONLY `--mcp-config`-provided MCP servers; none are passed, so zero MCP |
| `--disable-slash-commands` | no slash-command skills resolve |
| working directory = OS temp dir | no `CLAUDE.md` and no `.mcp.json` to auto-discover |
| `ANTHROPIC_API_KEY` removed from the child env | forces OAuth / Max-20x; never a metered API key |

## Hook-leak finding (2026-05-20)

Open question at planning time: dropping `--bare` re-enables the host's global
`~/.claude` hooks, including a SessionStart hook that injects a "CAVEMAN MODE"
instruction. If that hook reached the Coach, the dietician would answer in
caveman style.

**Empirically checked** — ran the production flags from the OS temp dir:

```
cd "$TEMP"   # or /tmp on POSIX
printf 'In one plain English sentence, how much protein should I eat daily as a lean-bulking adult?' | \
  claude -p --output-format json --exclude-dynamic-system-prompt-sections \
  --strict-mcp-config --disable-slash-commands
```

The `{"type":"result"}` element's `result` field came back as a normal,
complete English sentence (`subtype: success`) — NOT caveman-styled. **Hooks do
not leak into `claude -p` output.** No further isolation (no dedicated
`CLAUDE_CONFIG_DIR`) is required.

## Re-verification

Re-run the command above if Coach replies ever look wrong (caveman-styled,
truncated, or carrying repo context). Expected: a plain factual answer with
`subtype: success`.

If a future `claude` CLI version DOES start leaking hook output into `-p`
results, isolate via a dedicated config dir:

1. `mkdir -p "$HOME/.dietician-coach-claude"` then
   `CLAUDE_CONFIG_DIR="$HOME/.dietician-coach-claude" claude login` (one-time).
2. In `ProcessClaudeCliRunner.run`, after the `ANTHROPIC_API_KEY` removal, add:
   `pb.environment()["CLAUDE_CONFIG_DIR"] = <that dir>`.
3. Re-run the verification command with `CLAUDE_CONFIG_DIR` prefixed; confirm a
   clean answer.
