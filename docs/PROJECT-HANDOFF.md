# Project Draugr — Session Handoff

This file exists so that if a session crashes or runs out of context, a new one can resume immediately without re-explanation. **Read this first before doing anything.**

It is version-controlled deliberately. The machine-local entry point (`CLAUDE.md`) is gitignored and points here, so continuity survives losing the machine.

---

## What This Game Is

A survival simulation where the player is a person from 2026 Earth who has been transported to a primitive world with nothing but the clothes on their back. They must survive using only the knowledge they personally carry from their real life.

**Every player will approach this differently based on their actual expertise.** The developer (Juan) is a QA Lead and Technical Product Manager — his playthrough produced extensive documentation, settlement planning, and archives on Day 1. Another player who is a carpenter would build structures first. A hunter would pursue a bow and arrow. A nurse would prioritize wound treatment. There is no prescribed path. The sky is the limit.

This is the differentiator that makes this game significant. Execute it stably and it will be big.

---

## Core Design Rules — Never Violate These

### 1. All actions through the action composer
No UI buttons trigger player actions. The action composer (natural language input, up to 2500 characters) is the only interface for player intent. Panels (inventory, equipment, physiology, literature) are read-only context displays.

**Exception:** Clicking a literature or map entry in the menu opens the document reader. This is content access, not an action trigger.

### 2. The narrator is a witness — never a guide
The narrator describes:
- What the player did (the attempt, the procedure)
- What the player perceives (sensory, environmental)
- The outcome (success, failure, nothing)
- The surroundings and atmosphere

The narrator **never** hints at what to do next, explains why something failed, or suggests what the player is missing. "You press your palms into the earth. The soil crumbles and yields nothing." — correct. "You need clay-rich soil to gather clay." — never.

The Body HUD, weather/time header, and menus are the player's information layer. If a player is uninformed, they fail. Death from ignorance is a valid and intended outcome.

### 3. Knowledge gating is physical only
No XP bars. No skill unlock trees. No "you must reach level 2 before you can do X."

The only gates are:
- **Materials** — you cannot make charcoal without fire
- **Tools** — you cannot knap without a hard stone
- **Environment** — you cannot start a friction fire in a downpour

A player who watched survival documentaries their whole life can attempt advanced techniques on Day 1. Whether they succeed depends on what they have and how well they described what they're doing.

### 4. The three-layer success model
Every action's outcome is determined by:

1. **Physical prerequisites (hard gate)** — materials, tools, environment. Fail this = nothing happens. Narrator describes the futile attempt. No hint about what's missing.

2. **Action text specificity** — for actions the chronicle hasn't mastered, how precisely and accurately the player describes the technique determines success probability. "Light a fire" from a novice = very low chance. Full bow-drill procedure described accurately = high chance. The 2500-character action composer IS the skill system.

3. **chronicle_capability_adaptation** — after enough successful repetitions, the chronicle carries passive knowledge. A terse "kill the boar" from a chronicle who has hunted boar 20 times succeeds because the body knows. Same words, different chronicle, different outcome.

### 5. Realism as the rule of thumb
When designing any feature, ask: what would actually happen in real life? Clay is gatherable wherever it occurs naturally. Fire requires ignition. A boar will kill a person who charges it bare-handed. Stones plus one dry branch does not make fire. The simulation must be rational and logical.

### 6. World persistence and chronicle legacy
The world is ONE shared persistent entity. When a chronicle dies:
- All `world_object` rows they created/gathered remain (unless naturally decayed)
- All constructions remain (decay per existing integrity system)
- All literature/maps/archives remain at their last location
- The new chronicle spawns at a RANDOM location — retrieving the old chronicle's legacy requires exploration or luck

This makes literature the most powerful mechanic: a new chronicle stumbling onto a dead chronicle's archive gains wisdom the game never directly provided. The narrator describes finding it; the player reads it and decides what to do with that knowledge.

### 7. Chronicle capability is personal — schema is shared
- **chronicle_capability_adaptation** rows are per-chronicle. When a chronicle dies, their familiarity scores die with them. A new chronicle starts at zero even in a world full of kilns and forges.
- **DB schema (tables)** is monotonic and global. When a domain (pottery, smithing, etc.) is invented for the first time anywhere in the world's history, the Persistent State Architect adds its tables ONCE. All future chronicles inherit a world where those tables exist.
- The distinction: you can find a dead chronicle's pottery kiln (world_object persists) and learn to use it (your own capability rows grow). You do not inherit their skill.

---

## Tech Stack

- **Backend:** Java 21, Spring Boot, raw JdbcTemplate (no ORM), PostgreSQL, Flyway migrations
- **Frontend:** React + Vite + TypeScript
- **Launcher:** Electron-style via PowerShell scripts
- **Maven:** bundled under `.tools/apache-maven-3.9.11/` and NOT on PATH — always invoke by full path (see `CLAUDE.md` for the absolute path on this machine)
- **Git identity:** `devosphere.tech` / `devosphere.tech@gmail.com` (repo-scoped config)
- **Commit messages:** write to a file and use `git commit -F <file>`. PowerShell 5.1 mangles here-strings containing parentheses or quotes, and bash heredocs do not work there at all.

## Branching

`development` is the integration branch and where work lands. `main`, `staging`, and `production` are promotion targets — do not commit to them directly.

