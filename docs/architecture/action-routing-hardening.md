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

| # | Action text | Wrongly matched | Module that should never have been consulted | Survives word-boundary fix? |
|---|-------------|-----------------|---------------------------------------------|:---:|
| 1 | "gather mushrooms from the **forest** floor" | `ore` | Mineral prospecting | no |
| 2 | "install doors and windows with **flashing**" | `ash` | Ash gathering | no |
| 3 | "**weatherproof** the shell with cladding" | `weather` | Weather simulation | no |
| 4 | "build the floor frame with **beams**" | `beam` | Timber reinforcement | no |
| 5 | "**split** the fish into thin strips" | `split_planks` | Plank splitting (needs an axe and a log) | **YES** |
| 6 | "**shape** the bow blank from a branch" | `shape_components` | Component shaping (needs a timber plank) | **YES** |
| 7 | "assemble the bow with **cordage** and sinew" | `twist_cordage` | Cordage twisting (would make more cordage, not assemble) | **YES** |
| 8 | "process resin into binding compound with **ash**" | `gather_ash` | Ash gathering (not pitch rendering) | **YES** |

Occurrences 1 and 4 were caught by tests. Occurrences 2 and 3 were caught only by simulating a construction procedure by hand. **Occurrence 5 was caught by the fish-preservation simulation, and 6–8 by the bow-production simulation, all *after* the word-boundary fix (d0e238c) had shipped.** They are live false positives that whole-word matching does not remove, because the colliding words — split, shape, cordage, ash — are legitimate whole words in two modules at once.

Occurrences 6–8 are the sharpest evidence, because they show the failure is not merely wrong-module but **wrong-recipe**. "assemble the bow with cordage and sinew" does not route to *some* construction step; it routes to the process that *makes cordage*, because the material named in the sentence collided with a process keyword. Routed live, an assembly instruction would produce raw cordage. A COVERED verdict that looks correct at the route level can still be pointing at the wrong process underneath, which is why the fix must scope by activity category before matching, not after.

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

## Second fixture: primitive fish preservation

A ten-step primitive fish-preservation procedure was routed the same way.

**Result: 1 COVERED (false), 1 POLISH, 8 INVENT.**

| Step | Route | Note |
|------|-------|------|
| Clean and gut the fish | INVENT | no butchery-of-fish process |
| **Split into thin strips** | **COVERED (false)** | matched `split_planks` — collision #5 above |
| Salt every surface | INVENT | **no salt exists in the world at all** |
| Build a smoke rack over a fire | POLISH | matched the `fire` domain; it is really construction |
| Smoke until firm and dry | INVENT | no smoking process |
| Build a drying rack in sun and air | INVENT | no drying rack, no solar drying |
| Dry until leathery | INVENT | no active drying process |
| Cool and store | INVENT | `FoodPreservationService` is a spoilage clock, not a preservation act |

Two findings beyond the routing collision:

- **Salt does not exist as a material.** A preservation method that predates recorded history has no representation. This is the same reachability lesson as the fire kit: a procedure the whole world once depended on is simply absent.
- **Preservation is modelled as decay, not as action.** `FoodPreservationService` tracks how fast food spoils. Nothing lets a chronicle *slow* that through smoking, salting, or drying. The verbs exist in every survival manual and in none of the foundation.

The fish procedure is the food-domain counterpart to the cabin: the cabin fails at staged construction, the fish fails at active preservation, and both surface the same routing defect on the way down.

## Third fixture: primitive bow production

A twelve-phase primitive bow workflow was routed the same way.

**Result: 5 COVERED (three of them false), 1 POLISH, 6 INVENT.**

The three false COVEREDs are collisions #6–#8 above. What the bow adds beyond the routing defect is **three mechanics no milestone yet covers**, all in the second half of any real production workflow:

| Phase | Route | Missing mechanic |
|-------|-------|------------------|
| Inspect materials / components / finished item | INVENT | **Quality inspection** — no notion that a material or component has condition that gates its use |
| Refine components to consistent dimensions | INVENT | Component refinement short of the finished-item REFINE intent |
| Assemble blank + cordage + pitch + sinew into a bow | false COVERED | **Staged assembly of a portable item** — same gap as the cabin, but the output is carried, not sited |
| Cure while compounds harden | INVENT | **Curing time** — a stage that completes on elapsed time, not on inputs |
| Return failed item to a prior stage for rework | INVENT | **Rework loop** — a backward stage transition on failed inspection |

The single most important finding: **staged assembly is not construction-only.** The bow is a multi-component staged assembly whose product is a portable item. The M3 stage model, scoped in this document to *sited* construction, must also serve *portable* crafts, or every non-trivial tool — bow, trap, sled, loom — hits the same wall the cabin does.

The bow, its string, and arrows also do not exist as items (fire_bow and bow_drill are fire-making kit, not weapons), which is an ordinary vocabulary gap for M4.

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

### M3 — Staged assembly (sited **and** portable)

**Scope:** Give multi-stage things — structures *and* multi-component crafts — somewhere to exist. This is the gap that blocks the cabin and the bow outright. Revised from "sited construction only" after the bow fixture showed a portable craft hits the identical wall.

