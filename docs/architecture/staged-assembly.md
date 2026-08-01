# Staged Assembly (M3)

> **Project Draugr — Architecture**
>
> *Some things are not made in one act. They are built in stages, and some stages are only waiting.*

---

## Why this exists

`material_process` (V52) turns inputs into an output in a single act. That is the wrong shape for anything built in stages — a structure raised course by course, or a craft whose parts must be made, joined, and then left to set. The four procedure simulations hit this wall; the bow hit it hardest, because one of its stages is not work at all but **waiting**: sinew glue must dry for the better part of a day before the bow can be strung, and no single-shot process can say "come back later."

Structures and crafts share one schema deliberately. The bow fixture proved a portable craft hits the identical staging wall a sited structure does, so the engine serves both: a `subject_kind` of `STRUCTURE` (sited, becomes a `construction_project`) or `CRAFT` (portable, yields a carried item).

---

## The model

A **definition** plus an ordered list of **stages**; an **instance** tracks one chronicle's progress.

| Table | Holds |
|-------|-------|
| `assembly_definition` | The blueprint: subject kind, what it produces (item or construction kind), routing keywords, completion narration, `review_state`. |
| `assembly_stage` | One step: order, name, prerequisite stage, `cure_minutes`, tool class, `requires_fire`, narration. |
| `assembly_stage_requirement` | Items (and quantities) a stage consumes. |
| `assembly_instance` | One chronicle's build of one thing: state, location (sited), object_id (the result). |
| `assembly_stage_completion` | Which stages of an instance are done, and **when** (the clock a cure reads). |

State lives on the instance, never the definition, so one blueprint serves every chronicle — exactly as `material_process` is shared and per-run state lives elsewhere.

### The cure stage is the point

A stage with `cure_minutes > 0` and no requirements completes not on inputs but on **elapsed world time**: when the player next tries to advance it, the engine compares `now` to the completion time of its prerequisite. Too soon, and it reports how long remains and consumes nothing; long enough, and it completes. The clock is the same simulated tick the body and food already run on (`SimulationTickService`), so the player passes it simply by doing other things and coming back. This is the one capability a single-shot process fundamentally cannot express, and it is why M3 is a schema change rather than more data.

---

## Advancing

`ADVANCE_ASSEMBLY` is dispatched in `ChronicleActionService` **before** the material-process fallback: the text is matched to an assembly by whole-word keyword (longest wins, `AssemblyService.match`); a null means no assembly, and the dispatch falls through to a material process. On a match, `AssemblyService.advance` resolves the **next** stage — the lowest-order stage not yet done whose prerequisite is done — and:

- **cure stage:** checks elapsed time since the prerequisite; either "still curing" (no change) or marks it complete.
- **work stage:** checks tool class, fire, and every requirement; on success creates the instance if this is the first stage, consumes the inputs, and records completion.
- **final stage:** marks the instance `COMPLETE` and produces the result — `createCarriedItem` for a craft, or a completed `construction_project` at the location for a structure.

Each call advances one stage, so the player drives a build with repeated actions ("build a bow", …), which is also what lets a cure sit between two of them.

---

## The gate (same discipline as V53)

An assembly is not canon until reviewed. `assembly_review` records deterministic findings and the migration promotes only what is clean:

