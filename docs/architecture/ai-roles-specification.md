# AI Roles — Detailed Specification

The single source of truth for **what each AI in Project Draugr is, when it runs, what it consumes and
produces, what it may never do, and how it hands off to the next.** It consolidates the role definitions that
were previously scattered across [DR-0021](../systems/06.3-Decision-Log.md#dr-0021),
[DR-0022](../systems/06.3-Decision-Log.md#dr-0022),
[runtime-procedure-authoring.md](runtime-procedure-authoring.md), and
[ai-integration.md](ai-integration.md). Where those disagree with this file, treat those as history and this
as the specification.

## Supreme invariant (governs every role below)

**Every AI role is optional and non-load-bearing over a complete deterministic core.** The game runs at full
correctness with all AI disabled. No LLM is ever the final authority on physics or canon: the chain is always
**deterministic gate (physics) → QA (plausibility) → human (canon)**. Two master switches:
`draugr.ai.enabled` (default **false** — the whole layer) and `draugr.ai.authoring-enabled` (default **false**
— the Architect/QA authoring sub-layer). With both off, none of these classes construct a live model.

## What is NOT an AI (the deterministic layers the AIs sit on)

| Layer | Class | Role |
|---|---|---|
| **Intent classifier + reachability** | `ChronicleActionService.classify()` + `PhysicalItemService` reachable-pool queries | Resolves the ~99% common case with zero AI; on a miss, assembles the structured context the AI needs. The better this is, the less any AI ever runs. |
| **Physics gate** | `RuntimeProcessGate` | Deterministic mass-balance (outputs ≤ inputs), reachability (inputs obtainable), category/subject agreement. Runs on every Architect draft. The floor QA sits above. |

---

## Role A — Simulation Narrator

| Field | Value |
|---|---|
| **Class** | `SimulationNarrator` |
| **Model** | `claude-haiku-4-5` (cheap/fast — flavor, not reasoning) — `draugr.ai.narration-model` |
| **Frequency** | The *frequent* AI role — but only on router-selected moments (~10% of actions) |
| **Build status** | ✅ Built & wired into `resolve()`; gated off by `enabled` |

- **Mandate.** Add atmospheric flavor on top of the deterministic witness prose. It is a *witness*, never a guide.
- **Trigger.** After the deterministic narration is final and the `PerceptionFrame` is built, `NarrationRouter.shouldUseAI(intent, outcome, attention, text, stateChanges, …, died, …)` decides. A pure, free gate that keeps ~90% of actions off the network (routes on: deaths, kills, HIGH-attention observes, significant state changes).
- **Input.** `refine(PerceptionFrame frame, String backendNarration)` — the structured frame (location, weather, body deltas, nearby objects the chronicle can perceive) + the deterministic prose already written.
- **Processing.** Composes a witness-stance prompt and asks for **one** atmospheric sentence.
- **Output.** The deterministic prose, optionally with a single appended sentence. Returns the input **unchanged** on disable/timeout/error/blank.
- **Hard boundaries.** MUST NOT hint, teach a procedure, advise, reveal anything not in the `PerceptionFrame` (no unseen wolf, no hidden durability, no future weather), or alter mechanics. Reaffirms DR-0010.
- **Hand-off.** Upstream: the deterministic resolver + `PerceptionFrame`. Downstream: the player (its output is the displayed narration). Feeds no other AI.
- **Cost & convergence.** Low per call (small tokens, cheap model). Does not "converge" — it fires whenever the router selects a moment — but it is deliberately kept cheap because #30 pushes the *quality* into free hardcoded templates, leaving the Narrator only flavor.
- **Degradation.** Total: on any failure the deterministic prose stands. Never blocks the action.

---

## Role B — Procedure Interpreter (the "Analyzer")

| Field | Value |
|---|---|
| **Class** | `ProcedureInterpreter` |
| **Model** | `claude-sonnet-4-6` (mid-tier — decomposition/reasoning) — `draugr.ai.interpreter-model` |
| **Frequency** | Fires **only on a deterministic classifier miss** — the recurring cost role |
| **Build status** | ⚠ Partially built: `plan()` composes existing processes. The **authoring-brief output**, **quantified input**, and **convergence/memoization** are PLANNED (F2/F9). |

- **Mandate.** Do the *reasoning, logical, critical thinking* the hardcoded classifier can't — map a novel/complex player action onto the world's mechanics. It is the brain that lets the game say "yes" to a rational request.
- **Trigger.** The classifier returns no intent AND no existing material process matches → `RuntimeAuthoringService.attempt()` → `interpreter.plan(...)`.
- **Input (today).** `plan(String actionText, List<String> inventory)` — the raw action text + the reachable item **keys**. *(Gap F2: keys only, no counts; Gap: reachable pool is not yet zone/storage-complete — see DR-0022.)*
- **Input (target).** The full action text + the **quantified, sourced reachable pool** (counts + where: carried / ground / on-site storage / tool rack) + the 130 processes as exemplars.
- **Processing.** Attempts to express the action as an **ordered sequence of existing `process_key`s** (canonical + this chronicle's scoped ones). Each step is still validated by the deterministic gates when executed.
- **Output (today).** `List<String>` — an ordered composition plan of existing process keys (empty ⇒ can't compose).
- **Output (target).** Either a **composition plan** (existing keys) OR, when nothing composes, a complete **authoring brief** for the Architect: intended output item (+ attributes/dimensions parsed from the player's text), inputs with quantities from the pool, tool, procedural rationale, and *why nothing existing fits* — so the Architect formalizes instead of re-reasoning.
- **Hard boundaries.** Composes/analyzes; **never invents** a mechanic, **never decides an outcome**, **never writes**. Read-only reasoning.
- **Hand-off.** Upstream: the classifier miss + structured context. Downstream: if it composes → `runPlan` executes existing processes; if it can't → hands the **authoring brief** to the Architect. MUST hand over a complete brief so the Architect does not re-interpret raw text.
- **Cost & convergence.** THE recurring cost. Today it is **execute-and-forget** — a composed miss persists nothing, so the same phrasing re-fires and re-pays (F9). **Required convergence:** a resolved miss is memoized as a scoped **keyword/alias → plan**; the Auditor promotes it to canon; a deterministic pre-filter resolves near-misses with no AI call. Then it fires **once per novel phrasing**, then free. Prompt caching keeps first-time calls cheap.
- **Degradation.** On disable/failure → empty plan → the ordinary deterministic miss line. Never blocks.

---

## Role C — Runtime Architect

| Field | Value |
|---|---|
| **Class** | `RuntimeArchitect` |
| **Model** | `claude-opus-4-8` (strong — the hard reasoning) — `draugr.ai.architect-model` |
| **Frequency** | **Rare** — only when the Interpreter cannot compose; once per novel *mechanic*, then free |
| **Build status** | ✅ Built (`draft`/`revise` → `ProcessDraft`); gated behind `authoring-enabled` (default off) |

- **Mandate.** Author a **new mechanic** the world lacks, as data scoped to the discovering chronicle — with exact numerical inputs — when no existing process can be composed.
- **Trigger.** `RuntimeAuthoringService.author()`, only if `authoring-enabled` AND the Interpreter returned no plan.
- **Input.** `draft(String procedureText, List<String> inventory)` — the action text + reachable inventory. `revise(ProcessDraft previous, String reasons)` for the QA loop. *(Target: consume the Interpreter's authoring brief + quantified pool + exemplars.)*
- **Processing.** Drafts a physically-plausible recipe patterned on the real processes.
- **Output.** `Optional<ProcessDraft>`:
  ```
  ProcessDraft { processKey, category, keywords, subjects, toolClass,
                 inputs:[Ingredient{itemKey, quantity}], outputItemKey, outputQty,
                 narration, newItems:[NewItem{itemKey, displayName, category, unitMassGrams, unitVolumeMl}] }
  ```
  Exact integer `quantity` per input; may declare new items. On insert this becomes a scoped `material_process` row (with `keywords` + `discovered_by_chronicle_id`), so the next identical action resolves **deterministically**.
- **Hard boundaries.** Writes **scoped DATA rows only** — `discovered_by_chronicle_id = <this chronicle>`. NEVER schema, NEVER canon, NEVER an outcome, NEVER a new *domain*/table (that stays a human migration — DR-0009/0013). It proposes; it does not promote.
- **Hand-off.** Upstream: the Interpreter's brief. Downstream: the **physics gate** (every draft, every round), then the **QA Critic**. On pass → `insertScopedProcess` + execute. Does **not** re-reason the player's intent — it formalizes.
- **Cost & convergence.** Rare and self-amortizing: once authored+promoted, that mechanic is deterministic forever. A strong model is affordable *because* it fires rarely (once per novel mechanic across the world's finite vocabulary).
- **Degradation.** On disable/failure/non-convergence → no mechanic this session; attempt drops to the deterministic miss (and the backlog).

---

## Role D — QA Critic

| Field | Value |
|---|---|
| **Class** | `QaCritic` |
| **Model** | `claude-opus-4-8`, **independent of the Architect** — `draugr.ai.qa-model` |
| **Frequency** | Rare — only during authoring (fires with the Architect) |
| **Build status** | ✅ Built (`review` → `Verdict`); gated behind `authoring-enabled` |

- **Mandate.** Judge whether the Architect's draft is **plausible, balanced primitive technology** (design rule #5) — the judgment axis the deterministic gate cannot check.
- **Trigger.** Each round of the author↔critic loop, after the physics gate passes a draft.
- **Input.** `review(ProcessDraft d)` — the drafted mechanic.
- **Processing.** A bounded author↔critic loop of at most `qaMaxRounds` (**2**): draft → verdict → Architect `revise` → re-check.
- **Output.** `Verdict { boolean passed, String reasons }`. On fail, `reasons` feed the Architect's `revise`.
- **Hard boundaries.** Plausibility/balance **only** — NEVER physics (mass balance/reachability are the gate's job), NEVER the player's intent (the Interpreter's job). Must be a model **independent** of the Architect for genuine review, never a rubber stamp.
- **Hand-off.** Upstream: the Architect's gated draft. Downstream: back to the Architect (revise) or forward to insert+execute (pass). On non-convergence after 2 rounds → not created this session.
- **Cost & convergence.** Rare (authoring only), amortized like the Architect.
- **Degradation.** On disable → treated as non-blocking per the orchestrator's gating; the mechanic simply isn't authored when the layer is off.

---

## Role E — Persistent State Auditor

| Field | Value |
|---|---|
| **Classes** | Deterministic hot-path in `ChronicleActionService`; off-path LLM `AuditorSummarizer`; **canon-promotion owner** |
| **Model** | `claude-sonnet-4-6` (LLM summarizer only) — `draugr.ai.auditor-model` |
| **Frequency** | Deterministic part: every action. LLM part: occasional (flagged anomaly / operator report / promotion candidacy) |
| **Build status** | ⚠ Partial: hot-path grounding ✅, `AuditorSummarizer.summarize()` ✅; **incremental scoped-tech invariant check**, **promotion-candidate reporting**, **Tech Discovery Queue panel + promote action** are PLANNED |

- **Mandate.** The integrity backstop AND the **owner of canon promotion**. Two capabilities:
  1. **Hot-path, deterministic (no AI):** on every action, narration may reference only entities in the `PerceptionFrame`; a scoped-tech action's effects satisfy the SQL invariant catalog, checked incrementally against a running consistency checkpoint (never a full-history rescan).
  2. **Off-path, LLM (`AuditorSummarizer`, non-load-bearing):** `summarize(AuditReport)` turns a read-only consistency report into a plain operator summary; describes only.
- **Canon-promotion ownership (the key correction).** Because the Architect only ever writes *scoped* data, the **read-only Auditor** — not the Architect — drives promotion. Once a scoped discovery (an Architect **mechanic** OR an Interpreter **alias/composite**) has run consistently, the Auditor validates its persisted rows and **surfaces a canon-promotion candidate** (integrity report + generated migration draft) into the **Tech Discovery Queue**. A **human approves** there; the V53 gate + Flyway finalize. The Auditor prepares/drives promotion but is **never the final authority**.
- **Input.** The `PerceptionFrame` + effect rows (hot-path); an `AuditReport` (summarizer); the `chronicle_tech_discovery` ledger rows (promotion candidacy).
- **Output.** Deterministic pass/flag per action; an operator summary string; a canon-promotion candidate (report + migration draft) for human review.
- **Hard boundaries.** Strictly **read-only** — describes and reports, never repairs, never writes, never promotes to canon itself. The LLM auditor is **never** in the action loop.
- **Hand-off.** Upstream: everything written by the Architect/Interpreter + the action effects. Downstream: the human (via the Tech Discovery Queue) and the operator report.
- **Cost & convergence.** Hot-path is free (deterministic). LLM part is occasional and cheap.
- **Degradation.** The deterministic hot-path always runs; the LLM summary is optional and absent when off.

---

## The end-to-end flow (one player action)

```
Player action text
   │
   ▼
[Deterministic] classify + reachability ──hit(~99%)──▶ resolve deterministically ─┐
   │ miss                                                                          │
   ▼                                                                               │
[AI] Interpreter.plan(text, reachable pool)                                        │
   │ composes existing? ──yes──▶ runPlan executes existing processes ──────────────┤
   │ no (hands an authoring brief)                                                 │
   ▼  (only if authoring-enabled)                                                  │
[AI] Architect.draft ──▶ [Deterministic] physics gate ──▶ [AI] QA (≤2 rounds)      │
   │ pass                                                                          │
   ▼                                                                               │
insert scoped material_process (+ new items) ──▶ execute via runProcess ───────────┤
   │                                                                               │
   ▼                                                                               ▼
write chronicle_tech_discovery ledger                              [AI] Narrator (router-gated)
   │                                                                               │
   ▼                                                                               ▼
[AI/Deterministic] Auditor validates persisted rows,                     appended flavor sentence
   surfaces a canon-promotion candidate ──▶ Tech Discovery Queue ──▶ HUMAN approves ──▶ V53 gate + Flyway ──▶ CANON
```

## Cross-cutting matrices

**Boundary matrix — what each role may never do**
| Role | Never |
|---|---|
| Narrator | hint / teach / advise / reveal unseen state / change mechanics |
| Interpreter | invent a mechanic / decide an outcome / write |
| Architect | touch schema / canon / outcomes / new domains; promote |
| QA | check physics or intent; rubber-stamp |
| Auditor | write / repair / promote to canon itself |

**Cost matrix**
| Role | Model | Fires | Converges to zero? |
|---|---|---|---|
| Narrator | Haiku | ~10% of actions (router) | No (kept cheap by design; templates carry quality) |
| Interpreter | Sonnet | on classifier miss | **Only once memoization (F9) is built** — then once per novel phrasing |
| Architect | Opus | on un-composable miss (authoring on) | Yes — once per novel mechanic, then deterministic |
| QA | Opus (independent) | during authoring | Yes (amortized with the Architect) |
| Auditor (LLM) | Sonnet | occasional | Free hot-path; cheap off-path |

**Build-status matrix (2026-08-04)**
| Role | Built | Gated by | Pending |
|---|---|---|---|
| Narrator | ✅ wired into `resolve()` | `enabled` | live prompt tuning |
| Interpreter | ⚠ compose-only | `enabled` | authoring brief, quantified/zone-complete pool (F2), memoization/convergence (F9) |
| Architect | ✅ draft/revise | `authoring-enabled` | quantified input; exemplars; attribute-layer output |
| QA | ✅ review, ≤2 rounds | `authoring-enabled` | — |
| Physics gate | ✅ deterministic, unit-tested | — | — |
| Auditor | ⚠ hot-path grounding + summarizer | `enabled` (LLM part) | incremental scoped-tech invariants; **promotion-candidate reporting + Tech Discovery Queue panel + promote action** |
| Orchestrator (`RuntimeAuthoringService`) | ✅ `attempt()` | `isUsable()` | wire memoization + brief |

## Disambiguation — the two "Architects"

- **`RuntimeArchitect`** (this spec, Role C) — *resolution-time*, per action, authors **scoped** mechanics inside the DR-0021 pipeline.
- **`PersistentStateArchitect`** (a distinct, earlier sibling) — *authoring-time / offline*. Drains `routing_miss_backlog`, and `propose(entry)` drafts a **migration** for a gap (`ArchitectProposal{gapKind, sampleText, draftMigration}`), surfaced at `GET /api/architect/backlog` + `POST /api/architect/propose-top`. It proposes migrations for a human; it is **not** in the action path. Do not confuse the two.

## What stays true (every role)
- No LLM is the final authority: **gate (physics) → QA (plausibility) → human (canon)**, with the Auditor driving the promotion report.
- Runtime authoring writes **scoped DATA only** — never schema, never canon, never an outcome.
- Every role **degrades to deterministic** on disable/timeout/error. The game never depends on any of them.
- Quality/cost is **measured** (offline eval harness + Overseer telemetry), never assumed; the model tier is chosen on that evidence.
