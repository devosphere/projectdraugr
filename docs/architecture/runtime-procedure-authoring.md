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

5. **Auditor integrity split + canon-promotion owner.** Hot-path, per action, **deterministic**
   (`ChronicleActionService` already grounds + validates; add: a scoped-tech action's effects satisfy the
   invariant catalog incrementally). Off-path LLM Auditor (`AuditorSummarizer`, existing) only on a flagged
   anomaly + the operator report. The LLM Auditor is never in the action loop.
   **The Auditor — not the Architect — drives canon promotion.** The Architect only ever writes *scoped* data
   (it must never touch canon/schema, per DR-0009/0013), so the read-only integrity role is the correct one to
   endorse a discovery upward: once a scoped discovery has run consistently in the data file, the Auditor
   validates its persisted rows and **surfaces it as a canon-promotion candidate** — the integrity report + the
   generated migration draft — into the Tech Discovery Queue. A **human approves** there and the V53 gate +
   Flyway finalize it; the Auditor prepares and drives promotion but is **never the final authority**. This
   applies uniformly to **both** producers: the Architect's authored *mechanics* and the Interpreter's learned
   *aliases/composites* (below) are promoted the same way, by the Auditor.

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

## Cost model & role economics (pay once, then free)

The framework's economics: **AI cost is a one-time investment per novel thing, not a per-play tax.** New
features are *discovered during play* for a single API call and then implemented for a lifetime — instead of
being hand-built. Four parts, and the discipline that keeps each true.

**1. Architect + QA + Auditor — one call per novel mechanic, then free forever.** *(✅ built)* Authoring
writes a scoped `material_process` (with keywords); the very next identical action hits it **deterministically**
— no AI. Then the **Auditor** (not the Architect) validates the persisted rows and surfaces the discovery as a
canon-promotion candidate in the Tech Discovery Queue, where a **human approves** it into canon for all players.
This part already converges to zero at runtime.

**2. Narrator — cheap flavor over free templates.** Refine the hardcoded templates to high quality (#30, free
at runtime); the Narrator fires **low-token only to add flavor**, never load-bearing.

**3. The deterministic classifier does the maximum free work.** It resolves the ~99% common case; the AI fires
only on a genuine miss. A well-written classifier + reachability is precisely what keeps the paid layer idle —
the better the deterministic logic, the less the Interpreter ever has to think.

**4. Interpreter/Analyzer — the only recurring cost, and it must converge too.** *(⚠ NOT yet built)* It fires
on a classifier miss to do the reasoning the classifier can't. Today it is **execute-and-forget** — a
composition persists nothing, so the same missed phrasing re-fires and re-pays. **Required:** memoize every
resolved miss so the second occurrence is deterministic and free —
- a successful **composition** → persist a scoped **keyword/alias → plan** (or a small composite process);
- the **Auditor** validates that scoped alias and surfaces it for **canon** promotion through the same queue —
  identical path to an authored mechanic, so a learned phrasing becomes free for all players once a human approves;
- a cheap **deterministic pre-filter** (normalize + fuzzy-match against existing keywords/aliases/prior misses)
  resolves near-misses with **no AI call**.
Result: the Interpreter fires **once per novel phrasing**, then that phrasing is deterministic and free — the
same amortization as the Architect, applied to reasoning. **Recommended option: the scoped alias** (lightest;
rides the existing promotion pipeline). Recurring spend then reduces to genuinely novel reasoning the world has
never seen — finite, and converging as the vocabulary saturates. Prompt caching keeps even first-time calls cheap.

### The hand-off contract — no re-derivation, no cost spike
Each role adds its **narrow** value over the previous role's *structured* output and **never repeats** the
prior's work. This is what prevents a cost spike when the pipeline convenes to author a discovery.

