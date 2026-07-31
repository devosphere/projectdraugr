# Routing and Coverage Strategy

> **Project Draugr — Architecture**
>
> *AI belongs at authoring time, never at resolution time. Coverage is measured, not guessed.*

---

## Why this document exists

This settles a question that has now been asked more than once and would otherwise be re-derived every session: **should an AI layer sit inside action resolution, helping the classifier work out what the player meant?**

The answer is **no**, and the reasoning below is the whole of it. If a future cycle is tempted by the same idea, read this first — and if you still want to reopen it, [the closing section](#what-would-reopen-this) lists the specific evidence that would justify it. Re-deriving this from scratch costs more than the work it replaces.

The document also records the plan that was adopted *instead*, and the instrument (V56) that makes that plan self-directing.

---

## The decision

**AI is invoked at authoring time — when the world's definitions are written — and never at resolution time, when an action is being executed.**

Concretely:

| Moment | Who decides | Cost |
|--------|-------------|------|
| A player submits an action | `ActivityClassifier` + `ProcessMatcher`, deterministic | free, ~0 ms |
| A novel verb appears in the backlog | Persistent State Architect proposes a `category_term` | one call, **ever**, for that verb |
| The world lacks a mechanic entirely | Architect proposes a `material_process`, gated by V53 | one call per process, **ever** |

The deterministic layer never gets smarter at runtime. It gets smarter *between* runs, by acquiring vocabulary and process definitions that then serve every future action for free.

---

## Why not an AI classifier at resolution time

Four independent arguments. Any one of them is sufficient; together they are decisive.

### 1. The arithmetic says it would mostly be paying to confirm emptiness

Of roughly 46 actions probed across the four procedure simulations (15 m² cabin, fish preservation, bow production, leather armour), about 39 did not resolve. The diagnosis for most of them was **not** that the words were wrong — it was that **the mechanic does not exist**. There is no salt in the world. There is no staged assembly. There is no joinery. There are no building layers.

An AI classifier asked *"which of these twenty processes does 'brine the fillets' mean?"* would, in the majority case, pay an API call to arrive at *"none of them"* — a conclusion the free deterministic layer already reaches, correctly, in microseconds.

You cannot classify your way to a process that does not exist.

### 2. An LLM asked to pick from a short list is a false-COVERED generator by construction

The entire point of V54/V55 was to stop wrong matches. A wrong match is the dangerous direction: it does not throw, it silently suppresses the Architect call at the exact moment one was needed, and the gap is never recorded. See [action-routing-hardening.md](action-routing-hardening.md) for the eight recorded occurrences.

A model handed twenty candidates and a sentence will find the *nearest* one. That is what such models do. "Split the fish into thin strips" has an obvious nearest neighbour in `split_planks`, and a helpful model will say so. Inserting one at the resolution point would systematically re-manufacture the exact defect V54 and V55 were built to remove.

### 3. `runProcess` writes to a permanent record

An action that resolves consumes inputs, produces outputs, and appends to a chronicle's ledger. That record is history — the world persists across chronicle deaths (design rule #6).

A stochastic step in that path cannot be diffed, cannot be replayed, and cannot be gated. Note precisely why V53's review gate works: **what it gates is data sitting still.** A `material_process` row can be examined, mass-balanced, sampled by a human, and promoted from `DRAFT` to `VERIFIED` before it ever touches play. An inline classifier's output is not data awaiting review — it is a decision already executed. There is nothing left to gate.

### 4. It contradicts the architecture already decided

Under [core-agent-boundaries.md](core-agent-boundaries.md), neither the Architect nor the Simulation Agent resolves mechanics. The Architect evolves the schema; the Simulation Agent narrates. Mechanics are resolved by the deterministic foundation, which is what makes them replayable and auditable. A resolution-time classifier is a fourth agent with authority nobody granted it.

The routing spec said this already, in its own words: routing exists to *avoid* API calls, and a router that costs a call defeats itself.

---

## Where AI does belong

**Authoring time**, in two places, both one-shot and both permanent:

**Vocabulary.** A verb the world has never heard is a genuine gap in the classifier's knowledge. Ask the Architect to classify it once, land the resulting `category_term` in a migration, and every future action containing that verb routes for free. One call per novel verb, ever.

**Mechanics.** A missing process is a missing definition, not a missing inference. Generate the definition offline, run it through the V53 gate, promote only what passes. One call per process, ever.

The economic shape is the whole argument:

- **Inline classifier:** pays for the same sentence every time anyone types it. Cost scales with play. Never amortizes.
- **Authoring-time AI:** pays once and the answer compounds. Cost scales with the *world's vocabulary*, which is finite and converges.

---

## The plan

### Step 1 — Measure (V56) — **DONE**

Build the instrument before spending anything. `V56__routing_miss_backlog.sql` adds `routing_miss`: a frequency-ranked table of actions the foundation could not resolve, recorded on the play path only (`ProcessMatcher.matchAndRecord`, called from `PhysicalItemService.runProcess`).

It is a backlog, not a log. Repeats increment `hit_count` rather than adding rows, so it stays small and sorts itself by how much each gap actually costs.

Crucially it records **which gate rejected the action**, because that single field determines whether the fix is cheap or expensive.

### Step 2 — Generate mechanics in bulk through the V53 gate

The real cost lever. M1/M2 bought correctness, not coverage — true coverage across the four simulations is roughly 5–10%.

Generate 200–300 `material_process` definitions offline as `DRAFT`, validated by:

- **V53 mass balance** (`process_mass_balance`) — catches recipes that create matter; it caught 9 of the 20 *hand-authored* originals
- **V55 category agreement** — a process's own keywords must classify to its own declared category
- **V51 reachability** — every input must have an acquisition path
- **Derived subject terms** — from the process's own inputs and outputs, so it cannot drift out of agreement with itself
- **Sampled human review** — for plausibility, which mass balance cannot check (design rule #5: a recipe can conserve mass and still be bad primitive technology)

Only rows passing all of it are promoted to `VERIFIED`. Scope is named by the gaps the simulations found: staged assembly, joinery, building layers, timber preservation, salt and food preservation, fish processing, bow production, leather armour.

### Step 3 — Re-measure, then revisit

With the backlog populated and several hundred processes landed, ask the question again *with data*. If a large share of remaining misses are `VOCABULARY` or `SUBJECT` — the world has the mechanic but not the words — that is the case for authoring-time AI vocabulary work, and possibly the only case an inline layer would ever have had.

---

## How to read the backlog

```sql
SELECT gap_kind, hit_count, near_process_key, sample_text
FROM routing_miss_backlog
ORDER BY hit_count DESC
LIMIT 40;
```

`gap_kind` tells you what to *do*, and the four kinds have wildly different costs:

| `gap_kind` | What happened | The fix | Cost |
|-----------|---------------|---------|------|
| `VOCABULARY` | Nothing in the sentence was recognised at all (`classified_category IS NULL`) | Add `category_term` rows | cheapest |
| `KEYWORD` | A process shares the category but answers to none of these words | Add keywords to `near_process_key` | cheap |
| `SUBJECT` | Right process, right verb, **wrong material** | Either add a `process_subject` term, or accept that the player meant a material this process genuinely does not handle | judgement |
| `MECHANIC` | No process even shares the classified category | Author a whole new process, gated by V53 | expensive — this is what Step 2 is for |

The proportion of `MECHANIC` to everything else is the number that matters. It is the direct measure of whether the world is short of *words* or short of *capability*, and it is the number that decides where the next cycle's effort goes.

For raw candidate terms:

```sql
SELECT word, occurrences, distinct_actions
FROM routing_unknown_term
ORDER BY occurrences DESC
LIMIT 40;
```

Every row is a word the world has never heard, already excluding anything carried by `category_term` or `process_subject`. The ranking says which are worth an Architect call.

---

## Rules that must not be broken

These are the invariants the above depends on. Breaking any one of them quietly undoes the reasoning.

1. **One implementation of the resolution rule.** `ProcessMatcher.resolve` is a pure static function. It is not re-expressed in SQL, and both callers — `PhysicalItemService.runProcess` and `ArchitectRouter` — go through it. A process that would not *run* for an action must never be reported as *covering* it, or the gap goes both unreported and unresolved.

2. **Misses are recorded on the play path only.** `runProcess` uses `matchAndRecord`; `ArchitectRouter` uses the silent `match`, because it is read-only by contract. An action merely being assessed is not evidence a player wanted anything.

3. **Miss recording never costs a player their action.** `RoutingMissRecorder` is a separate `@Component` — not a method on `ProcessMatcher` — because `REQUIRES_NEW` is applied by a Spring proxy and a proxy never intercepts a bean's call to itself. Recording from inside the matcher would have joined the caller's transaction and rolled back with it, losing exactly the misses that matter most: the ones where the action then failed. It also swallows its own exceptions.

4. **A NULL classification drops the category condition** rather than matching nothing. Failing closed there is the expensive direction — an Architect call spent on something the foundation already knows how to do.

5. **Both test columns stay green.** `ProcessRoutingTest` asserts that recorded collisions stay blocked *and* that every process stays reachable. These pull against each other: tightening until nothing collides makes every action an Architect call; loosening until everything resolves is how the collisions arose.

---

## What would reopen this

State the evidence plainly so a future cycle can check it rather than re-argue it. This decision should be revisited if, after Step 2:

- `MECHANIC` falls below roughly a quarter of weighted backlog volume — meaning the world mostly *has* the capability and is failing on language, and
- `VOCABULARY` and `SUBJECT` misses are dominated by long-tail phrasings that no finite term list closes — meaning authoring-time vocabulary work has hit diminishing returns, and
- a candidate design exists that keeps AI **out of the write path** — e.g. an offline pass over the backlog that proposes terms, not an inline call that resolves an action

Note that even the reopened version puts the model at authoring time. Argument 3 above does not weaken with scale; a stochastic step in front of a permanent record is wrong at any coverage level.

---

## Related

| Document | Relevance |
|----------|-----------|
| [action-routing-hardening.md](action-routing-hardening.md) | The defect, the eight recorded collisions, and milestones M1–M5 |
| [core-agent-boundaries.md](core-agent-boundaries.md) | What each of the three agents may do |
| [domain-creation-pattern.md](domain-creation-pattern.md) | How the Architect adds a domain |
| `V53__process_review_gate.sql` | The gate that makes machine-authored processes safe to accept |
| `V54__activity_categories.sql` | The resolution rule, stated in full at the foot of the migration |
| `V55__routing_category_corrections.sql` | Category/vocabulary agreement, enforced at migration time |
| `V56__routing_miss_backlog.sql` | The instrument this strategy is directed by |
