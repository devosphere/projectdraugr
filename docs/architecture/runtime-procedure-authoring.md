# Runtime Procedure-Authoring — Implementation Spec

> Implements **[DR-0021](../systems/06.3-Decision-Log.md#dr-0021)**. Read the DR first for the *why*;
> this is the *how*. Governing invariant, restated: **every AI role is optional and non-load-bearing
> over a complete deterministic core.** With `draugr.ai.enabled=false` the game behaves exactly as it
> does today — the pipeline is inert.

---

## The seam

Resolution reaches AI at exactly one point: a **classifier miss**. Today
`PhysicalItemService.runProcess` → `ProcessMatcher.matchAndRecord(text)` returns `null` when no
existing process matches, and the action fails with the flat "turn the material over" line (the miss
is recorded to `routing_miss_backlog`). That miss is the only entry point. When AI is on, the miss is
handed to `RuntimeAuthoringService` before the failure line; when off, nothing changes.

```
runProcess(text)
  └─ matcher.matchAndRecord(text)  ──hit──▶ run the process (unchanged, deterministic)
        └─ miss (null) ─▶ RuntimeAuthoringService.attempt(chronicle, location, text)   [only if props.isUsable()]
              ├─ empty  ─▶ existing failure line (unchanged)
              └─ result ─▶ that result
```

## The five roles (four new classes + the Auditor split)

All follow the existing pattern: constructor-injected `LanguageModel` + `AiProperties`, **never throw**,
return empty/no-op when `!props.isUsable()`. Model per call via `props.get*Model()`.

1. **Procedure Interpreter** (`ProcedureInterpreter`, model: a mid-tier — configurable `interpreterModel`).
   Input: the action text + the chronicle's reachable inventory + the **catalog of existing process keys**
   (canonical + this chronicle's own scoped ones). Output: an ordered list of **existing** `process_key`s
   that compose the request, or empty ("novel / not composable"). It proposes a decomposition; it never
   invents a mechanic. Each step is then run through the **normal** `runProcess` gate (inputs, tools, mass,
   capability) — the deterministic backstop that kills a nearest-neighbour false match ("split the fish"→
   `split_planks` fails the input check).

2. **Runtime Architect** (`RuntimeArchitect`, model: `architectModel` = Opus). Fires only when the
   Interpreter returns novel. Drafts ONE new `material_process` (+ any new `item_definition` rows) as
   **data scoped to the discovering chronicle** (`discovered_by_chronicle_id`) — never schema, never
   canon, never an outcome. A genuinely new *domain* needing new tables is out of scope → stays a human
   migration (DR-0009/DR-0013); the Architect returns novel-but-unbuildable, logged for authoring.

3. **QA Critic** (`QaCritic`, model: independent of the Architect — `qaModel`). Reviews the Architect's
   draft for **plausibility/balance only** (design rule #5), never physics. Runs a **bounded ≤2-round**
   author↔critic loop: draft → QA verdict (pass / fail+reasons) → Architect revises → re-check. On
   non-convergence the mechanic is not created this session; the attempt drops to the human backlog.

4. **Deterministic physics gate** (`RuntimeProcessGate`, no AI). Runs at insert on every Architect draft,
   every round: mass balance (outputs ≤ inputs, no matter created), reachability (inputs obtainable),
   category/subject agreement. This is the floor QA sits above and the human sits above that.

5. **Auditor integrity split.** Hot-path, per action, **deterministic** (`ChronicleActionService` already
   grounds + validates; add: a scoped-tech action's effects satisfy the invariant catalog incrementally).
   Off-path LLM Auditor (`AuditorSummarizer`, existing) only on a flagged anomaly + the operator report.
   The LLM Auditor is never in the action loop.

**Orchestrator:** `RuntimeAuthoringService.attempt(...)` runs Interpreter → (compose | Architect+QA →
gate → insert scoped rows) → execute via the same runProcess machinery → write `chronicle_tech_discovery`.
Returns `Optional<String[]>` (outcome, narration) or empty. Gated by `props.isUsable()`.

## Schema (V66)

- **Scope column** `discovered_by_chronicle_id UUID NULL REFERENCES chronicle(id)` on `material_process`,
  `item_definition`, and the input/output/subject side-tables that define a mechanic. `NULL` = canonical
  (visible to all); non-null = that chronicle's private unlock. **Added once, globally** (monotonic —
  consistent with DR-0013; the schema stays global, only the *data rows* are scoped).
- `ProcessMatcher.candidates()` and every mechanic read gains `WHERE discovered_by_chronicle_id IS NULL OR
  discovered_by_chronicle_id = :chronicle`. Canonical behaviour is unchanged when no scoped rows exist.
- **`chronicle_tech_discovery`** ledger: `id, chronicle_id, discovered_at, procedure_text, process_key,
  gate_result, qa_verdict, model, promoted (bool), promoted_migration TEXT NULL`. The source of truth for
  the review pipeline; NOT immutable player history (an ops/derived record).

## Config (`draugr.ai.*`, additive)

| key | default | meaning |
|---|---|---|
| `interpreter-model` | `claude-sonnet-4-6` | the composer — independent reasoning, mid cost |
| `qa-model` | `claude-opus-4-8` | the critic — independent of the Architect for genuine review |
| `qa-max-rounds` | `2` | author↔critic loop cap |
| `authoring-enabled` | `false` | sub-switch: even with AI on, runtime authoring stays off until explicitly enabled |

`architectModel`/`narrationModel`/`auditorModel` already exist. The Interpreter is on by default when AI
is on (cheap, read-only); the **Architect authoring path is behind its own `authoring-enabled`** flag so
narration/interpretation can run without ever authoring scoped mechanics until the operator opts in.

## Surfaces & UX

- `GET /api/system/tech-discoveries` — the ledger, newest first (read-only; like `/api/system/errors`).
- Overseer **Tech Discovery Queue** panel (`OverseerAgents`): the procedure, the drafted rows, the gate +
  QA verdict, Approve→(generate a canonical migration draft) / Reject. GitHub `tech-discovery` issue
  auto-filed only on promote.
- Frontend **proportional loading overlay**: instant for the common (deterministic) case; the unclickable
  centered hourglass shows only while a real authoring pipeline is running.

## Build stages (each committed, AI-off verified)

1. **Spec** (this file). ✅
2. **V66 schema** — scope column + ledger; `candidates()` scoped read. Verify canonical unchanged.
3. **`RuntimeProcessGate`** (deterministic) + unit tests (mass balance, reachability).
4. **AI roles** (`ProcedureInterpreter`, `RuntimeArchitect`, `QaCritic`) + `RuntimeAuthoringService`,
   gated off, stub-tested (no network/key) exactly like `SimulationNarratorTest`.
5. **Wire** into the `runProcess` miss seam behind `props.isUsable() && authoring/interpreter flags`.
   Verify AI-off the game is byte-for-byte unchanged.
6. **Surfaces** — ledger endpoint + Overseer panel.
7. **Loading overlay** (frontend).
8. **AI-on live verification** — needs the operator key; deferred, like the existing three agents.

## What stays true

- No LLM is ever the final authority: gate (physics) → QA (plausibility) → human (canon).
- Runtime authoring writes scoped DATA only — never schema, never canon, never an outcome.
- Every role degrades to deterministic on disable/timeout/error. The game never depends on any of them.
