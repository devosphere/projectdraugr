# Three-AI Integration — Plan & Contract

> **Project Draugr — Architecture**
>
> *The deterministic world is complete and authoritative. The AIs are an upgrade layer bolted to seams that already exist — never a dependency the game needs to run.*

**Status:** Phase 1 groundwork in progress. **Last updated:** 2026-08-02.

---

## Why this document exists

The deterministic foundation (M1–M5, F1–F8) is done. The three collaborating agents from
[core-agent-boundaries.md](core-agent-boundaries.md) can now be wired in. This is the plan,
the boundaries they must respect, and the runtime contract for the shared model client — so a
future cycle doesn't re-derive the shape or accidentally let an AI become load-bearing.

The three agents, restated with where each attaches:

| Agent | Role | Seam it attaches to | When it runs |
|---|---|---|---|
| **Simulation Agent** | Narrates selected moments in a richer voice | `NarrationRouter` → `PerceptionFrame` → model → refined `perception` | Resolution time, gated hard |
| **Persistent State Architect** | Proposes new data (minerals, recipes, vocabulary) as reviewed migrations | `routing_miss_backlog`, `ArchitectRouter`, V53-style review gate | **Authoring time only** — never in the request path |
| **Persistent State Auditor** | Read-only consistency narration/summaries over committed state | `PersistentStateAuditor` / `AuditSentinel` | Offline / scheduled |

The Architect's placement is settled and non-negotiable — see
[routing-and-coverage-strategy.md](routing-and-coverage-strategy.md). AI belongs at authoring
time, never at resolution time.

---

## The one rule everything else follows from

**The AI is the upgrade layer, not the dependency.** If the model is disabled, unconfigured,
slow, erroring, or refusing, the game must continue at full correctness on deterministic prose.
Concretely, every model call:

- is **gated off by default** (`draugr.ai.enabled=false`) and only runs when a key is present;
- **never throws into the game loop** — the client returns `Optional.empty()` on any failure;
- always has a **deterministic fallback already computed** before the call is even considered
  (the `NarrationEngine` / per-intent prose is the source of truth; the AI only *appends*).

This is why the deterministic layer was built first and stays complete. See
[narration-engine.md](narration-engine.md).

---

## Phased plan

### Phase 1 — Simulation Agent narration (Task #21) — *in progress*

The first and highest-value AI; its seam (`NarrationRouter` + `PerceptionFrame`) is already built.

- **1a. Shared model-client foundation (this commit).** A provider-agnostic `LanguageModel`
  interface, an Anthropic implementation on the official `anthropic-java` SDK, `AiProperties`
  config, and a disabled no-op used when `enabled=false` or no key is set. `SimulationNarrator`
  turns a `PerceptionFrame` + the deterministic prose into a refinement prompt and returns the
  deterministic prose **plus** at most one AI sentence — or the deterministic prose alone on any
  failure. Unit-tested against a stub client (no network, no DB, no key).
- **1b. Wired into `ChronicleActionService.resolve()` — done.** After `perception` is final and
  the frame built, `if (narrationRouter.shouldUseAI(...)) perception = simulationNarrator.refine(frame, perception)`;
  a genuine change is re-persisted (`UPDATE chronicle_action`) and reflected in the returned frame.
  Verified: a HIGH-attention observe (which the router *would* route to AI) resolves unchanged with
  the feature off, and the whole context boots clean.
- **1c. Live verification — pending a key.** With `DRAUGR_AI_ENABLED=true` + `ANTHROPIC_API_KEY`,
  confirm a routed moment (a kill, a death, a HIGH-attention observe) gains a sentence and that
  disabling it is invisible. Needs the operator's key; nothing in the repo handles it.

### Phase 2 — Persistent State Architect (authoring-time) — *groundwork landed*

Drain `routing_miss_backlog` offline: the model (Opus 4.8) proposes a `category_term` for a novel
verb or a `material_process` / `mineral_definition` row for a missing mechanic, emitted **as a
Flyway migration** and passed through the existing V53 review gate before it is canon. Never in the
request path; one call per novel verb/process, ever. Framing in
[world-progression.md](world-progression.md) (Bucket A) and routing-and-coverage-strategy.md.

- **Groundwork (this session):** `PersistentStateArchitect` reads the frequency-ranked backlog
  (`backlog(limit)`) and drafts a migration proposal per gap (`propose(entry)`), keyed on the gap
  kind (VOCABULARY / KEYWORD / SUBJECT / MECHANIC). It **proposes; it never commits** — a proposal
  is text a human puts through the V53 gate. Unit-tested against a stub model (proposes on the
  architect model when enabled, silent when off/declined). Not yet wired to any runner or endpoint.