Work on `development`; it tracks `origin/development`. If a local branch ever ends up ahead of `origin/main` by a large count, that is the symptom of having committed to `main` by mistake.

## Key Files

| File | Purpose |
|------|---------|
| `backend/src/main/java/com/devosphere/draugr/action/ChronicleActionService.java` | Intent classification + action dispatch. All new intents go here. |
| `backend/src/main/java/com/devosphere/draugr/item/PhysicalItemService.java` | Item creation, crafting, gathering, equipment, `runProcess()`. |
| `backend/src/main/java/com/devosphere/draugr/literature/LiteratureService.java` | Document creation and revision. |
| `backend/src/main/java/com/devosphere/draugr/action/SuccessModel.java` | Layer 2 text specificity scoring + deterministic roll. |
| `backend/src/main/java/com/devosphere/draugr/capability/CapabilityAdaptationService.java` | Layer 3 familiarity() — reads chronicle_capability_adaptation. |
| `backend/src/main/java/com/devosphere/draugr/audit/PersistentStateAuditor.java` | Read-only invariant checks. Never add mutation methods here. |
| `backend/src/main/java/com/devosphere/draugr/domain/ArchitectRouter.java` | Cost gate for the Architect — routes COVERED / POLISH / INVENT. |
| `backend/src/main/java/com/devosphere/draugr/routing/ProcessMatcher.java` | The **only** implementation of the action→process resolution rule. Both `runProcess()` and `ArchitectRouter` go through it. |
| `backend/src/main/java/com/devosphere/draugr/routing/RoutingMissRecorder.java` | Records unresolved actions into the V56 backlog. Separate bean on purpose — see its Javadoc. |
| `backend/src/main/resources/db/migration/` | Flyway migrations V1–V57. Next is V58. |
| `backend/src/main/java/com/devosphere/draugr/domain/DomainRegistryService.java` | Reads domain_registry — the Architect's ledger of invented domains. |
| `docs/architecture/domain-creation-pattern.md` | The exact recipe for adding a new domain. |
| `docs/architecture/action-routing-hardening.md` | Sprint 003 spec — collisions, milestones M1–M5. |
| `docs/architecture/routing-and-coverage-strategy.md` | **Decision record: no AI at resolution time.** Read before proposing one. Also: how to read the coverage backlog. |
| `frontend/src/PlaythroughScreen.tsx` | Main game UI. |

## Architecture Patterns

- **Adding a new action:** Intent enum → duration switch → classifier keyword check → dispatch block → service method. Always in that order.
- **Items:** `world_object` row + `item_instance` row + `object_transition` row for provenance.
- **Chronicle identity:** One life per player. Permanent death. `world_object.id` = `chronicle.id` (same UUID).
- **Physiology:** Ticks on real-world elapsed time, independent of action resolution.
- **Literature:** `literature_document` + `literature_revision`. Surface types: `bark_sheet`, `animal_hide`, `stone_slab`.
- **Destruction:** Objects are NEVER deleted. `lifecycle_state='DESTROYED'`, `destroyed_location_id`, `destroyed_cause` all set. `current_location_id` and `current_owner_id` go NULL. Only junction rows (equipment_attachment, item_containment) are hard-deleted.
- **World persistence:** One `world_genesis` row. Chronicle dies → new chronicle same world_id. Old objects remain.

---

## What Is Built (Migrations V1–V57, all applied)