- [ ] `assembly_stage` table — subject kind, stage order, name, prerequisite stage (serves both construction kinds and craft recipes)
- [ ] `assembly_stage_requirement` — stage, item, quantity, tool class
- [ ] `assembly_progress` — per-instance stage completion, replacing bare `progress_percent`
- [ ] Stage dependency enforcement: a stage cannot begin before its prerequisite completes
- [ ] **Cure stages**: a stage may complete on elapsed world time rather than on inputs (the bow's compounds hardening), reusing the physiology/food tick clock
- [ ] `ADVANCE_ASSEMBLY` intent resolving the next available stage, for both a sited project and a carried in-progress craft
- [ ] Narration per stage, witness-stance
- [ ] Auditor invariants: no orphan stages, no cyclic prerequisites, no stage whose requirements are unobtainable
- [ ] Migrate `LEAN_TO` and `STONE_FIRE_PIT` onto stages without behaviour change; add the bow as the first portable staged craft

**Done when:** an existing lean-to still builds identically, a multi-stage sited structure can be defined entirely in data, and a portable multi-component craft (the bow) can be built through its stages including a cure.

---

### M3b — Production quality: inspection and rework

**Scope:** The quality dimension every real workflow has and the foundation has none of, surfaced by the bow's inspect-and-rework phases.

- [ ] Material and component **condition** as a first-class attribute (sound / worn / defective), set at creation and readable at use
- [ ] `INSPECT` intent — reports the condition of carried materials or an in-progress assembly, separating usable from defective; witness-stance, no advice
- [ ] Defective inputs gate the stages that would consume them, rather than silently producing a poor result
- [ ] **Rework**: a failed inspection on an assembly returns it to the appropriate prior stage rather than destroying it
- [ ] Finished-item quality derives from the condition of its inputs and the specificity of the assembly attempts (ties into the existing three-layer success model)
- [ ] Auditor invariant: no assembly may reach a terminal stage while carrying a component flagged defective

**Done when:** a bow built from defective components inspects as defective and can be reworked rather than only discarded, and quality flows from inputs to output deterministically.

---

### M4 — The building and preservation vocabulary

**Scope:** Close the material and technique gaps the cabin and fish simulations exposed.

Building:
- [ ] Framing members: joist, stud, plate, rafter, beam, purlin — as processed timber
- [ ] Joinery: wooden peg, mortise-and-tenon, structural lashing, notching
- [ ] Foundation: pier stone, sill beam, ground preparation, drainage cut
- [ ] Building layers: cladding board, shingle, thatch, insulation batt, vapour layer
- [ ] Preservation: char-treated timber, pitch-coated timber

Food preservation (from the fish fixture):
- [ ] Salt as a gatherable/producible material (evaporation, coastal, or mineral source)
- [ ] Butchery-of-fish process producing fillets/strips distinct from wood splitting
- [ ] Active preservation acts — salting, smoking, drying — that *reduce* a food's spoilage rate rather than only tracking it
- [ ] Smoke rack and drying rack as construction kinds (depends on M3)
- [ ] `smoked_fish`, `dried_fish`, `salted_fish` as outputs with slower spoilage than `raw_fish`

Weapons and tools (from the bow fixture):
- [ ] `bow_stave` / bow blank shaped from a hardwood branch (distinct from `wooden_component` off a plank)
- [ ] `bow` (weapon), `bowstring`, `arrow_shaft`, `arrow` as items, with a source and a review-passed recipe each
- [ ] Binding compound recipe reconciled: the bow workflow uses resin + charcoal + **ash**; the foundation's `render_pitch` uses resin + charcoal. Decide whether ash is a real third input or the simpler recipe stands.

- [ ] Every addition passes the V53 review gate before going live
- [ ] Reachability invariant stays at zero unreachable

**Done when:** the twelve cabin steps, the ten fish steps, and the twelve bow phases each route to COVERED or POLISH, none to INVENT on vocabulary grounds, and no false COVERED remains.

---

### M5 — Coverage as a standing measure

**Scope:** Make foundation drift visible before it becomes API spend.

- [ ] Extend `/api/domains/coverage` with per-category coverage
- [ ] Record every INVENT assessment as a gap row with the triggering text
- [ ] Rank gaps by frequency — the most-requested absent capability first
- [ ] Simulation harness: route a named procedure through the foundation and report per-step routes
- [ ] Fixtures: the 15 m² cabin (construction), primitive fish preservation (food), and primitive bow production (staged craft) as the first three
- [ ] Regression: no fixture's coverage may decrease, and none may regain a false COVERED
- [ ] The harness must report false COVERED separately from true COVERED — the bow showed a route can read COVERED while pointing at the wrong process, so route counts alone are not enough

**Done when:** a coverage regression fails the build rather than surfacing in play.

---

## Sequencing and risk

M1 and M2 are the hardening proper and should land together — M2 without M1 has no category to filter by, and M1 without M2 changes nothing. M3 (staged assembly, sited and portable) is independent and can proceed in parallel; it is the larger piece of work. M3b (inspection and rework) depends on M3, since it acts on stages and components. M4 depends on M3, since building and craft materials need stages to be consumed by. M5 is small and should follow M2 so the measure reflects scoped routing.

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