- **BAD_PREREQUISITE** (BLOCKING) — a stage whose prerequisite is not an earlier stage of the same assembly (a cycle, a forward reference, or a leak across assemblies).
- **NO_STAGES** (BLOCKING) — nothing to advance.
- **UNOBTAINABLE_REQUIREMENT** (BLOCKING) — a stage needs an item with no acquisition path (V51's `item_source`).
- **EMPTY_WORK_STAGE** (ADVISORY) — a non-cure stage with no requirements produces its result from nothing.

Craft outputs are registered in `item_source` as `TECHNIQUE 'assembly:<key>'` before the review runs, so a brand-new craft's own output is not read as unobtainable — the assembly *is* its source, exactly as a process is the source of its output.

Three of these also became **standing Auditor invariants** (`PersistentStateAuditor`): a migration guards only the rows present when it runs, so cyclic stages, stageless assemblies, and unobtainable stage inputs are re-checked continuously, plus a "verified-but-flagged" drift check.

---

## What shipped

| Assembly | Kind | Notable |
|----------|------|---------|
| `bow` | CRAFT | shape → back → **cure (480 min)** → string → `hunting_bow`. The flagship: a portable craft with a real cure. |
| `drying_rack` | STRUCTURE | set posts → lash crossbars. A sited structure defined **entirely in data** — proves the schema needs no code per structure. |
| `lean_to`, `stone_fire_pit` | STRUCTURE | Expressed as staged data to prove the migration path. Their existing intents (`START_LEAN_TO`, `BUILD_FIRE_PIT`) are matched earlier and still build them unchanged; the runtime cut-over onto the engine is deliberately deferred so no existing behaviour regressed. |

### Verified end-to-end (running stack, V58)

- Bow: all four stages; the cure correctly gated ("still curing — about 8 more hours"), completed after a sleep, produced `hunting_bow`, consumed all components, instance `COMPLETE`.
- Drying rack: raised through both stages as a `DRYING_RACK` construction.
- Lean-to and fire-pit: build identically through their old paths (`START_LEAN_TO` → 4× `WORK_LEAN_TO` → COMPLETED; `BUILD_FIRE_PIT` → COMPLETED).
- Regression on all ends: 93 unit tests green, 58 migrations apply via Flyway, launch audit clean (incl. the new assembly invariants), 83/83 reachability probe, 0/65 dispatch shadowing, 46/46 procedure simulation, frontend builds.

---

## M3b — Production quality

Quality is an **ordered grade** — `DEFECTIVE < POOR < SOUND < FINE` (V59) — carried by every made item (`item_instance.quality_grade`) and by every stage of a build (`assembly_stage_completion.quality_grade`). It is set when a thing is made and flows forward by one rule:

> `outputGrade = worst(worst input grade, attempt grade)`

The **attempt grade** is Layer 2 of the success model made concrete (`QualityGrade.attempt`): a careless description ("rush", "botch", "slap together") spoils the work to DEFECTIVE; a careful, detailed one ("carefully … evenly … slowly", ≥6 words) earns FINE; anything ordinary is SOUND. It never lifts an output above its inputs — `worst` sees to that — so a POOR hide can never yield a fine result, and a fine result needs both fine materials and care.

- **Flow** — process outputs (`runProcess`) and craft outputs (final assembly stage) are graded by the rule; a cure stage carries its prerequisite's grade forward (waiting well is just waiting). A finished craft inherits the **worst** of its stages.
- **Gating** — a defective material, or a defective prior stage, refuses to be built on: the next step fails with "…must be reworked before you can go on." This is why a defect can never reach a finished piece, and the Auditor invariant (no completed assembly holds a defective stage) is the backstop.
- **`INSPECT`** — witness-stance, no advice: reports an in-progress assembly's grade stage by stage (naming any defective stage) or, with nothing in hand, how many carried pieces are defective or poor. It sits before the generic `OBSERVE` catch and needs a quality/assembly context to claim "inspect".
- **`REWORK`** — strips the earliest defective stage completion and everything after it, returning the build to that step to be done again. The flawed work is undone, not the whole piece; the materials it spoiled are gone, so redoing it needs fresh ones (a botched sinew backing is scraped off, not recovered).

Verified end-to-end: a bow whose backing was botched with "rush the work on the bow" completed that stage DEFECTIVE, which gated the cure; `INSPECT` named "the lay on the sinew backing" as defective; `REWORK` stripped it; a careful redo made it SOUND; and the finished `hunting_bow` came out SOUND with a clean audit. Separately, a POOR fibre run through `twist_cordage` yielded POOR cordage — quality flowing, deterministically.

## Notes for the next milestone

- **Routing collisions with intents recur.** "drying rack" was initially stolen by `CRAFT_SHELF` (which treats "rack" as a shelf); the fix excluded "drying". Any new assembly whose keywords overlap an existing intent will be shadowed — the assembly engine sits in the fallback. See [[project-intent-classifier-substrings]] for the pattern; consider a general assembly-defer gate (like `actionMatchesProcess`) if collisions multiply.
- **FINE is reachable but rarely produced yet:** raw materials default SOUND, so FINE only appears from a careful attempt on already-fine inputs. FINE-yielding sources (a prime hide from a clean kill, select timber) are content for a later pass — the dimension and its flow are in place.
- **M4** is mostly shipped as data already (V57); what remains is the cut-planning multi-output process form and wiring building materials to be consumed by stages, both of which sit naturally on this schema.

---

## Related

| Document | Relevance |
|----------|-----------|
| [action-routing-hardening.md](action-routing-hardening.md) | M3's spec and the milestone sequence |
| [routing-and-coverage-strategy.md](routing-and-coverage-strategy.md) | Why the foundation resolves deterministically; where AI belongs |
| `V58__staged_assembly.sql` | The schema, the seed assemblies, and the review gate |
| `V53__process_review_gate.sql` | The gate this one mirrors |
