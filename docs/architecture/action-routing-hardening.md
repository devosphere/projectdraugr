# Action Routing Hardening

> **Project Draugr — Architecture**
>
> *Know what kind of thing the chronicle is doing before deciding which module answers.*

---

## Purpose

This document specifies the hardening layer that sits between a player's action text and the modules that resolve it: **category-scoped routing**.

Today every matcher in the system searches its own vocabulary against the raw action text, and all of them search the same text independently. Nothing establishes what *kind* of activity is being attempted first. The result is that unrelated modules match on incidental letters, and the failure is silent.

This is not a hypothetical risk. It has now occurred four times.

---

## The defect, stated precisely

Matching is **lexical and global** where it needs to be **semantic and scoped**.

### Recorded occurrences

| # | Action text | Wrongly matched | Module that should never have been consulted |
|---|-------------|-----------------|---------------------------------------------|
| 1 | "gather mushrooms from the **forest** floor" | `ore` | Mineral prospecting |
| 2 | "install doors and windows with **flashing**" | `ash` | Ash gathering |
| 3 | "**weatherproof** the shell with cladding" | `weather` | Weather simulation |
| 4 | "build the floor frame with **beams**" | `beam` | Timber reinforcement |

Occurrences 1 and 4 were caught by tests. Occurrences 2 and 3 were caught only by simulating a construction procedure by hand.

### Why word boundaries are not the fix

Whole-word matching was applied in `d0e238c` and removes this specific set. It does not remove the class, because the class does not depend on substrings:

- **"dress the stone"** and **"dress the hide"** share a verb across stoneworking and leatherwork.
- **"fire the pot"**, **"fire the kiln"**, and **"light a fire"** share a noun across pottery and ignition.
- **"plant a seed"** and **"gather a plant"** share a word across agriculture and flora.
- **"draw a map"**, **"draw water"**, and **"draw a bow"** share a verb across three unrelated domains.

Every one of these is a legitimate whole word in both modules. No amount of boundary tightening separates them, because the ambiguity is real. Only knowing the **activity category** first resolves it.

### Why the failure direction matters

A false negative is visible: the player's action does nothing and they try again.

A **false positive is invisible and worse**. When the router wrongly answers `COVERED`, it suppresses the Architect call that the situation actually required. The gap is never recorded, no schema is ever added, and the action becomes permanently unresolvable — a silent dead end rather than a visible absence. The cabin simulation showed four of twelve steps failing this way.

---

## Evidence: the 15 m² cabin simulation

A standard twelve-step timber cabin procedure was routed through the foundation.

**Result after the boundary fix: 0 COVERED, 0 POLISH, 12 INVENT.**

Before the fix, four steps falsely reported coverage.

### What the foundation genuinely holds

Felling, plank splitting, component shaping, structural timber, dressed stone grades, cordage, pitch rendering, hide tanning.

### What it does not

| Gap | Consequence |
|-----|-------------|
| No site survey or ground preparation | Levelling, compacting, drainage unrepresentable |
| No foundation concept | Piers, frost depth, bearing have nowhere to live |
| No framing vocabulary | Joists, studs, plates, rafters, spans unknown |
| **No staged assembly model** | See below — the deepest gap |
| No joinery | Pegs, mortise, structural lashing absent |
| No building layers | Cladding, weatherproofing, insulation absent |
| No timber preservation | Charring, pitch coating absent |

### The structural gap beneath all of them

`material_process` produces **carried items**. A cabin is a **sited, staged construction** whose stages depend on one another — a wall cannot be raised before a floor exists.

`construction_project` carries only `progress_percent`. It cannot express "floor complete, walls not started". So even with every material present and every recipe defined, a cabin has nowhere to exist.

This is why the cabin is the right test case: it fails at the architecture, not at the vocabulary.

---

## The architecture

### Two-phase resolution

```
action text
    │
    ▼
┌─────────────────────────────────────────┐
│ Phase 1 — ActivityClassifier            │
│ What KIND of activity is this?          │
│ Returns exactly one ActivityCategory.   │
└─────────────────────────────────────────┘
    │
    ▼  category
┌─────────────────────────────────────────┐
│ Phase 2 — scoped resolver               │
│ Match ONLY within that category's       │
│ vocabulary. Other modules are not       │
│ consulted and cannot collide.           │
└─────────────────────────────────────────┘
```

### Activity categories

| Category | Covers | Modules in scope |
|----------|--------|------------------|
| `ACQUIRE` | gathering, mining, foraging, felling, drawing water | flora, minerals, insects, water |
| `HUNT` | tracking, confronting, trapping, fishing, taming | wildlife, monsters, traps |
| `PROCESS` | transforming carried material | material_process |
| `CRAFT` | making a portable object | techniques producing items |
| `CONSTRUCT` | building a sited, staged structure | construction |
| `MAINTAIN` | repair, preservation, treatment | construction, materials |
| `INHABIT` | rest, sleep, eat, drink, personal acts | physiology |
| `RECORD` | writing, mapping, naming, designating | literature, navigation |
| `OBSERVE` | surveying, examining, tracking signs | perception |
| `MOVE` | travel, exploration | geography |

A module belongs to one or more categories. **A matcher may only see vocabulary from categories the classifier selected.** `weather` is not in `CONSTRUCT`'s namespace, so "weatherproof" can never reach it.