- **Next:** an offline surface to drive it (an admin-only endpoint or a CLI/scheduled job) that
  presents proposals for review, plus applying a reviewed proposal as a real `V*.sql`.

### Phase 3 — Persistent State Auditor (read-only) — *done*

`AuditorSummarizer` (Sonnet 4.6) turns a read-only `AuditReport` (consistency flag + violated
invariants) into a plain operator summary for the Overseer. Strictly describes; never proposes a
repair/migration/deletion and never writes player-facing prose. Exposed at
`GET /api/audit/summary` (raw `consistent`/`violations` always present; `summary` is null when AI
is off, so the surface works with or without a key). Unit-tested against a stub model.

---

## Reachable surfaces (so all three engines are exercisable)

| Engine | Where it runs | Surface |
|---|---|---|
| Simulation Agent | On the action path, gated by `NarrationRouter` | Any routed action — the refined `perception` |
| Architect | Authoring-time, operator-driven | `GET /api/architect/backlog`, `POST /api/architect/propose-top` (returns a draft; **never applies**) |
| Auditor | Read-only, operator-driven | `GET /api/audit/summary` |

With the feature off, every surface still responds on deterministic data (`summary`/proposal null),
so the mechanism is testable before a key exists and the AI is a pure upgrade when one is set.

**Overseer UI.** The Auditor and Architect are surfaced in the creator Overseer (`?mode=overseer`)
via `OverseerAgents`: an Auditor card (consistency badge + AI prose summary) and an Architect card
(the routing-miss backlog + a "draft a proposal for the worst gap" button that shows the drafted
migration — for review, never applied). Both render on deterministic data with the AI off. The
Simulation Agent needs no separate UI — its output is the narration in the playthrough.

---

## Runtime contract (the shared client)

- **Provider:** Anthropic, via the official `com.anthropic:anthropic-java` SDK (the documented
  default for a Java project). Package `com.devosphere.draugr.ai`.
- **Model:** defaults to `claude-opus-5` (Anthropic's standing recommendation; we do **not**
  silently downgrade). Configurable via `DRAUGR_AI_MODEL`. **Note for operators:** narration
  refinement is high-frequency and low-stakes (one atmospheric sentence). For latency and cost
  you may prefer `claude-haiku-4-5` here — set `DRAUGR_AI_MODEL=claude-haiku-4-5`. This is a
  deliberate operator decision, surfaced rather than made for you.
- **Credentials:** the API key comes only from configuration/environment
  (`ANTHROPIC_API_KEY` / `DRAUGR_AI_API_KEY`). **Never committed, never logged.** The repo ships
  with the feature off; nothing about the key lives in git.
- **Failure policy:** timeout-bounded; any exception, refusal, or blank result → `Optional.empty()`
  → deterministic prose. A model outage degrades voice, never correctness.
- **Cost gate:** `NarrationRouter` decides *before* any call whether a moment is worth one
  (~90% of actions never reach the network). See narration-engine.md § Cost Model.

### Configuration keys (`draugr.ai.*`)

| Key | Env | Default | Meaning |
|---|---|---|---|
| `enabled` | `DRAUGR_AI_ENABLED` | `false` | Master switch. Off ⇒ pure deterministic prose. |
| `api-key` | `DRAUGR_AI_API_KEY` / `ANTHROPIC_API_KEY` | *(empty)* | Anthropic key. Never committed. |
| `model` | `DRAUGR_AI_MODEL` | `claude-opus-5` | Model id. |
| `max-tokens` | `DRAUGR_AI_MAX_TOKENS` | `1024` | Output cap (generous so a short reply is never truncated). |
| `timeout` | `DRAUGR_AI_TIMEOUT` | `20s` | Per-call wall-clock bound. |

**To go live:** set `DRAUGR_AI_ENABLED=true` and export `ANTHROPIC_API_KEY`; restart the backend.
Turn it off by unsetting `DRAUGR_AI_ENABLED` — no code change, no data change.

---

## What would reopen the boundaries

- Moving the Architect to resolution time. Settled no; see routing-and-coverage-strategy.md.
- Letting the Auditor write or repair state. Never — it is read-only by definition.
- Making narration a hard dependency (removing the deterministic fallback). Never.
