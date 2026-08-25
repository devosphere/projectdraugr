# AI Integration — Plan & Contract

> **Project Draugr — Architecture**
>
> *The deterministic world is complete and authoritative. The AIs are an upgrade layer bolted to seams that already exist — never a dependency the game needs to run.*

**Status:** Phase 1 groundwork in progress. **Last updated:** 2026-08-02.

> **⚠ SUPERSEDED/EXTENDED by [DR-0021](../systems/06.3-Decision-Log.md#dr-0021) (2026-08-03).** The
> architecture grew from three AIs to **five runtime roles** — this doc's three (Simulation Agent,
> Persistent State Architect, Persistent State Auditor) plus a **Procedure Interpreter** and a **QA
> Critic** — and the Architect now also authors mechanics **at runtime as per-chronicle scoped data**
> (never canon). The full pipeline design and build are in
> [runtime-procedure-authoring.md](runtime-procedure-authoring.md); the live build state is in
> [systems/06.4-Runtime-Authoring-Build-Plan.md](../systems/06.4-Runtime-Authoring-Build-Plan.md). The
> boundaries and the "AI is an upgrade layer, never a dependency" invariant below still hold verbatim.

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
- **1b. Wired into `ChronicleActionService.resolve()` — done.** The deterministic prose is INSERTed
  once into the append-only `chronicle_action` row (the immutable source of truth). The death coda and,
  when `narrationRouter.shouldUseAI(...)` fires, the Simulation Agent's one refined sentence are added
  afterward and stored in a **separate overlay table** (`chronicle_action_narration`, V65) keyed by
  `action_id` — never a write-back to the base row. The read paths (`narrationHistory`, `journey` →
  archive + PDF, the idempotency replay) `LEFT JOIN` the overlay and `COALESCE(overlay, base)`, so
  history, the archive, and the PDF show exactly the enriched prose the player saw live, falling back to
  the deterministic prose when no overlay exists. The overlay records the `model` id for a later
  narration-quality review. **Ordering is deliberate:** the single paid model call is the last fallible
  step — every operation that can throw a hard persistence error commits-in-transaction *before* a token
  is spent, so a DB failure (or a retry of one) costs nothing. The overlay INSERT that follows targets a
  trigger-free table with an already-satisfied FK, so it cannot raise the immutability error that an
  earlier `UPDATE chronicle_action` did. Hard errors are also recorded to `system_error_log`
  (`GET /api/system/errors`) so a fault can't hide behind its clean HTTP response.
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
- **Surface (this session):** `GET /api/architect/backlog` and `POST /api/architect/propose-top`,
  shown in the Overseer UI (`OverseerAgents`).

#### Applying a proposal (deliberately a human step, not an endpoint)

There is **no runtime "apply".** `core-agent-boundaries.md` is explicit: runtime code may not alter
schemas, and a new domain arrives *only* as a reviewed Flyway migration. An endpoint that executed
an AI-drafted `INSERT`/`CREATE` against the live schema would break that boundary and hand a model a
write it must never have. So applying a proposal is a person's job, and the workflow is:

1. The Architect drafts a proposal (`propose-top`, or the Overseer button) — SQL + a rationale.
2. A human **reads it**, checks it references only existing keys and conserves mass.
3. If good, they save it as the next `backend/src/main/resources/db/migration/V{n}__{name}.sql`.
4. On the next boot/migrate, Flyway applies it and the **V53 review gate** (and the reachability
   probe in CI) validate it exactly as they validate a hand-authored migration — the draft's origin
   (human or AI) is irrelevant to the gate. Only then is it canon.