| Stage | Consumes | Produces for the next role | Must NOT |
|---|---|---|---|
| Classifier + reachability *(free)* | raw action text | resolved intent, **or** a miss + the quantified/sourced reachable pool + nearest existing processes | make the AI re-gather context the deterministic layer already has |
| **Interpreter / Analyzer** *(paid, on miss)* | the miss + structured context | a **composition plan** (existing `process_key`s), **or** a complete **authoring brief** — intended output (+ attributes/dimensions from the player's text), inputs with quantities drawn from the pool, tool, procedural rationale, and *why nothing existing fits* | hand the Architect raw text to re-interpret |
| **Architect** *(paid, only if "author")* | the Interpreter's brief | a validated `ProcessDraft` (exact rows) | re-reason the player's intent — it **formalizes**, it does not re-think |
| **QA Critic** *(paid)* | the draft | plausibility verdict | re-check physics (the gate's job) or intent (the Interpreter's job) |
| **Auditor** *(read-only; promotion owner)* | the written scoped rows (mechanic **or** alias/composite) | consistency confirmation **+ a canon-promotion candidate** (integrity report + migration draft) into the Tech Discovery Queue | write anything, or promote to canon itself — it prepares; the **human approves** and the V53 gate finalizes |

**The principle in one line:** *if each role does its job completely, the next role has nothing to redo.* A
complete Interpreter brief means the Architect **formalizes cheaply** instead of **re-reasoning expensively** —
so authoring a discovery does not spike cost. A well-written classifier means the Interpreter fires rarely; with
memoization, it never fires twice for the same phrasing. The roles must be built to this contract, not as five
independent models each re-deriving the player's intent.

## AI reasoning quality — making it good, cheap, and measurable

The user observed the Interpreter/Narrator "burn tokens for weak output." That is a *quality-and-cost*
problem, not an integrity problem, and it is solved by measurement + the gate, not by hoping a bigger model
behaves. This is the plan for turning "iterative tuning" into a bounded, data-driven loop, and for making
the cost-vs-richness model decision on evidence.

### Principle 0 — the gate makes weak output cheap, never catastrophic
Every reasoning output passes the deterministic **physics gate** (mass balance, reachability) → **QA**
(plausibility) → **Auditor** before anything persists. A weak Architect draft is **rejected**, not shipped.
So the cost of poor reasoning is *latency + a failed action*, never a corrupted world. Tuning therefore
targets **first-pass acceptance rate and readable quality** — it never carries the integrity burden (the gate
does). This is what lets us iterate boldly without risk.

### 1. Output is a validated schema, not prose
Each reasoning role returns **strict JSON** the code validates before use — the Architect returns a
`ProcessDraft` (`output_item`, `inputs[{item_key, quantity}]`, `tool_class`, `new_items[]`, `attributes`),
not free text. Malformed or implausible → rejected at zero downstream cost. Prompt *asks* for reasoning;
**code enforces the contract.** Boundaries (scoped-data-only, never-outcome) are enforced in code, never
trusted to the prompt.

### 2. Quantified, sourced input + template exemplars (the "guides")
The reasoning roles receive: the **quantified, sourced reachable pool** (Layer 1 of 06.5 — counts + where),
the raw action text, and a curated set of the **130 real processes as few-shot exemplars** (DR-0022 §3), so
authored recipes are patterned on known-good primitive technology. Weak output usually means weak *input* —
fix the context before the model.

### 3. Prompt caching = the token fix
The big static prefix — role contract + template exemplars + invariant catalog — is identical across calls.
Use **Anthropic prompt caching** so repeated authoring calls pay for the exemplars **once**; only the small
per-action tail (action text + reachable pool) is fresh. This directly removes the "burned tokens" cost of a
large exemplar block.

### 4. The evaluation harness (you cannot tune what you cannot measure)
Build `ai-eval/` — a **golden set** of action inputs (the acceptance suite A1–A10 + the broader Phase-0
vocabulary + known-bad cases), each tagged with its expected disposition: `COMPOSE(existing process)`,
`AUTHOR(recipe ≈ these inputs, mass-balanced)`, or `REJECT(must fail the gate)`. Run the pipeline **offline**
against it and score, **automatically where possible**:
- compose hit-rate; author gate-accept rate; mass-balance correctness; reachability; **round-trip** (does the
  authored item persist and grab with correct counts); QA rounds to converge; input tokens (cached vs fresh);
  output tokens; latency.
- **human rating** only for the part machines can't judge: does the recipe read as *good, plausible primitive
  technology* (design rule #5).

This runs without a live playthrough, so iteration is fast and cheap.

### 5. Model-tier strategy — the cost-vs-richness call, made on evidence
Match model to cognitive load, and note that **cost aligns with rarity**:

| Role | Load | Default | Frequency → cost |
|---|---|---|---|
| Narrator | low (witness flavor over deterministic templates) | Haiku | frequent → keep cheap |
| Interpreter | mid (decompose to existing processes) | Sonnet | on miss → moderate |
| Architect | **high (invent a physically-valid recipe with exact numbers)** | Opus | **rare — once per novel mechanic, then free** |
| QA Critic | high + **independent of Architect** | Opus (distinct) | rare (only when authoring) |
| Auditor | mid (summarize a read-only report) | Sonnet | occasional |

The expensive roles fire **only on genuine novel authoring** — rare, once per mechanic, then **zero** after
canonisation/dedup — so a strong Architect is affordable *because* it is rare. **Don't guess the frontier —
measure it:** run the eval with the Architect on the strongest model to set the quality ceiling, then step
models down and watch acceptance/quality on the golden set; **pick the cheapest model that still passes the
acceptance suite** and lock it. Subscription tiers select stronger models per account (the business model);
the eval produces the data to price tiers honestly.

### 6. The author↔critic loop is a prompt-quality signal
`qaMaxRounds = 2`. Track the convergence distribution (0/1/2 rounds): mostly-2 ⇒ the **Architect prompt** is
weak (fix the prompt/exemplars, do **not** raise the cap); mostly-0 ⇒ QA is too lenient. The loop count tunes
the prompts, not just the safety cap.

### 7. Dedup → cost converges to zero
Once `hand_carry_basket_handled` is authored (scoped), the next similar request resolves **deterministically**
via that scoped process — no AI. Cross-chronicle pending-draft dedup avoids re-authoring the same mechanic.
Cost scales with the world's **finite, converging vocabulary**, not with play.

### 8. Observability (Overseer)
Log every AI call: role, model, tokens (cached/fresh), latency, gate result + reasons, QA verdict + rounds,
accept/reject, final disposition — per-action and aggregate, on the Overseer. "Disappointing performance"
becomes a number we drive down, and the token/latency budget becomes visible.

### 9. The tuning workflow (the iterative loop, concretely)
1. Assemble the golden set (A1–A10 + Phase-0 vocab + bad cases).
2. Run offline; collect auto-scores; sample for human quality rating.
3. Fix the **weakest role's** prompt / exemplars / output schema; re-run; compare deltas.
4. Sweep models per role; find the quality/cost frontier.
5. Lock the config when the acceptance suite passes **and reads richly**.
6. **Only then** flip AI-on for the live verification playthrough (06.5 confidence gate).

**Honest expectation:** step 5 is not one-shot. Budget a handful of eval→fix iterations before the live run.
But because the harness is offline and auto-scored, those iterations are cheap and fast — the expensive,
slow thing (a live keyed playthrough) happens once, at the end, against an already-tuned pipeline.

## What stays true

- No LLM is ever the final authority: gate (physics) → QA (plausibility) → human (canon).
- Runtime authoring writes scoped DATA only — never schema, never canon, never an outcome.
- Every role degrades to deterministic on disable/timeout/error. The game never depends on any of them.
- Quality/cost is **measured** (offline eval + Overseer telemetry), never assumed; the model tier is chosen on that evidence.