### Scoping is enforced in data, not convention

Every matchable term carries its categories. A resolver that queries without a category filter returns nothing rather than everything — the safe direction. This is enforceable by an Auditor invariant.

---

## Milestones

### M1 — Category vocabulary and the classifier

**Scope:** Establish activity categories as first-class data and classify action text into exactly one.

- [ ] `activity_category` table — key, display name, description
- [ ] `category_term` table — term, category, weight, term kind (VERB / NOUN / OBJECT)
- [ ] Seed terms for all ten categories from existing intent keywords
- [ ] `ActivityClassifier.classify(text) → ActivityCategory` with confidence
- [ ] Ambiguity handling: when two categories score within tolerance, return the higher-weight one and record the contest for review
- [ ] Unit tests covering every documented collision above, asserting the *correct* category
- [ ] Auditor invariant: no term assigned to more than three categories without a recorded reason

**Done when:** all four recorded collisions classify correctly, and the classifier is a pure function with no DB dependency in the hot path (cached vocabulary).

---

### M2 — Scoped resolution

**Scope:** Make every existing matcher category-aware; remove global matching.

- [ ] Add category columns to `material_process`, `technique_definition`, `construction_kind`, `fire_method`, `mineral_definition`, `flora_definition`
- [ ] Backfill categories for all existing rows
- [ ] `ArchitectRouter.assess` takes a category and filters every query by it
- [ ] `PhysicalItemService.runProcess` filters by category
- [ ] Intent classification consults the category before its keyword chain
- [ ] Auditor invariant: every matchable row has at least one category
- [ ] Regression tests: the four collisions, plus the ambiguous pairs (`dress`, `fire`, `plant`, `draw`)

**Done when:** no matcher queries its vocabulary without a category filter, and the cabin simulation produces no false coverage.

---

### M3 — Staged construction

**Scope:** Give sited, multi-stage structures somewhere to exist. This is the gap that blocks the cabin outright.

- [ ] `construction_stage` table — project kind, stage order, name, prerequisite stage
- [ ] `construction_stage_requirement` — stage, item, quantity, tool class
- [ ] `construction_progress` — per-project stage completion, replacing bare `progress_percent`
- [ ] Stage dependency enforcement: a stage cannot begin before its prerequisite completes
- [ ] `ADVANCE_CONSTRUCTION` intent resolving the next available stage
- [ ] Narration per stage, witness-stance
- [ ] Auditor invariants: no orphan stages, no cyclic prerequisites, no stage whose requirements are unobtainable
- [ ] Migrate `LEAN_TO` and `STONE_FIRE_PIT` onto stages without behaviour change

**Done when:** an existing lean-to still builds identically, and a multi-stage structure can be defined entirely in data.

---

### M4 — The building vocabulary

**Scope:** Close the material and technique gaps the cabin exposed.

- [ ] Framing members: joist, stud, plate, rafter, beam, purlin — as processed timber
- [ ] Joinery: wooden peg, mortise-and-tenon, structural lashing, notching
- [ ] Foundation: pier stone, sill beam, ground preparation, drainage cut
- [ ] Building layers: cladding board, shingle, thatch, insulation batt, vapour layer
- [ ] Preservation: char-treated timber, pitch-coated timber
- [ ] Every addition passes the V53 review gate before going live
- [ ] Reachability invariant stays at zero unreachable

**Done when:** the twelve cabin steps route to COVERED or POLISH, and none to INVENT on vocabulary grounds.

---

### M5 — Coverage as a standing measure

**Scope:** Make foundation drift visible before it becomes API spend.

- [ ] Extend `/api/domains/coverage` with per-category coverage
- [ ] Record every INVENT assessment as a gap row with the triggering text
- [ ] Rank gaps by frequency — the most-requested absent capability first
- [ ] Simulation harness: route a named procedure through the foundation and report per-step routes (the cabin as the first fixture)
- [ ] Regression fixture: cabin coverage must not decrease

**Done when:** a coverage regression fails the build rather than surfacing in play.

---

## Sequencing and risk

M1 and M2 are the hardening proper and should land together — M2 without M1 has no category to filter by, and M1 without M2 changes nothing. M3 is independent and can proceed in parallel; it is the larger piece of work. M4 depends on M3, since building materials need stages to be consumed by. M5 is small and should follow M2 so the measure reflects scoped routing.

**Principal risk:** backfilling categories across every existing matchable row (M2) is broad and mechanical. A missed row silently stops matching. The Auditor invariant requiring a category on every matchable row is the guard, and it must land in the same migration as the columns, not after.

**Explicitly out of scope:** AI-assisted classification. The classifier must stay deterministic and free. Routing exists to *avoid* API calls; a router that costs a call defeats itself.

---

## Related

| Document | Relevance |
|----------|-----------|
| [narration-engine.md](narration-engine.md) | NarrationRouter — the same routing principle for prose |
| [domain-creation-pattern.md](domain-creation-pattern.md) | How the Architect adds a domain |
| [core-agent-boundaries.md](core-agent-boundaries.md) | What each of the three agents may do |
| `V52__material_processing.sql` | The declarative process table this scopes |
| `V53__process_review_gate.sql` | The Auditor gate new vocabulary passes through |