The AI shortens step 1; steps 2–4 are the same safety pipeline every migration already passes
through. This is the whole "apply" story — no auto-apply is planned, by design.

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
- **Models — one per agent, not one global id.** Each engine is served by the tier that fits its
  job, and every id is pinned to a **current, valid** Anthropic model (a stale id fails silently the
  instant the pipeline is enabled — #244): the **narrator** on `claude-haiku-4-5-20251001` (high
  frequency, low stakes — one atmospheric sentence), the **interpreter** and **auditor** on
  `claude-sonnet-5` (mid-tier read-only reasoning), and the **architect** and **QA critic** on
  `claude-opus-4-8` (the deliberate, consequential authoring work). Each is overridable via its own
  `draugr.ai.*-model` key (below). We do **not** silently downgrade; keep these ids current.
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
| `enabled` | `DRAUGR_AI_ENABLED` | `false` | Master switch. Off ⇒ pure deterministic prose; the whole pipeline is inert. |
| `api-key` | `DRAUGR_AI_API_KEY` / `ANTHROPIC_API_KEY` | *(empty)* | Anthropic key. Never committed, never logged. |
| `authoring-enabled` | `DRAUGR_AI_AUTHORING_ENABLED` | `true` | The Architect's switch: create NEW scoped mechanics at runtime. On by default, but gated behind the master + key. |
| `narration-model` | `DRAUGR_AI_NARRATION_MODEL` | `claude-haiku-4-5-20251001` | Narrator model. |
| `interpreter-model` | `DRAUGR_AI_INTERPRETER_MODEL` | `claude-sonnet-5` | Procedure Interpreter model. |
| `auditor-model` | `DRAUGR_AI_AUDITOR_MODEL` | `claude-sonnet-5` | Auditor model. |
| `architect-model` | `DRAUGR_AI_ARCHITECT_MODEL` | `claude-opus-4-8` | Architect (authoring) model. |
| `qa-model` | `DRAUGR_AI_QA_MODEL` | `claude-opus-4-8` | QA critic model (deliberately distinct from the Architect). |
| `max-tokens` | `DRAUGR_AI_MAX_TOKENS` | `1024` | Output cap (generous so a short reply is never truncated). |
| `timeout` | `DRAUGR_AI_TIMEOUT` | `20s` | Per-call wall-clock bound. |

*(There are also per-agent `*-enabled` switches — `narration-enabled`, `interpreter-enabled`,
`auditor-enabled`, `qa-enabled` — all default **on**, so flipping the one master lights the whole
pipeline. Set an individual one false to run the rest without that agent. Every agent is additionally
gated by the master + key: an agent runs iff `isUsable()` **and** its own switch.)*

### Enabling the full generative pipeline, and verifying it (#244)

The pipeline is **built and wired but off by default**. Three things must be set — nothing in git
changes; the key is yours, supplied by env only:

1. `DRAUGR_AI_ENABLED=true` — master (lights narration + the Sonnet interpreter / multi-step decomposition).
2. `export DRAUGR_AI_API_KEY=…` (or `ANTHROPIC_API_KEY`) — your Anthropic key.
3. `DRAUGR_AI_AUTHORING_ENABLED` is already `true` — leave it, so the Architect may create new mechanics.

Restart the backend. Turn it all off again by unsetting `DRAUGR_AI_ENABLED` — no code or data change.

**Verify both stages actually fire** (not just that the flag is on):

- **Stage 1 — interpret / multi-step (Sonnet).** Submit a detailed multi-step action whose steps are
  all *existing* processes phrased naturally (e.g. a 2000-character sequence of fell → buck → split →
  season). It should be decomposed into an ordered plan and each step resolved in turn, visible in the
  outcome/narration — not collapsed to a single intent or a "nothing happened" miss.
- **Stage 2 — author (Opus).** Submit an action naming a real-world process the catalogue does *not*
  hold. The Architect should compose a new scoped mechanic, pass the physics gate + QA critic, write it
  scoped to the chronicle, and execute it — again, not "nothing happened".
- **If it still says "nothing happened" after enabling**, check the backend log for
  `AI call to model '<id>' failed …` — a stale/invalid model id fails exactly here and is otherwise
  indistinguishable from a legitimate miss. All configured ids must be current (see the table above).

The key is stored encrypted at rest ([SECURITY.md](../../SECURITY.md)). To give **internal testers** the
AI-enabled experience without exposing your key, see
[sharing-the-ai-with-testers.md](sharing-the-ai-with-testers.md).

---

## What would reopen the boundaries

- Moving the Architect to resolution time. Settled no; see routing-and-coverage-strategy.md.
- Letting the Auditor write or repair state. Never — it is read-only by definition.
- Making narration a hard dependency (removing the deterministic fallback). Never.