- V1–V30: World geography, ecology, chronicle lifecycle, physiology, action ledger, wildlife, items, equipment, carry capacity, capability adaptation, construction, literature, fire, weather, food preservation, tools, clothing, idempotency
- V31: Writing materials (bark_sheet, charcoal, clay_lump; STRIP_BARK, MAKE_CHARCOAL, GATHER_CLAY, WRITE)
- V32: Fire-making kit (hearth_board, fire_spindle, tinder_nest) — LIGHT_FIRE now requires ignition chain
- V33: Stone slab writing surface (stone_slab, gatherable from MOUNTAIN/HIGHLAND biomes)
- V34: chronicle_named_location table (player-designated settlement zones)
- V35: Furniture item definitions (wooden_desk, wooden_chair, stone_shelf; category FURNITURE)
- V36: Navigation memory (location_marker, chronicle_chunk_visit; memorized + last_visited_at)
- V37: Destruction provenance (destroyed_location_id + destroyed_cause on world_object)
- V38: Domain registry (domain_registry — the Architect's ledger of invented domains, seeded with 22 PREBUILT domains)

### Sprint 002 — Ecosystem Expansion + Narration Foundation — ALL COMPLETE
- V39: Flora (flora_definition/flora_drop/chunk_flora; 33 species; GATHER_PLANT, FELL_TREE)
- V40: Insects (insect_colony_kind/product/colony; 11 colonies; RAID_HIVE, COLLECT_INSECTS)
- V41: Wildlife species registry (wildlife_species — movement_class, base_resistance, ambush/pack/territorial, tamability) + 6 FSM cascade rules in WildlifeSimulationService.applyCascades()
- V42: Aves + Pisces + wildlife_drop (per-species butchery) + aquatic_catch; FISH, SNARE
- V43: Monsters (monster_profile; 9 species as kingdom_class MONSTRUM; FIRE_BREATH, GRAB_AND_CARRY, SWARM_WOUNDS, DISEASE_WOUND, VENOM_WOUND, ITEM_THEFT)
- V44: chronicle_wildlife_event (immutable ledger) + wildlife_sign; passive encounters + TRACK
- V45: wildlife_bond + tamed_yield/tamed_production; TAME
- V46: bait_profile + placed_lure + placed_trap; LURE, SET_TRAP, CHECK_TRAP

**Totals (validated from scratch against Postgres 16):** 35 domains, 66 wildlife species, 33 flora, 11 insect colonies, 9 monsters, 122 item definitions, 97 wildlife drops, 48 flora drops, 124 wildlife signs, 12 baits. 61 DB-free unit tests green.

**Cost-control layer (the reason AI narration is affordable):**
- `ActionInputClassifier` — pre-pass filter before intent classification. Personal acts and aggression carry real physiological/ecological consequence; gibberish and the impossible are intercepted before the tick with no DB write and no AI call.
- `NarrationEngine` — deterministic witness prose for 30 intent×outcome scenes plus a safe generic fallback. Zero token cost, and correct even if the API is down.
- `NarrationRouter` — pure function deciding whether a moment earns an API call. 27 intents always deterministic; death, monsters, taming thresholds, serious wounds, writing, and HIGH-attention OBSERVE always get the call.

### Foundation plan F1–F8 — ALL COMPLETE
- **F1** PerceptionFrame returned with every action (intent, outcome, location, timeOfDay, weather, attention, nearbyObjects, physiology, sinceLastFrame deltas, narration) — the AI seam.
- **F2** Tick-progression deltas: frame.sinceLastFrame reports qualitative transitions since the last frame (hunger Hungry→Starving, weather CLEAR→RAIN).
- **F3** ATTENTION-scaled detail: attentionLevel(text,intent) → HIGH/MODERATE/LOW; at LOW the frame surfaces no ambient objects (death-from-not-looking preserved).
- **F4** Consequence completeness: all 7 death vectors terminate, incl. Fatal Trauma (injury≥100); wildlife wounds scale by confrontation deficit.
- **F5** Auditor catalog expanded 9→17 invariants (literature/fire/carcass/navigation/registry consistency).
- **F6** AuditSentinel: launch gate (ApplicationReadyEvent, optional fatal) + scheduled heartbeat; /api/audit; @EnableScheduling.
- **F7** domain_registry (V38) + DomainRegistryService + GET /api/domains + docs/architecture/domain-creation-pattern.md.
- **F8** DECIDED: monotonic schema, world-scoped objects, chronicle-scoped capability.

### Sprint 003 — Materials Foundation + Routing Hardening (IN PROGRESS)

- V47: Phase 0 heritage — the Wolf Kingdom's proven technology folded into the schema
- V48: Thermal insulation + garments (`insulation_value`; hide_coat/fur_cloak/hide_leggings/hide_boots/fiber_tunic). Fixed 100% hypothermia mortality (three compounding physics bugs: uncapped wind term, no metabolic heat, zero clothing insulation).
- V49: Nine fire ignition methods with difficulty (ember_transfer 14 → hand_drill 88) + `fire_method_requirement`
- V50: `mineral_definition` — flint_stone, iron_pyrite, lens_crystal, field_stone, precision_tool_stone
- V51: Item reachability — `item_source` + `item_unreachable_known`. Every item must have an acquisition path.
- V52: Declarative material processing — `material_process` (+ inputs, input groups). 20 processes. Material chains became data, not Java.
- V53: The Auditor's review gate — `process_review`, `process_mass_balance` view, `conservation_exempt`. A process is not canon until `review_state='VERIFIED'`; `runProcess()` reads VERIFIED only. Caught 9 of the 20 V52 processes creating matter.
- V54: Activity categories + subject gate (schema half of M1+M2). See below.
- V55: Routing category corrections — four processes V54 made unreachable or mis-resolved, plus a migration-time check that a process's own keywords classify to its own category.
- V56: Routing miss backlog — `routing_miss` (frequency-ranked, recorded on the play path only) + `routing_miss_backlog` and `routing_unknown_term` views. Answers the question that directs coverage work: is a gap missing *words* or a missing *mechanic*?
- V57: Foundation process expansion — **the coverage batch.** 109 new processes (20 → 129) + ~105 items across the eight simulation-named gaps: timber preservation, joinery, building layers, salt/food preservation, fish processing, bow production, leather/armour, plus the tools and containers those chains need. Every row written as DRAFT and promoted only by the V53 gate (extended: category-agreement and subject-presence are now BLOCKING findings, not migration exceptions). Verified from a clean DB: 129 VERIFIED, 0 held, 0 advisories.
- V58: **Staged assembly (M3).** `assembly_definition` + `assembly_stage` (+ `assembly_stage_requirement`) define multi-stage structures and crafts in data; `assembly_instance` + `assembly_stage_completion` track one chronicle's progress. A **cure stage** completes on elapsed world time (the same tick the body runs on), which no single-shot process can express. Its own review gate (`assembly_review`) mirrors V53: bad prerequisite (cycle/forward), no stages, unobtainable requirement are BLOCKING. Seeds the bow (portable craft, with a cure), a drying rack (sited structure, entirely in data), and lean-to/fire-pit as data-proof of migration. Engine: `AssemblyService`; dispatched as the `ADVANCE_ASSEMBLY` intent ahead of the material-process fallback.
- V59: **Production quality (M3b).** `quality_grade` (DEFECTIVE < POOR < SOUND < FINE) on `item_instance` and `assembly_stage_completion`, set at creation and flowing `worst(inputs, attempt)` — the attempt read from the action text (`QualityGrade.attempt`, Layer 2 of the success model). `INSPECT` reports grade/defects (witness-stance); a defective input or prior stage gates the next step; `REWORK` rolls an assembly back to its earliest defective stage; a standing Auditor invariant forbids a completed assembly holding a defective stage.
- V60: **Cut-planning + preservation (M4).** `material_process_output` gives a process several outputs at once (yield scaled by grade) — `lay_out_hide` lays a hide into panel + lamellae + cords + offcuts; a reviewed multi-output mass check joins the V53 gate. Preserved food (salted/dried/smoked) now keeps 30–60 days vs 18 h raw (`FoodPreservationService` kinds + `runProcess` registration). Leather armour becomes a V58 staged assembly (`leather_armour`) consuming the laid-out components; a `smoke_rack` structure; fleshed/dehaired hides accepted by `tan_hide` (yield capped to 1 to conserve mass).

**Why routing hardening exists:** matching was lexical and global. "Split the fish" matched `split_planks`; "weatherproof the shell" matched the weather domain. A wrong match doesn't throw — it silently suppresses the Architect call the moment needed, which is worse than an honest gap.

**Correction to the original spec:** category scoping alone only separates 5 of the 8 recorded collisions — "split the fish"/"split the log" are both PROCESS. Scoping needs **two axes**: category *and* subject material. V54 implements both.

**V54 delivers:** `activity_category` (10), `category_term` (~180 weighted terms), `process_subject` (derived from each process's own inputs/outputs so it can't drift), `material_process.category_key` (NOT NULL, backfilled in the same migration so a missed row fails the migration rather than silently ceasing to match). Also broadened V52's subject-bearing keyword phrases to bare verbs — safe only now that category+subject must also agree.

**Resolution rule (documented in full at the foot of V54 — Java must not drift from it):** classify by summed term weight, `precedence` breaks ties; a process matches only if category **and** keyword **and** subject all agree; **if no category term matches, classification is NULL and the category condition is dropped**, not treated as "matches nothing".

**V55 corrects V54's data.** Wiring the Java and probing with ordinary phrasing exposed the opposite failure from the one V54 fixed: `dress_foundation`, `reinforce_timber` and `fire_vessel` had become unreachable by any plausible sentence, and `weave_large_basket` resolved to `weave_textile`. In every case a process's declared category disagreed with the category its own verbs classify to — an axis V54 introduced but never checked. V55 fixes the four and adds the check.

**M1 + M2 are COMPLETE.** `ActivityClassifier` (classification) and `ProcessMatcher` (the full rule) live in `com.devosphere.draugr.routing`; both `PhysicalItemService.runProcess()` and `ArchitectRouter` route through `ProcessMatcher`, so the rule has exactly one implementation. The Auditor carries three new standing invariants. 88 DB-free unit tests green; 57 migrations apply clean from scratch.

**Verified:** 8/8 recorded collisions blocked, 21/21 processes reachable by ordinary phrasing, and every miss correctly diagnosed as vocabulary / keyword / subject / mechanic — `ProcessRoutingTest`.

**Step 2 (coverage) is COMPLETE — V57.** 129 processes now VERIFIED (was 20). The V53 gate was extended to make category-agreement and subject-presence BLOCKING, so an unreachable process is held rather than shipped; it caught 3. A reusable reachability probe — [routing-reachability-probe.sql](architecture/routing-reachability-probe.sql), a SQL replica of the runtime rule — checks every process resolves from ordinary phrasing; it surfaced 7 in-batch collisions, all fixed before landing. Re-run it after any migration that adds processes.

#### SETTLED: no AI at resolution time — read this before proposing one

**`docs/architecture/routing-and-coverage-strategy.md` is the decision record. Read it rather than re-deriving it.**

The question "should an AI layer help the ActivityClassifier work out what the player meant?" has been asked and answered: **no.** Four reasons, in short — (1) ~39 of ~46 probed actions missed because *the mechanic does not exist*, so a classifier would pay a call to confirm emptiness; (2) an LLM handed 20 candidates finds the nearest one, which manufactures exactly the false COVERED that V54/V55 exist to prevent; (3) `runProcess` writes permanent history, and V53's gate works precisely because what it gates is data sitting still — an inline classifier's output is a decision already executed, with nothing left to gate; (4) it grants a fourth agent authority that `core-agent-boundaries.md` never gave it.

**AI goes at authoring time instead** — one call per novel verb ever, one call per process ever, both permanent and compounding. The strategy doc carries the full argument, the backlog queries, the invariants that must not be broken, and the specific evidence that would justify reopening the question.

#### Resume point

1. **DONE — Step 1: measure.** V56 shipped. The backlog is live and directs the rest.
2. **DONE — Step 2: bulk foundation generation through the V53 gate.** V57 landed 109 processes (20 → 129) across the eight simulation-named gaps, promoted only by the extended gate, verified reachable from a clean DB. Still owed: sampled human plausibility review of the batch (design rule #5) — the gate proves conservation and reachability, not that a recipe is good primitive technology.
3. **DONE — Step 3: re-measure.** The four procedure simulations, re-run through the live dispatch, went from ~7/46 to **46/46 resolved**. `routing_miss_backlog` showed **0 `MECHANIC` misses** — the world has the mechanics; the residual gaps were vocabulary and were closed. No further bulk generation needed for the named procedures. Full write-up in [routing-and-coverage-strategy.md](architecture/routing-and-coverage-strategy.md). This pass also fixed the substring-shadowing class of intent-classifier bugs (see [feedback/project memory]) including plant⊃"ant".
4. **M3 (staged assembly) is COMPLETE — V58.** The engine (`AssemblyService` + `ADVANCE_ASSEMBLY`) builds multi-stage things in data, including cure stages that wait out world time. Verified end-to-end against a running stack: the bow builds through all four stages — shape, back, **cure** (correctly gated "still curing ~8h", then completed after sleep), string — producing `hunting_bow` and consuming its components; a drying rack raises as a data-defined sited structure; lean-to and fire-pit build identically through their unchanged intent paths. See [staged-assembly.md](architecture/staged-assembly.md).
5. **M3b (production quality) is COMPLETE — V59.** Grade dimension, INSPECT, gating, and REWORK, all verified E2E (a botched bow inspects defective, reworks, finishes SOUND; POOR fibre → POOR cordage). **M3 and all its sub-milestones are now done.** See [staged-assembly.md](architecture/staged-assembly.md#m3b--production-quality).
6. **M4 (V60) and M5 are COMPLETE. The entire routing-hardening milestone sequence M1–M5 is done.** M4 closed the schema gaps V57's data couldn't (multi-output cut-planning, preservation shelf life, armour-as-assembly). M5 made coverage a build-time gate: `routing-reachability-probe.sql` raises on any miss/false-COVERED under `ON_ERROR_STOP=1`, and `/api/domains/coverage` reports per-category coverage + the ranked backlog.
7. **DONE — post-M5 hardening + playtest fixes (this session).**
   - **V61–V63** landed the primitive felling tool and wider gathering: a craftable one-handed `stone_hatchet` (left-hand) so a tree can realistically be felled without planks; oceanside/`SALT_DEPOSIT` salt (deliberately **not** rivers/streams — those are drinkable freshwater); flint/pyrite eroding out on `RIVER_BANK`; loose vines on forest trees. `SALT_DEPOSIT` is a dedicated deposit biome like `CLAY_DEPOSIT`; its world-generator placement is a deferred follow-up (the affinity is already wired).
   - **GitHub playtest bugs #14/#15/#16 fixed and verified E2E:**
     - **#16 (blocker)** — a fresh chronicle died instantly on its first action after a prior chronicle had aged the world. Root cause: `awaken()` baselined `arrived_at` / `last_metabolic_update` to `Instant.now()` (wall clock) while `ChroniclePhysiologyService.advanceTo` measures elapsed metabolic hours against the **simulated** clock, so the first tick read the whole wall-to-simulated gap as elapsed and starved the newborn. Fix: baseline to `simulation_clock.simulated_at`. Proven at runtime (45-day gap killed the old chronicle; the newborn baselined to sim-time and survived) and guarded by a new `@Order(8)` regression in `FullTickPlaythroughIntegrationTest`.
     - **#15** — the narration timeline now pins to the newest line on append (a `useLayoutEffect` on `narrations` gated by a `pinToBottom` ref that `loadOlderNarrations` clears, so earlier-moment prepends don't yank the view). Browser-verified: scrolled to top, a new action snapped the view to the bottom.
     - **#14** — the narration panel and composer scale with the viewport: width `clamp(20rem, 100vw−29rem, 64rem)` (raised cap, keeps the Body-HUD clearance) and timeline height `clamp(11rem, 100vh−13.5rem, 48rem)`. Browser-verified across 720/460/340px heights.
   - **CI coverage gate is now automatic.** `.github/workflows/verify.yml` gained a `routing` job: a `postgres:16` service, every migration applied `V1..Vn` under `ON_ERROR_STOP=1`, then `routing-reachability-probe.sql` (which `RAISE`s on any miss). The deferred "run the gate in CI" enrichment is closed.
   - **World progression is documented.** [architecture/world-progression.md](architecture/world-progression.md) settles the recurring age-progression questions: Bucket A (data the Architect may author — minerals/recipes/flora, reachable the instant committed) vs Bucket B (terrain/mechanics — new biomes like iron caves or oil/gas reservoirs need human code **and** a Flyway migration into the already-generated world, not just a seed-generator change), plus the sequence for adding an age.
8b. **DONE — playtest bugs #17/#18 (second round).**
   - **#17 (felling vs carry capacity).** A felled trunk was created *owned by the chronicle* and the action then failed on carry capacity, so a tree could never actually come down. Now `fellTree` drops the log(s) on the **ground at the location** (owner NULL) and never fails on weight; the chronicle works the log **where it lies** into carriable pieces. Two supporting changes in `PhysicalItemService`: new `hasAtLeastHere`/`consumeOneHere` let `runProcess` source inputs from carried items **or** items on the ground at the chronicle's location, so `split_planks`/`timber_from_log` consume the felled log in place. Also fixed the classifier: `FELL_TREE` no longer triggers on bare `"log"` and now yields via `actionMatchesProcess` — *"split the oak log into planks"* is `PROCESS_MATERIAL`, not a second felling (it was chopping another tree). Verified E2E (fell → 1 log on ground, 0 carried → split → 2 carried planks) and guarded by `@Order(9)` in `FullTickPlaythroughIntegrationTest` plus a DB-free `IntentClassificationRegressionTest` case.
   - **#18 (Materials & Recipes doc).** The gitignored local `Project Draugr — Materials & Recipes.md` refined to set the right expectation: the resolver matches **one action to one process by verb+material** — it does not read a paragraph of procedure and simulate it (no AI yet). Added a "How the resolver reads your action" section, a step-by-step **fire** sequence (the exact example the issue cited — five declared steps, one per action), and the felling→ground-log→split update. Snapshot bumped to V1–V63. (Local file; not in git.)
9. **DONE — chronicle narration PDF export (pre-3-AI playtest tooling).** A backend endpoint renders a chronicle's *entire* ordered narration (from `ChronicleService.journey`, not the paginated on-screen slice) to a real PDF via OpenPDF (`ChronicleNarrationExporter`): `GET /api/chronicles/{id}/narration.pdf` (any life, living or archived) and `GET /api/chronicles/active/narration.pdf` (the living one), both `application/pdf` with a `chronicle-{seq}.pdf` attachment name; an unknown id is 404. Layout: header + fate/stats line, each entry as a `DAY n · HH:mm UTC` marker + its narration prose, the final-body snapshot for the dead, closing line. Frontend wired at both entry points — the playthrough menu's **Export Chronicle** and the Archive's per-life **Export PDF** (both fetch→blob→download). Verified E2E: valid `%PDF`, correct headers/content, both buttons fire `→ 200` in-browser. A DB-free `ChronicleNarrationExporterTest` reads the output back with OpenPDF's `PdfReader` and asserts a 240-entry life spans **≥15 pages** with opening/middle/closing sentinels all extractable, plus a one-page empty-life case.
10. **IN PROGRESS — three-AI integration.** Plan and boundaries in [architecture/three-ai-integration.md](architecture/three-ai-integration.md). Phased: (1) Simulation Agent narration = Task #21, first; (2) Persistent State Architect = authoring-time only (drains `routing_miss_backlog` into reviewed migrations); (3) Persistent State Auditor = read-only summaries.
    - **DONE — Phase 1a: shared model-client foundation (this session), gated OFF by default.** New `com.devosphere.draugr.ai` package: `AiProperties` (`draugr.ai.*` config), a provider-agnostic `LanguageModel` seam, `AnthropicLanguageModel` on the official `com.anthropic:anthropic-java` SDK, and `AiConfig` which yields a no-op model unless `enabled=true` + a key is present. `SimulationNarrator` turns a `PerceptionFrame` + the deterministic prose into a witness-stance refinement prompt and appends **one** AI sentence — or returns the deterministic prose unchanged on disabled/timeout/error/blank (`LanguageModel.generate` never throws). Config: `DRAUGR_AI_ENABLED` (default false), `DRAUGR_AI_API_KEY`/`ANTHROPIC_API_KEY`, `DRAUGR_AI_MODEL` (default `claude-opus-5`), max-tokens, timeout. Verified: 5 DB-free unit tests (stub model, every fallback branch) green; the Spring context boots clean with the feature off (`AiConfig` logs "AI narration disabled … deterministic prose only").
    - **DONE — Phase 1b: wired into `ChronicleActionService.resolve()`.** After `perception` is final and the frame built: `if (narrationRouter.shouldUseAI(intent,outcome,attention,text,stateChanges,0,null,died,false)) perception = simulationNarrator.refine(frame, perception);` a genuine change re-persists (`UPDATE chronicle_action`) and updates the returned frame. Verified: a HIGH-attention observe (a would-route moment) resolves unchanged with AI off, and the whole context boots clean (30 DB-free tests green).
    - **DONE — Phase 2 groundwork: `PersistentStateArchitect`.** Reads the frequency-ranked `routing_miss_backlog` and drafts a migration proposal per gap (VOCABULARY/KEYWORD/SUBJECT/MECHANIC) on Opus 4.8 — **proposes, never commits** (a human runs it through the V53 gate). Unit-tested against a stub model. Not wired to any runner/endpoint yet.
    - **DONE — Phase 3: the Auditor AI.** `AuditorSummarizer` (Sonnet 4.6) turns a read-only `AuditReport` (consistency flag + violated invariants) into a plain operator summary — describes only, never proposes a repair/write. All three engines now have reachable surfaces: Simulation on the action path; **Architect** at `GET /api/architect/backlog` + `POST /api/architect/propose-top` (drafts a migration; **never applies**); **Auditor** at `GET /api/audit/summary`. Every surface still responds on deterministic data with AI off (`summary`/proposal null), so the mechanism is testable before a key exists. Verified live (AI off): `/api/audit/summary` → `{consistent, violations, summary:null}`, `/api/architect/backlog` → `[]`, `propose-top` → 204. 44 DB-free tests green (11 across the three AI engines).
    - **Per-agent models = the tiering lever (user's business model).** narration → `claude-haiku-4-5`, Architect → `claude-opus-4-8`, Auditor → `claude-sonnet-4-6` (config `draugr.ai.{narration,architect,auditor}-model`; `LanguageModel.generate(model, system, user)` takes the model per call). On launch, subscription tier can select stronger models per account. For solo Engineering-Phase-1 testing the defaults are deliberately modest — the goal is a stable, seamless 3-AI mechanism, not model strength.
    - **DONE — Overseer UI wired (frontend).** `OverseerAgents` (in `?mode=overseer`) surfaces the Auditor (consistency badge + AI prose summary) and the Architect (routing-miss backlog + a "draft a proposal for the worst gap" button showing the drafted migration — review-only, never applied). Both render on deterministic data with AI off; the Simulation Agent needs no separate UI (its output is the playthrough narration). Verified in-browser against a live backend: the Auditor shows "World consistent"; the Architect lists seeded VOCABULARY gaps (ferment/forge/distill) and, with AI off, the draft button shows the graceful "enable draugr.ai" note. Frontend builds clean.
    - **DONE — the deferred items are cleared:**
      - **`SALT_DEPOSIT` now functional (no migration needed).** `gatherMineral` treats a chunk's salt/clay deposit sites as effective biomes (a salt flat reads as `SALT_DEPOSIT`, resolving rock_salt's affinity there), `activeLocation` overlays the `SALT_DEPOSIT` presentation, and the **classifier gained `salt`** (rock/sea salt was gatherable by affinity but unreachable by phrasing). Proven E2E: 13 rock_salt gathered at a WETLAND salt spring — impossible without the overlay.
      - **FINE-grade material source reachable.** A careful mineral search now grades the nodule by the attempt (`QualityGrade.attempt`), so careful gathering → FINE stone → a careful downstream chain can finish FINE instead of being capped at SOUND. Proven E2E: FINE flint from careful gathering.
      - **Apply-a-proposal is documented as a human step, by design** (three-ai-integration.md): there is no runtime auto-apply — that would break the "runtime never alters schema" boundary; the Architect drafts, a human saves it as a `V*.sql`, and Flyway + the V53 gate validate it like any migration.
    - **DONE — stabilization sweep.** Full backend suite green (**111 tests, 0 failures**; the 3 Testcontainers integration tests run in CI — they abort locally on the Docker-npipe quirk). Frontend builds clean. Booted-stack core-loop smoke (observe → gather fiber/stone/branches/berries → craft knife/hammer/hatchet → build fire pit → strip bark → rest/drink/eat → hygiene): **23 actions, 0 non-201 responses, auditor consistent, chronicle LIVING**. Each of this session's specific paths was also verified E2E along the way (all 3 AIs inert-when-off, fell→ground→split, salt gather at a SALT_DEPOSIT, FINE mineral, PDF export, #14–#18).
    - **NEXT:** flip the AI switch and live-verify all three with a real key (`DRAUGR_AI_ENABLED=true` + `ANTHROPIC_API_KEY`, next usage-limit cycle); tune prompts against real playthrough output.
    - Still deferred: FINE-grade material sources, and the `SALT_DEPOSIT` world-generator placement.

#### Why coverage, not correctness, is the cost driver

Four procedure simulations (15 m² cabin, fish preservation, bow production, leather armor) returned 0/12, 1/10, 3/12, 3/12 COVERED — and several of those were *false* COVERED. True coverage is roughly 5–10%. With only 20 processes, nearly every meaningful action still routes to the Architect.

**M1/M2 improves correctness, not cost.** Routing hardening makes the gaps honest; it does not make them fewer. V56 makes them countable. Step 2 makes them fewer.

The unlock: V53's review gate already makes machine-authored processes safe to accept — it caught 9 of 20 hand-authored recipes creating matter, deterministically and with no AI involved. Bounded one-time offline cost instead of an unbounded per-action one. Caveat: mass balance catches physics errors but not *plausibility* errors — a recipe can conserve mass and still be bad primitive technology. Needs tightly grounded generation prompts plus sampled human review (design rule #5).

**THEN: Task #21 — AI narration (the Simulation Agent's voice).** The seam is built: `NarrationRouter` decides whether to call, `NarrationEngine` supplies the `backendNarration` the refinement prompt builds on. See `docs/architecture/narration-engine.md` for the prompt template and cost model.

### Intents implemented (ChronicleActionService)
GATHER, HARVEST, CRAFT, EQUIP, UNEQUIP, DROP, BUILD, REPAIR, ABANDON, RESUME, LIGHT_FIRE, ADD_FUEL, MAKE_CHARCOAL, COOK, SLEEP, STRIP_BARK, GATHER_CLAY, GATHER_STONE_SLAB, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, WRITE, EDIT_DOCUMENT, SKETCH_MAP, DESIGNATE, MARK, TRAVEL, CONFRONT_WILDLIFE, REFINE, OBSERVE, GATHER_PLANT, FELL_TREE, RAID_HIVE, COLLECT_INSECTS, FISH, SNARE, TRACK, TAME, LURE, SET_TRAP, CHECK_TRAP, ADVANCE_ASSEMBLY, INSPECT, REWORK, PERSONAL_ACT, AGGRESSION_WILDLIFE, AGGRESSION_INANIMATE (multi-output & preserved-food outputs handled inside PROCESS_MATERIAL)

### Known limitations (acceptable for now)
- Friction fire kit never wears out — infinite fires once made (wear/degradation future task)
- Three-layer model only wired into LIGHT_FIRE and CONFRONT_WILDLIFE — other complex intents (wound treatment, crafting) still binary
- stone_shelf is a container but documents placed in a location-container aren't reachable via owner-tree CTE (reading from shelves not yet wired)
- Named locations not shown in frontend header
- Furniture has no dedicated UI panel
- `fire_piston` catalogued but unreachable (no acquisition path); `field_journal` and `hand_drawn_map` likewise — left deliberately
- Chronicle Archive styling has never had visual/screenshot verification

---

## Three-AI Architecture (NOT YET WIRED)

**1. Simulation Agent** — Physics, Ecology, Weather, Wildlife, Narration. Reads committed state, mutates runtime state, witnesses outcomes. Never hints. Maps to existing backend services + future AI narration layer.

**2. Persistent State Architect** — Owns schema, evolves it, creates new domains when civilization invents them. Writes Flyway migrations. NEVER narrates. Structural authority only. Gated by `ArchitectRouter` (COVERED / POLISH / INVENT).

**3. Persistent State Auditor** — Read-only guardian. Verifies invariants. NEVER modifies data. `PersistentStateAuditor.java` is the implementation; V53 gives it authority over process definitions before they become canon.

**F8 DECIDED (2026-07-31):** Schema is MONOTONIC. One shared schema, world-scoped objects, chronicle-scoped capability. When pottery is invented for the first time, the Architect adds the tables ONCE globally. Subsequent chronicles inherit a world where those tables exist but start with zero familiarity. Branching schemas rejected — impossible when world_object rows persist across chronicle deaths and a new chronicle might find the old chronicle's kiln.

---

## Canonical Engineering Principles (all currently followed)

1. Every physical object has a UUID — world_object PK.
2. Objects are NEVER deleted — DESTROYED lifecycle_state + destroyed_location_id + destroyed_cause. Only junction rows hard-deleted.
3. Identity never changes — UUID stable; REFINE keeps object_id.
4. State changes mutate the live row — condition/lifecycle/owner/location/name.
5. History is immutable — object_transition, chronicle_action, chronicle_event, literature_revision are append-only.
6. Current state is authoritative — narration generated from state, never the reverse.
7. Runtime state and historical archive are separate — live mutable rows vs append-only ledgers.
8. Every object always has a physical location or owner — enforced by PersistentStateAuditor.
9. World remembers everything — ledgers + chronicle_discovery + chronicle_chunk_visit + location_marker + death snapshot.
10. A definition is not canon until it is reviewed — `material_process.review_state='VERIFIED'` gates execution.
11. Every item must have an acquisition path — `item_source`, or an explicit `item_unreachable_known` row saying why not.

---

## Deferred

- **Task #13** — Verify writing flow end-to-end (live playthrough): STRIP_BARK → MAKE_CHARCOAL → WRITE
- **Task #21** — AI narration API integration (foundation now complete; unblocked)

---

## The GPT Prototype Playthrough (Days 1–10 Summary)

Juan ran a 10-day playthrough using ChatGPT as the simulation engine, with JSON files as state persistence. The prototype was unstable (memory loss, inconsistencies) but proved the concept works and people will tolerate friction to play it.

**What actually happened:**
- Day 1: Founded Wolf Kingdom at Wolf Stone landmark. Built The Archives (stone slab shelf) before any shelter. Crafted Stone Knife and basket.
- Day 2: Built Wolf Fire Pit. Constructed wooden desk + chair (Wolf Knowledge Workstation). Documented friction fire technique on a **stone slab**.
- Day 3: Explored Northern Woodland, Rocky Transition, Natural Stone Quarry, Dense Forest, Forest Stream. Designated Drinking Area and Urination Area.
- Day 4: Created Primitive Utility Belt. Archived fire documentation.
- Day 5: Crafted Stone Hatchet. Upgraded Utility Belt to Revision II (hatchet holder added). Completed Blueprint Map and Regional Map Revision IV.
- Days 6–7: Built Woodworking Table and Stone Working Table in Heavy Manufacturing District.
- Day 8: Built Weaving Work Table in Textile Manufacturing District. Utility Belt refined again (hammer holder).
- Days 9–10: Built and organized Tool Shed in Arsenal District.

**Key insight:** The first thing built was The Archives — not shelter, not weapons. This player is a QA Lead and TPM. Another player would do it completely differently. The game must support all approaches equally.

---

## What NOT to Do

- Do not add skill unlock trees, XP systems, or progression gates beyond physical prerequisites
- Do not make the narrator helpful — it describes, it never guides
- Do not add UI action buttons — everything through the action composer
- Do not use `mvn` bare in terminal — use the full path to the bundled Maven
- Do not use bash heredoc syntax for git commits in PowerShell — write the message to a file and use `git commit -F`
- Do not degrade the current UI design — the dark survival aesthetic is intentional and correct
- Do not delete world_object rows — ever. DESTROYED lifecycle_state only.
- Do not give the new chronicle any of the dead chronicle's capability scores — they start at zero
- Do not commit to `main`, `staging`, or `production` — work lands on `development`
- Do not add mutation methods to `PersistentStateAuditor` — it is read-only by contract
