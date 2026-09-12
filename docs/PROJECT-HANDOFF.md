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

## GitHub issue workflow — the ticket is the source of truth (STANDARD, do not skip)

Because PRs merge to `development` (a promotion branch) and **not** the default branch `main`, GitHub **never auto-closes** an issue from our merges — `Fixes #N` only fires on the default branch. So the backlog only reflects reality if we keep it current by hand. Make this part of finishing every slice, not a batched afterthought:

1. **On merge of a slice** (PR → `development`, CI-green): post a progress comment on the parent issue — `gh issue comment <n>` as **devosphere** — stating **what was delivered** (the PR # and the mechanic), **what scope the story now covers**, and, most important, **what remains**. Reference the PR.
2. **When a ticket's scope is genuinely, fully delivered:** `gh issue close <n>` with an evidence comment (the fix PRs/commits and which acceptance criteria are met). Self-contained bugs close outright; broad multi-sector EPIC *stories* usually stay **open** with a progress comment because they span sectors not yet built — say so explicitly ("survival sector delivered; cross-sector extends as those sectors land").
3. **Starting a story:** read the ticket's latest progress comment first to learn what is left, then continue. The ticket comment — not a fresh read of the code — is the resume point for a story's remaining scope.

This is why a later cycle can tell what is done vs. left on a ticket without guessing. Keep it truthful.

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
| `backend/src/main/resources/db/migration/` | Flyway migrations V1–V126. Next is V127. (…V109 bare-hand carrying, V110 bare-hand wraps, V111 bare-hand cordage, V112 arrival viability, V113 bare-hand carrying completion, V114 bare-hand food handling, V115 bare-hand clay ornaments, V116 bare-hand raw materials, V117 handwork mass-conservation, V118 smoke face wrap + smoke-exposure vector, V119 first-aid supplies, V120 bare-hand resin scavenge, V121 resin torch, V122 stone-boiling, V123 early shields, V124 early clubs, V125 sling, V126 javelin.) |
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

## What Is Built (Migrations V1–V126, all applied)

> **Post-playtest cycle (2026-08-03) — summary; full detail + resume point in
> [systems/06.4-Runtime-Authoring-Build-Plan.md](systems/06.4-Runtime-Authoring-Build-Plan.md).**
> A live playthrough drove four buckets of work, all committed to every branch:
> - **Bucket A** — playtest bugs fixed & closed: #24 eat-any-food, #23 gather-right-resource, #21 plural
>   subjects route ("split the **logs**"), #19 crafts drop in front instead of a raw carry error.
> - **Bucket B** — the `NarrationEngine` (previously dead) now grounds every action in its setting;
>   varied failure prose. The "robotic" fix.
> - **Bucket D** — calibration: #27 per-action energy/hygiene labour cost, #26 relevant mastery per
>   action, #28 realistic seasonal weather (storms ~8%, not every-4th-day).
> - **Bucket C** — the **DR-0021 runtime procedure-authoring pipeline** ([DR-0021](systems/06.3-Decision-Log.md#dr-0021),
>   [runtime-procedure-authoring.md](architecture/runtime-procedure-authoring.md)): five AI roles (Narrator,
>   Interpreter, Runtime Architect, QA Critic, Auditor) + a deterministic physics gate, V66 chronicle-scoped
>   tech schema + discovery ledger, the authoring orchestrator, `GET /api/system/tech-discoveries`, and a
>   player loading overlay. **Built, gated behind `draugr.ai.enabled` (+ `authoring-enabled`), stub-tested,
>   and verified inert AI-off.** AI-on live verification of the whole layer awaits the operator key.
>
> 131 backend unit tests green; SQL regression replays in `tests/regression/` (incl. the tech-scope
> isolation guarantee) pass; frontend builds clean.

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

### Sprint 003 — Materials Foundation + Routing Hardening (COMPLETE — M1–M5, V47–V63)

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
- V61–V63: **Felling tool + wider gathering.** One-handed craftable `stone_hatchet` (left-hand) so a tree can be felled without planks; oceanside/`SALT_DEPOSIT` salt (deliberately not rivers/streams — drinkable freshwater); flint/pyrite eroding out on `RIVER_BANK`; loose vines on forest trees. `SALT_DEPOSIT` is now a functional deposit overlay (resolved in `gatherMineral` + `activeLocation` presentation + the classifier's `salt` vocabulary) — no migration needed for placement.

**Post-M5 (this phase), all applied and verified:** playtest bug fixes #14–#18 (see resume point below); chronicle-narration **PDF export**; the **three-AI integration** (Simulation Agent narration wired into `resolve()`, Persistent State Architect + Auditor with Overseer UI — all gated off by default); FINE-grade material source reachable via careful mineral gathering. Full suite: 111 backend tests green; core-loop smoke clean.

**Why routing hardening exists:** matching was lexical and global. "Split the fish" matched `split_planks`; "weatherproof the shell" matched the weather domain. A wrong match doesn't throw — it silently suppresses the Architect call the moment needed, which is worse than an honest gap.

**Correction to the original spec:** category scoping alone only separates 5 of the 8 recorded collisions — "split the fish"/"split the log" are both PROCESS. Scoping needs **two axes**: category *and* subject material. V54 implements both.

**V54 delivers:** `activity_category` (10), `category_term` (~180 weighted terms), `process_subject` (derived from each process's own inputs/outputs so it can't drift), `material_process.category_key` (NOT NULL, backfilled in the same migration so a missed row fails the migration rather than silently ceasing to match). Also broadened V52's subject-bearing keyword phrases to bare verbs — safe only now that category+subject must also agree.

**Resolution rule (documented in full at the foot of V54 — Java must not drift from it):** classify by summed term weight, `precedence` breaks ties; a process matches only if category **and** keyword **and** subject all agree; **if no category term matches, classification is NULL and the category condition is dropped**, not treated as "matches nothing".

**V55 corrects V54's data.** Wiring the Java and probing with ordinary phrasing exposed the opposite failure from the one V54 fixed: `dress_foundation`, `reinforce_timber` and `fire_vessel` had become unreachable by any plausible sentence, and `weave_large_basket` resolved to `weave_textile`. In every case a process's declared category disagreed with the category its own verbs classify to — an axis V54 introduced but never checked. V55 fixes the four and adds the check.

**M1 + M2 are COMPLETE.** `ActivityClassifier` (classification) and `ProcessMatcher` (the full rule) live in `com.devosphere.draugr.routing`; both `PhysicalItemService.runProcess()` and `ArchitectRouter` route through `ProcessMatcher`, so the rule has exactly one implementation. The Auditor carries three new standing invariants. 88 DB-free unit tests green; 57 migrations apply clean from scratch.

**Verified:** 8/8 recorded collisions blocked, 21/21 processes reachable by ordinary phrasing, and every miss correctly diagnosed as vocabulary / keyword / subject / mechanic — `ProcessRoutingTest`.

**Step 2 (coverage) is COMPLETE — V57.** 129 processes now VERIFIED (was 20). The V53 gate was extended to make category-agreement and subject-presence BLOCKING, so an unreachable process is held rather than shipped; it caught 3. A reusable reachability probe — [routing-reachability-probe.sql](architecture/routing-reachability-probe.sql), a SQL replica of the runtime rule — checks every process resolves from ordinary phrasing; it surfaced 7 in-batch collisions, all fixed before landing. Re-run it after any migration that adds processes.

#### AMENDED by DR-0021 (2026-08-03): a BOUNDED AI at resolution time is now approved

**⚠ The old "no AI at resolution time, ever" stance below is SUPERSEDED for the composition/authoring
case. Read [DR-0021](systems/06.3-Decision-Log.md#dr-0021) and the live build tracker
[06.4-Runtime-Authoring-Build-Plan.md](systems/06.4-Runtime-Authoring-Build-Plan.md) before doing any
of this work — the architecture is DECIDED; do not re-open the discussion.**

Why it changed: the four arguments below were measured at **~5–10% mechanic coverage**, where most misses
were *missing mechanics*. At **129 processes** that premise flipped — the belt and the planks already
exist, and playtest failures are increasingly *interpretation/decomposition* against present mechanics.
DR-0021 introduces a bounded resolution-time pipeline that neutralises each old argument structurally:
the deterministic gate rejects nearest-neighbour false-matches (arg 2); the runtime Architect writes only
**per-chronicle scoped, physics-gated data — never an outcome, never canon, never schema** (args 1, 3);
and canon promotion stays human-gated (arg 4). Governing invariant: **every AI is non-load-bearing over a
complete deterministic core.** New *domains/tables* still require a human migration (DR-0009/DR-0013 intact).

*Historical record — the original stance (still the reasoning for why runtime authoring is tightly bounded):*
The question "should an AI layer help the ActivityClassifier work out what the player meant?" was first answered **no.** Four reasons — (1) ~39 of ~46 probed actions missed because *the mechanic does not exist*, so a classifier would pay a call to confirm emptiness; (2) an LLM handed 20 candidates finds the nearest one, which manufactures exactly the false COVERED that V54/V55 exist to prevent; (3) `runProcess` writes permanent history, and V53's gate works precisely because what it gates is data sitting still; (4) it grants a fourth agent authority that `core-agent-boundaries.md` never gave it. `docs/architecture/routing-and-coverage-strategy.md` carries the full original argument; DR-0021 records exactly which evidence reopened it.

#### Resume point

> **▶ LATEST (2026-09-12): the two systems husbandry was waiting on got built, and the biggest
> declared-in-code seam yet — what every action does to the world — moved into data behind a gate.**
>
> | slice | what it made possible |
> |---|---|
> | **V303** stock in the heat | Grown animals feel heat above 26°C. The cold rule (V297) was about *young*, so a full-grown beast could stand in any weather indefinitely. Gives the **shade shelter** and the **mud wallow** each a job the other cannot do — a wallow does nothing for a goat, and shade does not replace a wallow for a pig. |
> | **V304** a bull among the young | **Temperament.** Every tamed animal behaved identically once TAMED, which is why `boar_isolation_pen` had nothing to isolate. |
> | **V305** fire clay and a lined furnace | A *better* smelt rather than a replaced one: `station_kind` is single-key equality, so a lined furnace cannot satisfy the processes naming the plain one — it enables a **new** process instead. Nothing existing changes. Also moved the `GATHER_MINERAL` mineral list out of Java literals, which made **limestone** diggable by name for the first time. |
> | **V306** minerals bound to their geology | `mineral_province`: obsidian comes off a volcanic field, not off any mountain. Nine new marker sites, and `ecology.reconcile()` so the **pinned world receives them without being regenerated**. |
> | **V307** an impact card for every procedure | What an act does to the land was decided in **four** places in Java covering **seven** procedures; the other **121 left nothing and none of them had ever said so**. One table, one reader, and a gate that makes a card mandatory. |
> | **V308** something to hold a beast with | A `leg_hobble` for a keeper with a dangerous animal and no stanchion for a mile. Carried restraint **reduces** the injury and never removes it, or the stanchion is a building nobody would raise. |
> | **V309** the card carries the cost | `laborOf` and `capabilityDomainOf` join the footprint on the same row. |
>
> **PUTTING THE THREE LISTS ON ONE ROW MADE A QUESTION ASKABLE THAT HAD NEVER BEEN ASKED:** does anything mark
> the land while costing the body nothing? Two did. `AGGRESSION_WILDLIFE` is correct (a fight runs its own
> physiology). **`COPPICE` was a defect** — in none of `laborOf`'s three tiers, so cutting a stool back with an
> axe took the canopy down and tired nobody. Nothing could have seen it while the footprint and the labour lived
> in different switches in different parts of the same file.
>
> **⚠ THE EXPENSIVE LESSON OF THIS CYCLE — one catalogue row failed 301 of 679 tests.**
> `make_lined_bloomery_furnace` is 96kg of standing shaft out of 15kg of carried clay and stone. The plain
> bloomery has the same shape and has been `conservation_exempt` since it was written; I added a furnace beside
> it without carrying the exemption across. **Nearly every integration test ends in
> `assertTrue(auditor.inspect().consistent())`**, so one bad row is a 50-minute CI round for the whole suite.
> **Reproduce the Auditor's conservation query inside any migration that adds a `material_process`** — V305 and
> V308 now do, over the whole catalogue rather than their own rows.
>
> **AND MY OWN GUARD HAD BLESSED THE DEEPER FAULT.** The recipe said "the same ore, twice the bloom" — two
> blooms weigh 2800g, two ore weigh 2600g: iron from nothing. The guard I wrote asserted *"the hot smelt must not
> eat more ore"*, which **forced** it. A slogan written into a guard, defending itself against the physics. **A
> better process means better RECOVERY (output per input), never more matter** — write the guard as a ratio
> against the process it improves on, never as "no more inputs".
>
> **⚠ PROCESS — what else cost time:**
> 1. **A Java intent runs before the assembly matcher.** V303's shade shelter answered to `"build a sun shade"`,
>    which `coverKindOf` has owned since #195 — the V284 `BUILD_PEN` trap wearing another face. CI's
>    `noJavaIntentShadowsAnAssemblysOwnKeywords` caught it. Both things were kept and the *phrasing* moved.
> 2. **A gate is only as honest as what the player is told.** V306 gated obsidian to its province and
>    `groundGeology` still read minerals by biome alone — the survey would have promised what the hand could not
>    get. **Two places answering the same question from different data** is this project's signature defect;
>    check for the second reader every time a gate is added.
> 3. **`development` can be red for reasons nobody owns.** `RiverSandGravelIntegrationTest` made 60 attempts
>    through `actions.resolve`, each advancing the world 40 minutes — up to forty simulated hours of standing in
>    a marsh — and the Chronicle sometimes died before the assertion. Fixed by making the routing claim and the
>    yield claim separately: one `resolve` (whose intent is now actually asserted) and the rest at the service
>    boundary, which moves no clock.
>
> **Recommended next:** finish the card. #216 also names inputs, outputs/waste, and time — and `durationFor` is
> the same shape (one switch, 120+ intents) though much the riskiest to move, since it sets every action's
> duration. After that, the card's most arguable silence is worth settling deliberately: a fence line, a lean-to
> and an assembly stage are hours of work on ground a Chronicle already lives on, and whether that deserves a
> mark is now a one-row decision instead of an invisible omission.
>

> **▶ PREVIOUS (2026-09-12, earlier): husbandry finished as a loop — #108 is out of work it can do, and every structure it
> still names waits on a system rather than on a decision.**
>
> Six slices (V297–V301 + tending). Each one existed because the slice before it created something for it to act
> on, which is the opposite of the catalogue-token habit this project keeps guarding against:
>
> | slice | what it made possible |
> |---|---|
> | **V297** a hard winter takes the young | Young could be *lost*. This is the first thing that tells a **byre from a fold** — `encloses` has been in the catalogue since V280 and the two kinds of stock shelter were identical to a keeper until now. |
> | **V298** the ones that do not live | Birth losses, so the **farrowing shelter / brooder / foaling stall** answer something a byre cannot. Three tiers: open ground → full loss, roofed shelter → half, birthing house → none. |
> | **V299** what spreads through a herd | **Sickness**, which is the missing consequence of a loop that already ran: stock foul the ground, and the ground had been harming only the Chronicle's larder. Gives the **isolation shelter** a job. |
> | **V300** a cart left in the rain | Vehicles **weather** (nothing wooden could be touched by weather before), and `condition_state` is finally read — a **broken cart had been hauling a sound cart's load**. |
> | **V301** what carries you | **Riding.** Eight species pulled and none could be ridden; a horse and an ox were the same number of grams. Riding is *how you travel*, not a state, and the journey's fatigue lands on the animal — so a keeper who rides everywhere cannot pull. |
> | **tending a sick beast** | V299 left sickness curable only by acting on the *ground*. Now a keeper can dose the animal, with medicine the catalogue already had. |
>
> **THE SHIP TEST DID MORE WORK THAN THE CODE.** Four structures #108 names were **declined**, each with its
> reason recorded in the migration that declined it, so none reads as forgotten:
> `quarantine_pen` (one behaviour with `sick_animal_shelter`), `cart_shed` + `yoke_rack` + `harness_rack` +
> `loading_platform` (renames of stores that exist, until a **size/capacity model** exists to tell them apart).
> Twice the mechanic had to be built *before* the structure could honestly exist at all — a shelter that reduces
> losses is meaningless while there are no losses.
>
> **⚠ PROCESS — what actually cost time, and it was never the code:**
> 1. **`PREPARE p(type)` hides the defect it is meant to catch** (see the previous entry). Used correctly since,
>    and every slice from V299 on went green on the first CI run.
> 2. **`object_transition` is immutable by trigger.** Test teardown must **retire** (DESTROYED + cause + no
>    location/owner), never DELETE. And a `DELETE` matching **zero rows succeeds** — a probe that finds nothing
>    proves nothing, which briefly convinced me the history was mutable.
> 3. **Read the source before asserting what routes where.** `TREAT_WOUND` comes only from `classifyLegacy`
>    (verbs bind/bandage/dress); `"treat the wound"` was **already unrouted** and I had written the wrong claim
>    into a code comment. Two failed local assertions found it in seconds — the cheap version of the morning's
>    narration mistake.
>
> **Recommended next:** **temperament** is the largest single unlock left in husbandry — five items in #106 plus
> `boar_isolation_pen` in #108 — but it is a real new system and wants its own story, not a gear ticket. After
> that, **stock thermoregulation** and **site-level hygiene**. Do not build the remaining structures first: each
> is one data row once its system exists, and building them earlier means inventing the system to justify the
> structure, which is the wrong way round.
>
> **▶ PREVIOUS (2026-09-09/10): eight slices merged — the declared-but-ignored defect found nine more times, and
> husbandry given the one stage it never had.**
>
> **The shape is unchanged and still the richest seam in this codebase:** a capability is declared in the
> catalogue and the code names literals instead, so the declaration does nothing.
>
> | was | now | what it cost |
> |---|---|---|
> | `restPennedDraftBeasts`' three literals | `construction_kind.shelters_stock` (V293) | a **cattle byre** was buildable in two stages from a VERIFIED assembly and **rested no ox** — likewise the goat fold, pig sty, poultry coop, timber barn |
> | day encounter's two fence literals | `construction_kind.barrier_strength` (V293) | V281 had already made `is_barrier` what the **night** raid reads, so a dry stone wall turned a wolf aside at midnight and was **invisible at noon** |
> | `takeTamedYield`'s species lists | `tamed_yield.yield_kind` (V294) | a **reindeer could not be milked** though the catalogue said so since V45; `kingdom_class='AVES'` made a **peregrine falcon poultry** |
> | one `last_yield_at` per bond | `tamed_production` per product (V294) | **milking a goat made it unshearable** — one animal, one timer, two different jobs |
> | DESIGNATE's six invented tags | `district_purpose.keywords` (V295) | `district_purpose` was **the only table in the schema no Java file named**; the two vocabularies shared **one word** |
> | `safeWaterSource`'s unconditional river | refuse + `fouls_water` (V295) | a camp fouled to refuse 100 drew **risk-free water for ever** |
> | `BackdropResolver` | served at `/v1/backdrop` (#561) | the resolver was **called by nothing but its own test** |
> | the eligibility chain | served as `candidates` (#566) | built in #562 and **not put on the wire**, so no caller could degrade through it |
> | `wildlife_population.population_count` | herd-scaled yields (V296) | read **only as `> 0`** — twenty goats gave exactly what one gave |
>
> **The ship test remains the filter:** *does anything change because this exists?* It is what rejected three more
> fireplaces last cycle and what forced V296 to grow past breeding alone — a herd that grows is bookkeeping unless
> the herd is worth having.
>
> **Delivered:** #108 (stock shelters + day/night barrier agreement, with a full mapping of its 40-odd named
> structures to what exists); #52 (yield catalogue, per-product clocks, **breeding and young** — the one husbandry
> stage the simulation lacked, which four of #108's structures were waiting on); #64 (one district vocabulary,
> enforced, and a camp that can foul its own water); #30 (**~130 narration scenes** — 78 intents had none at all,
> and `BUILD_*`/`CRAFT_*` were nearly absent); #234/#236 (backdrop endpoint + eligibility chain).
>
> **⚠ PROCESS — the three that cost real time this cycle, all of them about *verification*, not code:**
> 1. **`PREPARE p(type)` HIDES THE DEFECT IT IS MEANT TO CATCH.** A declared parameter list types the parameter
>    *for* you — and that declaration is exactly what JDBC does not supply. V296 passed `PREPARE p1(timestamptz)`
>    and then failed **thirty-odd integration tests** with `operator does not exist: timestamp with time zone <=
>    interval`, because a bare `?` beside `make_interval` resolves as an *interval*. **Use `PREPARE name AS` with
>    no parameter list** — that reproduces the driver exactly. Any `?` in date arithmetic needs `?::timestamptz`.
> 2. **Anything wired into `SimulationTickService.advance()` runs in every test that ticks the world.** One bad
>    statement there fails the whole suite rather than one test. Treat it as the highest-risk place for new SQL.
> 3. **`@Test` methods in one class share one Testcontainers database, and "shared" is wider than fixture rows.**
>    Three separate failures from it: a species count another test had grown; **a structure a sibling test built
>    on the same chunk**; and a population the **ecology simulation** grew during the tick that resolving an
>    action runs. Scope every count, every teardown, and every aggregate to the thing under test — or call the
>    service boundary directly and keep the tick out of it.
>
> **Honest tally on #567:** three CI rounds, and the production code was wrong in **one** of them. The other two
> were assertions resting on state the test did not own. That is a pattern worth watching for, not bad luck.
>
> **Recommended next:** #108's remaining structures now have a mechanism to hang on (`farrowing_shelter`,
> `brooder_shelter`, `foaling_stall`, `boar_isolation_pen` all wanted young, and young now exist). After that, the
> honest gaps in husbandry are **animal health** (no disease/parasite state for `quarantine_pen` to act on) and
> **riding** (draft haulage exists; being carried does not). `technique_definition` is the last substantial
> declared-but-ignored table — 162 hand-written techniques whose `principle` a player can never see — but it wants
> a design answer first, because the game has no model of what a Chronicle *knows*.
>
> **▶ PREVIOUS (2026-09-08): #158 closed, #157/#159/#77 advanced — and the same defect found seven more times.**
>
> **The shape, stated once:** a thing is declared in the catalogue and the code names literals instead, so the
> declaration does nothing. Seven instances this cycle, each fixed by moving the list into data:
>
> | was | now | what it cost |
> |---|---|---|
> | `STONE_FIRE_PIT` ×7 literals | `construction_kind.holds_fire` (V289) | a `CLAY_LINED_HEARTH` was buildable in three stages **and could not be lit** |
> | taming's six weapon keys | `weapon_profile` | **33 of 36 weapons invisible** — a bronze spear read as empty hands, a stone hammer frightened the animal |
> | `clearLand`'s seven axe keys | `tool_profile` | named a **phantom** `hand_axe` (0 rows) and missed `stone_hand_axe`, which had no tool row at all — barring the archetypal biface from **156** CUTTING processes |
> | predator draw's two keys | `carcass_scent` (V287) | a goose over the shoulder drew nothing |
> | pottery's two keys | `LIKE 'unfired%'` | `unfired_vessel` was wet clay the rain could not touch |
> | colony regrowth | `insect_colony_kind.shellfish` (V292) | every stretch of coast came back at one rate |
> | `requires_fire` | `retained_heat_minutes` (V290) | nothing could hold heat past the flame |
>
> **The Auditor is part of the fix, not just the check.** Its "displaced fire" rule named `STONE_FIRE_PIT` too, so
> fixing only `FireService` would have made every fire in a hearth read as world corruption.
>
> **Delivered:** #158 **closed** (cave interior derived from the mouths — dark at noon, out of the weather, one way
> in, its own ecology in V288; `karst_sink` explicitly rejected). #157 — the shore now **holds fish** (five species,
> no new item keys; every aquatic species was freshwater) and a **shell bed** recovers faster than a scatter. #159 —
> the **wood's edge** carries more than the wood, with markers that can now require a neighbour. #77 — the **earth
> oven**, which cooks after the fire is out, chosen over three more fireplaces that would have been three names for
> one behaviour.
>
> **⚠ PROCESS — the three that cost real time:**
> 1. **A green local suite is not evidence for an integration test.** They skip here. Validate their SQL on a
>    throwaway Postgres with `PREPARE` **before pushing** — that found `mass_grams` (it is `unit_mass_grams`), a
>    missing `site_category`, `ecology_site.richness` (it is `baseline_abundance`), no `world` table (it is
>    `world_genesis`), and `timestamp > interval` needing an explicit cast. Each would have been its own 47-minute
>    CI round trip.
> 2. **Every catalogue migration should assert the Auditor's obtainability rule in its guard block.** `flora_drop`
>    is NOT consulted — `cave_mushroom` dropped off a plant and still counted unobtainable, failing ~20 tests.
> 3. **Probe EVERY assembly keyword, not a sample.** `"work on the earth oven"` was taken by TILL_GROUND ("earth"
>    is a whole word in it). The catalogue-wide shadow invariant needs Docker and so does not run here; the
>    Docker-free assertions now live in `IntentClassificationRegressionTest`.
>
> **And two things I got wrong and corrected:** I claimed the rivers held no fish and wrote a migration for it —
> V269 had already stocked them, and I had grepped `INSERT` rows instead of the live state (deleted it). I also
> claimed a process produced nothing because `material_process_output` was empty — outputs live in the
> `output_item_key` **column**. **Query the live database, never the migration text.**
>
> **▶ PREVIOUS (2026-09-07): #156 hydrology closed out — and three rules that were real but unreachable.**
>
> The recurring shape this cycle: **a rule can be correct, tested, and impossible to reach.** Three of them, in
> the same ticket.
>
> - **The floodplain (#541).** #539 gave it fertility recovering twice as fast as ordinary ground — and it stands
>   on `RIVER_BANK`/`WETLAND`, which `sowCrop` refused, while `clearLand` takes woodland only. So the ground that
>   renews fastest was ground **no Chronicle could put a field on.** The fertility test could not see it: it
>   inserts a ripe `crop_stand` directly, because waiting a season is not a thing a test can do, and that walks
>   past whether sowing there is possible at all. `isArable` now also takes floodplain and marsh-island silt, and
>   `WetGroundIsFarmableIntegrationTest` asserts it through `sowCrop` — **including the negative**, that open bog
>   is still not farmland, or the change would read "wetland is arable now", which is a different and false claim.
> - **The sea was walkable (#544).** `move()` handed over any adjacent chunk without ever asking what it was. You
>   could step off a beach into open ocean carrying a hundred kilos. The fix is not a wall: open water can be swum
>   but not while loaded (¼ of capacity), a marsh takes a walker but not a heavy pack (¾), dry land is unchanged.
>   **That is what finally gave `shallow_ford` something to do** — until movement asked what it was crossing, a
>   ford could only ever have been a label.
> - **Fast vs slow water (#543).** #156 demands these as topology, *not interchangeable labels*, so what differs is
>   what works in them: a fixed trap or net gains in fast water (the current delivers the fish — the principle a
>   weir is built on), a hand-line gains in slack water. Applied after gear and bait, so it shifts odds rather than
>   deciding them; unnamed water is asserted unchanged.
>
> **A silent hole in genesis, now closed.** `marker()` returns the middle of the map when a spec names no biome the
> terrain generates — no check, no warning. Such a marker does not go *missing*, which would be noticed; it appears
> in a plausible place and means nothing. `WorldMarkerPlacementTest` walks every spec across five seeds. Probing it
> with a deliberately bad spec showed the centre chunk is **open sea in three of five seeds**.
>
> **`roc` is inside `au-roc-hs` (#542).** CI flaked on "a monster is never casually named" against *"A aurochs, by
> the look of it"*. The matcher was the bug and it was **live in gameplay** — `"hunt the aurochs"` could select the
> roc. Six call sites now go through `narration.Words`, which bounds with lookarounds rather than `\b` (a name
> ending in punctuation, `lean-to`, has no word boundary after it). Also fixes "A aurochs" → "An aurochs".
>
> **Two process notes.** (1) Merge only on `gh pr checks --watch` exit status — two hand-rolled jq watchers merged
> #541/#542 before the backend suite reported (`//` substitutes for `null`, not `""`). (2) I wrote a migration to
> "put fish in the rivers" and **deleted it**: V269 had already done it, and I had grepped the original `INSERT`
> rows instead of the live state. A throwaway Postgres with all migrations applied is what caught it.
>
> **#156 is closed out.** Every site the ticket names is placed and functional: `headwater_spring`, `pond`,
> `lake_margin`, `riverbank`, `floodplain`, `marsh_island`, `fast_stream`, `slow_river`, `shallow_ford`,
> `beaver_pool`, `springhead`. The beaver pool is not the `Beaver lodge` already placed — a lodge is where the
> animals are, a pool is what their dam made: it holds more fish, reads as slack water, and is water to drink.
>
> **The audit that keeps paying, run again:** grep the hardcoded `IN ('a','b',…)` lists out of the main source and
> diff each against the table it stands in for. It found that an animal could not see 33 of the world's 36 weapons
> (a bronze spear or hunting bow read as empty hands, while a stone hammer frightened it); that `hand_axe`, named
> in two felling checks, is a key the catalogue has never held; that `stone_hand_axe` had no `tool_profile` row and
> so could not perform any of 156 CUTTING processes; that fowl and crayfish carcasses drew no predators; and that
> `unfired_vessel` was green ware the rain could not touch.
>
> **⚠ Process, learned the hard way twice in one cycle.** A green local suite is NOT evidence for a new
> integration test — they skip without Docker here, so their SQL is unrun. Validate it against a throwaway
> Postgres (`PREPARE` each statement) BEFORE pushing; doing that after the first red found three further errors in
> one fixture, each of which would have cost its own 46-minute round trip. And do not stack PRs on unverified
> work: four were invalidated at once by one bad fixture in the base.
>
> **▶ PREVIOUS (2026-09-06): the world-seed topology closed, and one flag that was answering three different questions.**
>
> **The biggest structural find: `is_shelter` was being asked three unrelated questions.** Five places in the Java
> ask *"is there something here I can be inside, out of the weather"*; four named four project kinds literally, so a
> pit house, hide tent, debris hut, bark cabin or snow shelter gave no warmth, no rest bonus, no deep sleep and no
> roof for a smoke vent. But `is_shelter` reads far too wide for that question — 34 kinds carry it and **14 are
> parts, not places** (doors, walls, screens, a roofing frame, a smoke hood, furniture). And a *third* consumer, the
> predator raid on a tamed herd, needed a **barrier**, so every fence a keeper builds protected nothing while a bark
> door did. Now three flags, one question each:
> - `is_shelter` — any shelter-domain build; what decays together (unchanged, so nothing reading it broke)
> - `encloses` (V280) — can a Chronicle be inside this
> - `is_barrier` (V281) — does this stand in something's way
>
> **RULE learned: before wiring more consumers to a flag, check the flag means what they need.** A registry that has
> grown can drift in *meaning*, not just membership. Found by `grep -rnoE "IN \('[A-Z_]+'(,'[A-Z_]+')*\)"` over main
> Java, sorted by frequency — the four-shelter list was the most repeated in the codebase.
>
> **Topology (#155 family).** `CAVE_MOUTH` now generates (#158), derived like the shore: it must be *in* rock and
> *open onto* walkable ground, thinned by a deterministic hash. 13 cave mouths, 40 of 57 mountain chunks still open
> (obsidian/pumice/clear quartz need them), 0 opening onto nothing. V279 furnishes it. A cave mouth **shelters
> without being built** — the oldest roof there is. With `RIVER_BANK` (#156) and `COAST` (#157) already landed, the
> generator now emits **nine** biomes.
>
> **The standing gate that stops this recurring (#161, `CatalogueWorldSeedCompatibilityIntegrationTest`):** no
> candidate stranded on ground that does not exist; **no affinity naming a habitat the generator never builds**
> (the one that would have caught RIVER_BANK and COAST, since those entries named a second biome too); no generated
> ground barren; preview and persisted world agree per biome. It also forced out two fallbacks that violated it —
> `profileFor` returned a hardcoded `forest_fox` for every unmatched wildlife site *whatever the ground*, and
> `monsterFor` took the first candidate alphabetically, which a cave breaks (cave bear/troll/screecher all share
> "cave"). Longest shared word wins now; verified a no-op for all ten existing lairs.
>
> **Catalogue tokens made functional.** `encloses`/`is_barrier` above; three immortal foods (`crayfish_meat`,
> `raw_fowl_meat`, `bird_egg` had no `food_preservation_state` row at all — `smoked_fowl` existed as a preserved
> form that bought nothing); the `stone_lantern_cover` (now keeps a flame alight in weather that guts a bare one);
> the `firebrand`; the `atlatl` (a spear-thrower that threw no better than a bare arm); the `float_bobber`.
>
> **Seams audited CLEAN — do not re-run without a reason:** all 727 items attainable; every process routable and
> every tool class / station supplied; `technique_definition`'s 162 claims honest; all 47 `CODE_TERMINAL` entries
> genuinely covered; no assembly keyword shadowed by another's longer one; declared numbers all vary meaningfully.
> Full detail in the `reference-declared-but-ignored-audit` memory.
>
> **Test patterns worth copying.** A combat/tackle modifier is proven statistically: a fixed set of seeded action
> ids, state restored before each trial, two cohorts compared — and **assert the baseline both wins and loses**, or
> a rebalance that saturates it passes vacuously. Picking the quarry matters: with a javelin in hand a deer is beaten
> on every roll, so the margin can only show against something like a brown bear.
>
> **▶ SAME DAY, SECOND HALF (2026-09-06): the catalogue tokens, and three more one-sided checks.**
>
> **Every inert equippable from the #134 sweep is now wired**, each to a mechanism that already existed rather
> than an invented one: the `stone_lantern_cover` (hoods a flame against weather that guts a bare one), the
> `firebrand` (gives light, **carries fire** as its own method, **wards beasts** in both the ambush check and the
> close), the `atlatl`, the `float_bobber`, the `fire_poker`, the `warning_rattle`, the `tracking_marker_bundle`
> and the `whistle`. Only `smoke_signal_bundle` is left, honestly blocked on #109 — signalling needs someone to
> signal, and inventing a recipient would be worse than leaving it.
>
> **I called the whistle blocked and was wrong.** I had only thought of it as a way to CALL something. Warding is
> the third thing a whistle does: it defeats an ambush from the opposite direction to a cloak or a scent mask —
> those hide you, it announces you, and an ambush needs surprise. **The lesson is to enumerate what a thing
> physically does before concluding its mechanism is missing.**
>
> **Other structures made functional:** `SMOKE_RACK`/`DRYING_RACK` were buildable and read by nothing while three
> smoking and five drying processes ran beside them (V285 — and `station_kind` now accepts a construction kind
> directly from the registry, so the next rack needs no Java); four species-appropriate animal shelters that work
> because `is_barrier` is now read (V284); **salt from the sea** (V282), which needed its own `requires_salt_water`
> gate because `waterToWorkWith` accepts a freshwater spring and deliberately excludes the coast.
>
> **THREE MORE ONE-SIDED CHECKS, all the same shape as the flags:**
> 1. **Flammability** was asserted only as "stone must not burn", so a WOODEN build marked fireproof passed in
>    silence — `THATCH_ROOF` had, and four new shelters of mine were about to. Both halves asserted now.
> 2. **Fresh water** was spelled out in EIGHT places (seven main + a test fixture that clears water to assert dry
>    ground). Consolidated to `FreshWater` as a deliberate NO-OP first, *then* the ponds were added (#156). Done in
>    the other order, the first symptom would have been an unrelated test going red with nothing pointing at ponds.
> 3. **`isDark` had two callers** — fine sight-work and the night predator raid. Cave darkness got its OWN
>    question, because widening `isDark` would have brought wolves onto stock at noon.
>
> **A comment can lie, and two did.** `builtStationAt`'s claimed "the catalogue carries buildable WEAVING_TABLE,
> WOODWORKING_TABLE and STONEWORKING_TABLE structures" — it does not; those five workstation kinds have no
> assembly and nothing creates them as constructions (harmless: the item path is live, because `REACHABLE_CTE`
> counts what is on the ground). And `monsterFor` promised determinism while hashing a `UUID.randomUUID()`.
> **Check a comment that claims a capability, especially one claiming determinism.**
>
> **Why making the workshop tables buildable is not free** (recorded so it is not rediscovered): `CRAFT_DESK`
> matches `(craft|make|build|construct|assemble)` beside `(desk|table|workbench|bench)`, so any assembly keyword
> naming a table or a bench is shadowed before the matcher sees it.
>
> **▶ PREVIOUS (2026-08-13): confront query refactored (tech-debt cleared) + EPIC #222 backdrop registry story #223 landed.**
> - **Tech debt RESOLVED:** `WildlifeEncounterService.confront` collapsed from ~13 correlated COUNT subqueries (12
>   params) to two LATERAL conditional-aggregation passes (1 param). Proven behaviour-preserving by an old-vs-new
>   set-wide `EXCEPT` on a 200-chronicle random dataset (0 rows diverge). Commit `f9c3b96`.
> - **EPIC #222 / story #223 (#230 inventory + #231 validate) DONE (code):** canonical typed backdrop registry for all
>   151 `playthrough-*.png`. New: `frontend/src/backdrops/{manifest.schema.ts, backdrop-manifest.json, README.md}`,
>   `scripts/backdrops/{build-manifest.mjs, test-validation.mjs}` (Node built-ins, no deps), `docs/systems/backdrop-
>   coverage.md`, npm `backdrops:{generate,check,report,test}`. Deterministic slug-token classification (biome ∈ the 6
>   `BiomeClimate` bases + proximity + 14 families + fallback chains terminating at root `forest`), **boundary-safe token
>   matching** (avoids the ore-in-forest / cave-in-scavenging substring trap — see [[project_intent_classifier_substrings]]).
>   Validator rejects 15 drift classes; tsc clean. Commit `4764659`.
>   - **⚠ DEFERRED (user decision 2026-08-13):** the **396 MB of backdrop PNGs** (147 untracked, ~2.7 MB each) are NOT
>     committed yet, and the full directory⇄manifest **bijection** CI gate (`npm run backdrops:check`) is NOT wired —
>     only the self-contained `backdrops:test` runs in CI. **NEXT: decide asset storage (Git LFS vs direct vs
>     optimize/downscale — ties into task #229 budgets), land the PNGs, then wire `backdrops:check` into `verify.yml`
>     and regenerate the manifest.** Until then a fresh clone's `backdrops:check` fails bijection by design.
>   - Remaining #222 stories after assets land: #224 perception-safe visual-context API, #225 precedence resolver,
>     #226 temporal/weather eligibility, #227 lazy frontend loader, #228 creature-free governance, #229 budgets.

 **▶ ACTIVE NOW (2026-08-11): Milestone 1 — deep into the content EPICs, building autonomously toward M1 then M2.**
> All **13 playtest [BUG] issues (#29–#44)** are FIXED and **CLOSED**. **The user granted autonomous execution:
> finish M1, then continue into M2, WITHOUT interruption or permission requests — choose the engineering strategy
> yourself, land verifiable increments, commit/push, and close a story only when its acceptance is fully met.**
> Everything is on `development` (pushed) as `devosphere.tech` (never `johncalado`). **Migrations through V112.**
> Full suite **163 backend tests + 55 SQL regressions green** on the V1–V126 chain; each commit compiles with tests
> green and the routing-reachability probe clean (84 ok / 0 miss).
>
> **This session's major deliverables (V82→V112):**
> - **EPIC #64 Action Catalogue — CLOSED** (all 9 stories: #65–#73). **EPIC #54 supply chains — ADVANCED** (#55
>   building stock, #56–#60 objects, #61 twelve staged shelters, #62 route coverage; V82/V83).
> - **The real-world-simulation principle** ([[feedback_real_world_simulation]]): every catalogue object must be
>   functional as real-world logic dictates. **ORPHAN-MATERIALS AUDIT COMPLETE — 99 → 0 true gaps** (V90–V95 +
>   code): timber buildings consume the carpentry chain, logs mill to timber, cooking recipes, fletching/
>   adhesives, worked points, garments, tea/tinder/bedding/bait, and the code-path/fire-table closures.
> - **Three new gameplay systems:** **armour + combat defence** (V97, confront blunts a mauling), **poison**
>   (V98, venom-tipped spears), **light/darkness** (fine sight-work needs a fire or a lit lamp at night).
> - **Net-new #75 catalogue breadth** (V99–V108): tea/grain, greens/roots/morel, fruits, cordage fibres, tinder,
>   **dye→pigment→dyed cloth**, **grain→flour→flatbread**, **soapstone bowl**, **whetstone→sharpening**,
>   **hammerstones** — each with a real consumer.
> - **EPIC #191 Bare-hand handwork — ✅ COMPLETE** (V109–V118 + code): all 8 stories closed — carrying objects
>   (**#194**, V109+V113), **#196 food handling** (V114), **#197 clay beads/seals** (V115), **#195 bedding+covers**
>   (make_bed/PLACE_WINDBREAK/PLACE_COVER), **#192 raw materials** (V116), **#199 limits-proof** (V117+dr0119),
>   **#193 twist/braid/knot/lash**, **#198 body wraps + smoke wrap** (V110+V118, smoke-exposure vector) — the whole
>   gather→prepare→bind→shape→use→salvage loop, all tool_class NULL bar the one deliberate gate (crack_walnut).
> - **EPIC #123 Survival viability — ADVANCED** (V112 + code): the **arrival viability validator** (`arrival_viability()`
>   + `ArrivalViabilityService`) labels starts VIABLE/CHALLENGING/REJECTED from biome + 8-neighbour envelope +
>   water + wildlife pressure. **`ChronicleService.selectSpawn` now awakens a Chronicle only on a validator-approved
>   coordinate** (prefer VIABLE → fall back CHALLENGING → never REJECTED; old forest/grassland heuristic kept only as
>   a last-ditch net). `dr0113-survival-coverage.sql` pins that all nine survival categories stay content-backed and
>   every land start biome feeds and warms from its own ground. **#124 CLOSED.** **#135 forest-floor materials
>   CLOSED** (V120 — pine_resin now bare-hand scavenge-able; all 8 obtainable+used; dr0122). **#125 Recovery gap
>   filled** (V119 — bare-hand first-aid tier bandage/splint/sling wired into bindWound with distinct effects;
>   dr0121; plus V121 resin_torch portable light, V122 stone-boiling for safe water without a fireproof pot, and
>   the existing (V57) digging_stick made functional — GATHER_CLAY +1 with one carried; rest of #125 audited as
>   existing equivalents). **#126 defence — STARTED** (V123 early graded shields bark/reed=4, rawhide=6 vs war
>   shield 7; V124 early clubs — wooden/stone +22 blunt in confront, and the existing stone_maul/stone_hammer now
>   usable as weapons; V125 sling — amplifies thrown-stone capability (up-to-10→up-to-24, viable vs AERIAL); camp alarm — CAMP_ALARM cuts passive-ambush −15; carried raw kill (raw_game_meat/raw_fish) draws predators +10 (cook/store/cache to end it); dr0125–dr0129). Remaining on the
>   EPIC: more of #126 (throwing_stick/sling+stones/alarms/escape aids)/#127/#128 catalogues, and #129 the end-to-end first-day survival *run* across the six biomes under
>   night/rain/cold/heat/injury/low-food-water (CI integration scenario).
>   **TECH DEBT — RESOLVED 2026-08-13:** `WildlifeEncounterService.confront` had grown a per-item COUNT subquery
>   for every combat item (handWeapon/stones/armour/poison/lightShield/rawhideShield/blunt/sling/javelin/bow/arrows
>   — ~13, with 12 chronicle params). Refactored to TWO LATERAL conditional-aggregation passes — one over
>   `equipment_attachment` (worn) and one over owned `world_object` (carried) — collapsing 12 params to 1 and ~13
>   subqueries to 2 scans, same Combatant column order/semantics. Proven behaviour-preserving: an old-vs-new
>   set-wide `EXCEPT` on a 200-chronicle / 912-combat-item random dataset diverged by 0 rows (both directions);
>   compile clean, backend suite green, 55/55 SQL regressions + routing probe (0 misses) green. Add future modifiers
>   as new `SUM(CASE …)` terms in the relevant LATERAL, not another subquery. **Ranged kit #132: sling
>   (V125), javelin (V126), and the pre-existing bow chain now wired (+40, opens AERIAL, needs arrows) — dr0130/dr0131.**
>   **CHIEF-ENGINEER ASSESSMENT (2026-08-13):** the survival SYSTEMS are verified complete & correct — physiology
>   (temp/water/food/illness/smoke/exposure), **food freshness+spoilage+preservation** (a finished wired subsystem,
>   see [[reference_food_preservation_architecture]] — do NOT rebuild; RAW 18h<COOKED 72h<SMOKED 30d<DRIED 45d<SALTED
>   60d; storage re-owns items so caching/cooking ends the raw-meat predator draw), danger/ambush/defence (shields,
>   clubs, sling, poison, armour, alarm, fresh-kill draw), perception, fire, light. #127 camp infrastructure ~80%
>   present as staged assemblies. **Remaining #123 catalogue items (#125 tail/#126 escape/#127 food-cache-privacy-
>   lookout/#133) are mostly existing equivalents, or need PREREQUISITE subsystems (#218 sanitation, #220 decay for
>   non-food rot, perception-range) or a ground-search resolver refactor — NOT quick clean fills.** Next high-value:
>   either the #133 ambient-materials resolver language (canonical search_ground/look_for_tinder phrasings → existing
>   gathers; intricate classifier work, do carefully) or pivot to M2. Method: **audit for existing equivalents,
>   fill only genuine no-equivalent gaps with a real function** — never a decorative card.
>
> **EPIC #64 Action Catalogue — scorecard:**
> - **#65 perception — ✅ CLOSED.** observe/inspect/examine/analyze/investigate (incl. #33 subject scoping) +
>   search/listen/smell/feel (`ExaminationService.sense`) + read/review_record (`readDocument`) + measure
>   (`ExaminationService.measure`) + identify (folds into EXAMINE).
> - **#66 body care — ✅ CLOSED.** rest/sleep/wash/drink/relief/treat_wound existed; added `WARM_BODY`/`DRY_BODY`/
>   `COOL_BODY`/`SHELTER_BODY`/`STRETCH` (new `ChroniclePhysiologyService` methods; `fireInReach`/`shelterInReach`).
> - **#67 manipulation — CORE done, OPEN.** full take/place/store/retrieve/pack/unpack aliases; container
>   **open/close/seal** access-state (**V72**). *Remaining:* tie/untie, stack/pile, move/drag/push.
> - **#68 gathering — ✅ CLOSED.** gather aliases (forage/harvest/take-all); WASH scoped to the body; **V73**
>   prep-verb vocabulary; grounded (non-generic) failure for recognised material work (`isMaterialWork` +
>   `MATERIAL_UNRESOLVED`); effort/skill → yield (`gatherBonus`, 0–2 extra, capped by capacity + depletion).
> - **#69 crafting/transformation — ✅ CLOSED.** most verbs already routed; added **REPAIR_ITEM** (mends a
>   worn/broken item one condition step, consuming cordage/fibre) filling the empty MAINTAIN category; **V74**
>   craft-verb synonyms.
> - **#70 construction/site-work — OPEN (dismantle + repair done).** `DISMANTLE` (ConstructionService.dismantle:
>   take a construction apart, DESTROYED+DISMANTLED, UUID+history kept, recover a fraction of materials);
>   **`REPAIR_STRUCTURE`** (ConstructionService.repairStructure: mend/maintain any standing construction — not just
>   the lean-to — +25 integrity from kind-appropriate carried material; grounded). *Remaining:* the staged-building
>   verbs (clear_site/stake_out/level/dig_posthole/set_post/raise_frame/brace/weave_wall/daub/thatch/line_hearth/
>   pave/fence/gate/drain) — **now with a real home** in the V83 staged assemblies (they ARE those assemblies'
>   stages); what's left is a routing layer mapping each bare verb to advance the relevant in-progress assembly.
> - **#71 fire/water/cooking/camp — ✅ CLOSED.** Fire management (`EXTINGUISH_FIRE`/`BANK_FIRE`/tend→`FEED_FIRE`);
>   water handling (**V81** raw/filtered/clean water axis: `COLLECT_WATER`/`BOIL_WATER`/`FILTER_WATER`/`DRINK` +
>   `applyWaterborneRisk`); **`MAKE_BED`** (persistent GROUND_BED off the cold ground → rest/sleep bonus via
>   `beddedAt`), **`MAINTAIN_CAMP`** (site upkeep + `settleCamp`), and cooking-verb aliases
>   (grill/bake/broil/simmer/stew/braise → COOK_MEAT on flesh).
> - **#72 travel/terrain/wildlife/husbandry — OPEN (terrain + disengage done).** terrain crossing (wade/ford/
>   swim/cross/climb + direction → MOVE); `DISENGAGE` (retreat/flee/hide, non-exposing). *Remaining:* husbandry
>   (feed/lead/tether — needs a **tamed-companion ownership model**), stalk/watch tactics.
> - **#73 resolver fallback/telemetry — OPEN (functional intent met; commented).** routing-miss telemetry +
>   grounded failures + no-mutation guarantee + growing regression matrix already in place. *Deliberately
>   deferred:* the data-owned **alias-catalogue table** and distinct **outcome codes** (a focused infra refactor,
>   not to be rushed — see the issue comment).
> - **EPIC #54 supply chains — ADVANCED (V75–V83).** #56 containers, #57 aids, #58 tools, #59 clay/ceramic,
>   #60 provisioning, #62 route coverage. Fixed a latent bug — `createCraftedItem` never created
>   `container_properties`, so every process-made container could hold nothing; now populated from
>   `container_capacity_default`. **#55 gatherable building stock — done (V82):** straight_sapling + straw via the
>   flora system (geological stock deferred with the lime chain). **#61 staged shelters — done (V83):** all twelve
>   settlement-core structures as staged assemblies (huts, fences, hearth, catchment, platform, bridge, gate,
>   wood store, landing), the two enclosing huts wired into the exposure model. **#62 route coverage — mechanism
>   done** (tracks catalogue completion). *Remaining:* #56 sited storage objects + more portables; #57 named
>   logistics objects (carry_pole/yoke/sledge) + handcart chain; #59/#60 the deferred lime chain.
> - **#75 raw-materials catalogue — IN PROGRESS (V84–V91).** Two threads: (a) net-new material slices — fibre→
>   cordage (V84), knappable stone (V85), nut foods (V86), medicinal poultices (V87), wild foods (V88), soap
>   (V89); each = real source + a VERIFIED use (or edible FOOD) + a `dr00NN` regression + named-material-beats-
>   generic matcher discipline. (b) **the ORPHAN-MATERIALS AUDIT** — the user's governing principle
>   ([[feedback_real_world_simulation]]): *every* catalogue object must be functional as real-world logic, never
>   a token; never remove an orphan, ADD the mechanic. Audit method: obtainable `item_definition` with no
>   consumer in `material_process_input` ∪ `material_process_input_group` ∪ `assembly_stage_requirement`, minus
>   legitimate code-path consumers (FOOD→EAT, pelts→craftGarment, tinders→LIGHT_FIRE, poultice→bindWound,
>   soap→wash). Started at **99 MATERIAL orphans**; now **58**.
>   - **Vertical A DONE (V90/V91), Vertical B DONE (V92 — 7 cooking recipes, cook fixed to PROCESS).** the carpentry chain sawed every building component (floorboard/ridge_beam/
>     joined_frame/door_blank… 25 of them) but nothing built with them, and logs milled to timber *from nothing*.
>     V90 adds `log_cabin` (enclosing, wired into the shelter set) + `timber_barn` consuming all 25 components;
>     V91 wires `split_planks`/`timber_from_log`/`notch_log` to consume a species log via input GROUP.
>   - **Vertical B DONE (V92):** 7 cooking recipes (root_vegetable_stew/grain_porridge/acorn_flatbread/
>     herbal_infusion/cooked_mushrooms/trail_cake/berry_compote) — fire + water + ingredient input-groups; also
>     fixed `cook` miscategorised INHABIT→PROCESS so "cook a stew" routes. FOOD ingredients now cook, not only EAT.
>   - **Vertical C DONE (V93):** any feather fletches / any adhesive binds — input-groups on fletch_arrows
>     (feather/harpy/roc) + assemble_arrows binder (pine_pitch/birch_tar/fish_glue/propolis) + tar_cordage
>     (pitch/birch_tar). Closed harpy_feather, roc_feather, birch_tar, fish_glue, propolis. **MATERIAL orphans
>     now 53** (of which ~9 are code-path-used and fine: bear_pelt, char_tinder, ember_bundle, tinder_nest,
>     flint_stone, iron_pyrite, lens_crystal, herbal_poultice, soap → ~44 true gaps left).
>   - **Vertical D partial DONE (V94):** 13 animal hard parts (horn/tusk/fang/claw/talon/bone/stinger/thorn) →
>     `carve_point` → `worked_point`, which tips arrows. **Vertical E DONE (craftGarment code):** fish_skin_leather,
>     leather_offcut, snake_skin, wool_cloth, felt_sheet, textile_material → garments; silk_fiber/
>     spider_silk_thread → stitching thread (dire_wolf_pelt/troll_hide were already garment-used). **Misc (V95 +
>     craftTinder):** elder_flower/dried_herb_bundle → tea; cattail_fluff/birch_polypore → tinder.
>   - **Small batch DONE (V96):** earthworm → fishing bait (confront `fish`); reed_mat/cattail_stalk/
>     water_lily_pad → `make_bed` BEDDING; leather_boot_sole → garment leather; tarred_cordage → waterproof binder
>     (bucket/bark-container/burden/harness); smoke_hood → completes the smoke rack.
>   - **The three systems DONE:** **Armour (V97)** — scale_armour/chitin_helm/war_shield from wyvern_scale/
>     chitin/turtle_shell/wing_membrane, and `WildlifeEncounterService.confront` counts worn armour to blunt a
>     mauling (-7 severity/piece, floored at 1). **Poison (V98)** — `coat_spear` tips a primitive_spear with any
>     venom → poisoned_spear; confront counts it as a weapon **and** adds a +20 kill-odds bonus. **Light** —
>     fine sight-work (read/write/sketch/examine/analyse/investigate/measure) fails after dusk/before dawn unless
>     a fire is in reach or a portable light is spent (`isDark`/`isSightWork` guard + `consumePortableLight`
>     burns a rushlight/candle, or fish_oil in the oil_lamp).
>   - **✅ AUDIT COMPLETE — 99 → 0 true gaps.** Every obtainable MATERIAL now has a real-world-faithful use.
>     The audit *query* still lists ~29 as "unconsumed" because it only sees process/assembly/input-group rows;
>     **all 29 are consumed by a CODE PATH or the fire-method table** and are intentionally fine — do not
>     re-close them: garments (bear_pelt, dire_wolf_pelt, troll_hide, snake_skin, fish_skin_leather,
>     leather_offcut, leather_boot_sole, wool_cloth, felt_sheet, textile_material, silk_fiber,
>     spider_silk_thread → craftGarment), tinder (cattail_fluff, birch_polypore, char_tinder, tinder_nest,
>     ember_bundle → craftTinder/LIGHT_FIRE), bedding (reed_mat, cattail_stalk, water_lily_pad → make_bed),
>     light (rush_light, tallow_candle, fish_oil → consumePortableLight), bait (earthworm → fish), medicine/
>     hygiene (herbal_poultice → bindWound, soap → wash), firestarters (flint_stone, iron_pyrite, lens_crystal →
>     `fire_method_requirement`, reusable so not consumed). **When auditing in future, subtract these before
>     flagging.**
>   - **Net-new breadth so far (V99–V108):** V104 dye/pigment (ochre → pigment → dyed cloth → garments), V105
>     grain→flour→flatbread, V106 soapstone → carved bowl (container), V107 whetstone/grit → sharpening (rewired
>     repairNamedItem: sharpen with a whetstone, not cordage), V108 granite/basalt cobbles → hammerstones
>     (STRIKING). Earlier: tea/grain (chamomile, pine-needle, wild rice), greens/roots/morel
>     (burdock/bulrush roots, nettle/watercress greens → cooked_greens, morel), fruits (crab apple, sloe,
>     bilberry → compote), cordage fibres (bramble, cattail leaf, bulrush → cordage), tinder (wood shavings,
>     fatwood). ~19 new materials, all real-world-functional via existing consumers (V92 recipes, twist_cordage,
>     craftTinder). Same clean pattern: flora/process source → real use → dr00NN regression → GATHER_PLANT noun.
> - **#191 Bare-hand handwork EPIC — STARTED (V109/V110 + code).** The contract: bare-hand procedures are
>   material_processes with `tool_class=NULL`, producing improvised, low-capacity, short-lived results; the
>   cut/knap/fell boundary stays enforced by tool requirements. Done: **#194 carrying objects — CLOSED** (V109 —
>   leaf wrap, folded bark cup, bark fold container, grass sling, reed pouch; V113 — bark scoop, tied reed sheaf,
>   cordage carry loop, grass carry-mat, tied carry bundle; all ten no-tool, functional containers; new
>   hand-gathered big_leaf + dry_grass_bundle; keywords tuned classifier-safe, dr0114 pins reachability),
>   **#198 body wraps** (V110 — grass ankle wraps +
>   fibre hand wraps paired L/R, reed hat, bark hood, moss pad; worn+warming, not armour), **#195 bedding + cover —
>   CLOSED** (make_bed dry grass/leaves/moss + **PLACE_WINDBREAK**; plus a generic **PLACE_COVER** intent placing
>   SUNSHADE/RAIN_COVER/GROUNDSHEET/STONE_RING construction_projects — each with a graded, non-shelter weather
>   effect in ChroniclePhysiologyService: windbreak now cuts tick wind ×0.5, rain-cover cuts rain wetness, sunshade
>   cuts heat load when hot, groundsheet counts as a bed, stone-ring reflects fire heat; dr0117 pins co-location).
>   #193 cordage twist
>   is already bare-hand. **#196 food/water handling — CLOSED** (V114 — shell_hazelnut/peel_root/wash_root bare-hand,
>   crack_walnut STRIKING-gated hard shell, handled roots feed cook_root_stew so handling ≠ safety; new PROCESS
>   verbs peel/husk/shell/shuck; dr0115 pins reachability + the safety distinction). **#197 clay/mud/pigment —
>   CLOSED** (V115 — most already bare-hand via temper_clay/form_vessel→unfired_vessel/mix_daub/pack_earth_floor/
>   grind_pigment; added shape_clay_bead + press_clay_seal → fire_clay_trinkets (separate fire chain) →
>   thread_trinket_cord wearable; unfired≠durable gate, dr0116). **#192 raw materials — CLOSED** (V116 — ten
>   bare-hand materials, each obtainable via NULL-tool flora_drop or NULL-tool mineral AND consumed via 7 feeder
>   processes or craftTinder; STRIP_BARK collision avoided; dr0118). **#199 limits-proof — CLOSED** (V117 + dr0119
>   — proof suite over all 29 handwork processes: no tool routes bare-handed except crack_walnut, no creation from
>   nothing, no mass gain [found+fixed flatten_bark/ret_bark_strip], carriers finite; persistence/Auditor covered by
>   the standard world_object invariants). **#193 twist/braid/knot/lash — CLOSED** (all mechanics already exist:
>   twist_*/ret_*/plait_withy_rope/knot_cordage_loop/lash_burden_frame/tie_*). **#198 body wraps + smoke wrap —
>   CLOSED** (V110 wraps/hat/hood/pad; V118 added `smoke_face_wrap` + a **smoke-exposure vector** — an active fire
>   in an enclosed shelter with no vent gives a small non-lethal illness pressure; a `smoke_hood` at the hearth
>   vents it, a worn wrap eases it; this also gave the existing smoke_hood a real effect. Balance: unvented indoor
>   fire is now a mild hazard, avoidable. dr0120). **✅ EPIC #191 COMPLETE — all 8 stories closed.**
> - **NEXT, in order:** the #75 orphan audit is DONE — every existing material is functional; continuing *net-new*
>   catalogue breadth toward "100" (unbuilt families: dye/pigment [needs a dyeing consumer], more stone/mineral
>   [whetstone/grindstone needs a sharpening/grinding mechanic], more forageables) and its sibling stories
>   **#76–#78 / #46–#47** ("100 portables / structures / procedures"). Then **#191** bare-hand handwork → **#123** survival viability → remaining #54 named objects →
>   then **M2** (84 issues). Deferred M1 tails: #67 tie/stack/drag, #70 staged-building-verb
>   routing,
>   #72 husbandry companions, #73 alias-catalogue infra — each a distinct new
>   model/content effort. **Full M1+M2 spans many sessions.** *(Recurring pattern for new craftable objects: a
>   migration like V75 — item_definition + container_capacity_default + material_process [CRAFT, keyword that
>   classifies to its category, subjects, obtainable inputs, output mass < input mass, review_state VERIFIED] —
>   then apply on a throwaway Postgres and run `routing-reachability-probe.sql` until clean.)*
>
> **How the work is done (the proven rhythm):** read the story's exact action catalogue → check current coverage
> (spin a throwaway Postgres, apply V1..Vn, query `category_term`/`material_process`; grep the classifier/services)
> → fill the gap (new intent + `ChroniclePhysiologyService`/`PhysicalItemService`/`ConstructionService`/
> `ExaminationService` method + classifier rule + duration/labor/capability maps; or a vocabulary migration whose
> categories AGREE with existing process keywords — see V73/V74, and the bundle/peel/rive exclusions) → add a
> case to `IntentClassificationRegressionTest` and, where DB-backed, a scenario in `PlaytestBugFixesIntegration
> Test` → `mvn -o test` (all green) + re-run `routing-reachability-probe.sql` after any process/vocab migration →
> commit `-F` a scratch file, push, close/comment the issue. **New intents this session:** `CRAFT_NET`,
> `CRAFT_BELT`, `PICK_UP`, `STORE`, `OPEN_CONTAINER`, `CLOSE_CONTAINER`, `SEARCH`, `LISTEN`, `SMELL`, `FEEL`,
> `READ`, `MEASURE`, `WARM_BODY`, `DRY_BODY`, `COOL_BODY`, `SHELTER_BODY`, `STRETCH`, `REPAIR_ITEM`, `DISMANTLE`,
> `EXTINGUISH_FIRE`, `BANK_FIRE`, `DISENGAGE`.
>
> **DR-0022 "Phase-0 parity" — ALL deterministic layers SHIPPED last cycle** (migrations → V70; full detail in
> milestone #12 below; tracker [06.5](systems/06.5-Phase0-Parity-Build-Plan.md)). **Two locked design principles
> stay in force — do not re-litigate:** (A) reachability is **knowledge-scoped & capped at the chunk/locality**
> (do NOT tighten Layer-1 reach to the zone); (B) **quality is majorly the craftsman** (bare hands make superior
> work), a workstation is efficiency + a minor bounded assist, **never a gate**. Key-gated remainder (AI-on
> verification, F9 Interpreter memoization) unchanged — the operator's launcher run with the encrypted,
> password-gated key; the agent never handles the key/password.
>
> **What remains (all non-blocking or key-gated):** craft fetch-time + workstation time/effort efficiency
> (need a small `resolve()` restructure — the tick advances before the process resolves), per-action
> success-core narration breadth, and the Interpreter memoization/convergence (F9). **The DR-0021 five-AI
> pipeline is BUILT and gated off; AI-on verification is the operator's launcher run** (`DRAUGR_AI_ENABLED`
> + the encrypted, password-gated key — the agent never handles the key/password). **Two locked design
> principles (do not re-litigate):** DR-0022 → "Principles settled during Layer 4b" — (A) reachability is
> knowledge-scoped & capped at the chunk/locality; (B) quality is majorly the craftsman (bare hands make
> superior work), a workstation is efficiency + a minor bounded assist, never a gate.
>
> The DR-0021 tracker [06.4](systems/06.4-Runtime-Authoring-Build-Plan.md) is the companion (the AI pipeline
> this builds on). The numbered history below is the prior milestone sequence, all DONE.

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
10. **DONE — three-AI integration (built, gated off until an API key is set).** Plan and boundaries in [architecture/ai-integration.md](architecture/ai-integration.md). Phased and all delivered: (1) Simulation Agent narration = Task #21, wired into `resolve()`; (2) Persistent State Architect = authoring-time, drafts reviewed migrations from `routing_miss_backlog`; (3) Persistent State Auditor = read-only summaries. All three have Overseer surfaces and unit tests; the whole mechanism is inert until `DRAUGR_AI_ENABLED=true` + `ANTHROPIC_API_KEY`.
    - **DONE — Phase 1a: shared model-client foundation (this session), gated OFF by default.** New `com.devosphere.draugr.ai` package: `AiProperties` (`draugr.ai.*` config), a provider-agnostic `LanguageModel` seam, `AnthropicLanguageModel` on the official `com.anthropic:anthropic-java` SDK, and `AiConfig` which yields a no-op model unless `enabled=true` + a key is present. `SimulationNarrator` turns a `PerceptionFrame` + the deterministic prose into a witness-stance refinement prompt and appends **one** AI sentence — or returns the deterministic prose unchanged on disabled/timeout/error/blank (`LanguageModel.generate` never throws). Config: `DRAUGR_AI_ENABLED` (default false), `DRAUGR_AI_API_KEY`/`ANTHROPIC_API_KEY`, `DRAUGR_AI_MODEL` (default `claude-opus-5`), max-tokens, timeout. Verified: 5 DB-free unit tests (stub model, every fallback branch) green; the Spring context boots clean with the feature off (`AiConfig` logs "AI narration disabled … deterministic prose only").
    - **DONE — Phase 1b: wired into `ChronicleActionService.resolve()`.** After `perception` is final and the frame built: `if (narrationRouter.shouldUseAI(intent,outcome,attention,text,stateChanges,0,null,died,false)) perception = simulationNarrator.refine(frame, perception);` a genuine change re-persists (`UPDATE chronicle_action`) and updates the returned frame. Verified: a HIGH-attention observe (a would-route moment) resolves unchanged with AI off, and the whole context boots clean (30 DB-free tests green).
    - **DONE — Phase 2 groundwork: `PersistentStateArchitect`.** Reads the frequency-ranked `routing_miss_backlog` and drafts a migration proposal per gap (VOCABULARY/KEYWORD/SUBJECT/MECHANIC) on Opus 4.8 — **proposes, never commits** (a human runs it through the V53 gate). Unit-tested against a stub model. Not wired to any runner/endpoint yet.
    - **DONE — Phase 3: the Auditor AI.** `AuditorSummarizer` (Sonnet 4.6) turns a read-only `AuditReport` (consistency flag + violated invariants) into a plain operator summary — describes only, never proposes a repair/write. All three engines now have reachable surfaces: Simulation on the action path; **Architect** at `GET /api/architect/backlog` + `POST /api/architect/propose-top` (drafts a migration; **never applies**); **Auditor** at `GET /api/audit/summary`. Every surface still responds on deterministic data with AI off (`summary`/proposal null), so the mechanism is testable before a key exists. Verified live (AI off): `/api/audit/summary` → `{consistent, violations, summary:null}`, `/api/architect/backlog` → `[]`, `propose-top` → 204. 44 DB-free tests green (11 across the three AI engines).
    - **Per-agent models = the tiering lever (user's business model).** narration → `claude-haiku-4-5`, Architect → `claude-opus-4-8`, Auditor → `claude-sonnet-4-6` (config `draugr.ai.{narration,architect,auditor}-model`; `LanguageModel.generate(model, system, user)` takes the model per call). On launch, subscription tier can select stronger models per account. For solo Engineering-Phase-1 testing the defaults are deliberately modest — the goal is a stable, seamless 3-AI mechanism, not model strength.
    - **DONE — Overseer UI wired (frontend).** `OverseerAgents` (in `?mode=overseer`) surfaces the Auditor (consistency badge + AI prose summary) and the Architect (routing-miss backlog + a "draft a proposal for the worst gap" button showing the drafted migration — review-only, never applied). Both render on deterministic data with AI off; the Simulation Agent needs no separate UI (its output is the playthrough narration). Verified in-browser against a live backend: the Auditor shows "World consistent"; the Architect lists seeded VOCABULARY gaps (ferment/forge/distill) and, with AI off, the draft button shows the graceful "enable draugr.ai" note. Frontend builds clean.
    - **DONE — the deferred items are cleared:**
      - **`SALT_DEPOSIT` now functional (no migration needed).** `gatherMineral` treats a chunk's salt/clay deposit sites as effective biomes (a salt flat reads as `SALT_DEPOSIT`, resolving rock_salt's affinity there), `activeLocation` overlays the `SALT_DEPOSIT` presentation, and the **classifier gained `salt`** (rock/sea salt was gatherable by affinity but unreachable by phrasing). Proven E2E: 13 rock_salt gathered at a WETLAND salt spring — impossible without the overlay.
      - **FINE-grade material source reachable.** A careful mineral search now grades the nodule by the attempt (`QualityGrade.attempt`), so careful gathering → FINE stone → a careful downstream chain can finish FINE instead of being capped at SOUND. Proven E2E: FINE flint from careful gathering.
      - **Apply-a-proposal is documented as a human step, by design** (ai-integration.md): there is no runtime auto-apply — that would break the "runtime never alters schema" boundary; the Architect drafts, a human saves it as a `V*.sql`, and Flyway + the V53 gate validate it like any migration.
    - **DONE — stabilization sweep.** Full backend suite green (**111 tests, 0 failures**; the 3 Testcontainers integration tests run in CI — they abort locally on the Docker-npipe quirk). Frontend builds clean. Booted-stack core-loop smoke (observe → gather fiber/stone/branches/berries → craft knife/hammer/hatchet → build fire pit → strip bark → rest/drink/eat → hygiene): **23 actions, 0 non-201 responses, auditor consistent, chronicle LIVING**. Each of this session's specific paths was also verified E2E along the way (all 3 AIs inert-when-off, fell→ground→split, salt gather at a SALT_DEPOSIT, FINE mineral, PDF export, #14–#18).
    - **NEXT:** flip the AI switch and live-verify all three with a real key (`DRAUGR_AI_ENABLED=true` + `ANTHROPIC_API_KEY`, next usage-limit cycle); tune prompts against real playthrough output.
    - Still deferred: FINE-grade material sources, and the `SALT_DEPOSIT` world-generator placement.
11. **DONE — GitHub playtest backlog cleared (#25 + #28), migrations through V67.**
    - **#25 Inspect / Analyze / Investigate — examination verbs scaled by mastery.** `ExaminationService` (deterministic, witness-stance) resolves a subject — a reachable item the text names, else the place itself — and returns tiered facts at a depth set by the relevant mastery: INSPECT/`EXAMINE` (perception), ANALYZE (insight), INVESTIGATE (perception+insight+knowledge). **V67** added two hidden per-chronicle capability dimensions — `insight_familiarity` + `knowledge_familiarity` — so the families are now **8** (LOAD, LOCOMOTION, FINE_MOTOR, AIM, ATTENTION, RECOVERY, INSIGHT, KNOWLEDGE). Each verb builds the mastery it leans on; AI enrichment of the deepest tier is the non-load-bearing future layer. Classifier: ANALYZE/INVESTIGATE own their words, `EXAMINE` needs a pointed determiner (this/my), bare looking stays OBSERVE. Commit baf8151.
    - **#28 regional weather — geography-driven, no schema change.** `BiomeClimate` no longer keys off the biome label with hand-tuned constants; it derives the felt climate from the chunk's own geography: temperature follows the environmental lapse rate from real `elevation` (a high peak is genuinely colder than a low one, continuous not bucketed), a latitude gradient from `grid_y`×world-height, and a wet-bulb rain↔snow bias from `moisture`. The biome label now contributes only the non-altitude residual (sea damp, canopy shelter, open-ground sun/wind). Both callers (`groundPerception`, `activeEnvironment`) feed it the geography; SQL EXPLAIN-validated on a throwaway Postgres. **Open follow-up:** the weather KIND is still one world-wide front, so spatial variation of the kind itself (a rain shadow behind a range) needs a per-region weather model. Commit 640b783.
    - **Materials & Recipes doc regenerated from the live DB** (`Project Draugr — Materials & Recipes.md`, gitignored local file): every item/process/assembly count and every input→output quantity verified against a throwaway Postgres at V67. Now a complete, specific catalogue — all 130 processes by domain, 6 assemblies with exact stages, flora/wildlife/mineral acquisition by biome. Snapshot V1–V67.
    - Full suite **142 tests green**; `BiomeClimateTest` (8 cases) and `ExaminationServiceTest` guard the new behaviour, `IntentClassificationRegressionTest` covers the new verbs.
12. **IN PROGRESS — DR-0022 "Phase-0 parity": make Phase 1 EXCEED the ChatGPT playthrough.** Live tracker: [06.5-Phase0-Parity-Build-Plan.md](systems/06.5-Phase0-Parity-Build-Plan.md) (its ▶ CURRENT line is authoritative). Origin: reviewing the user's Phase-0 (single-ChatGPT, 13 hand-saved JSONs) "Wolf Kingdom" save + issues #29–#37 showed Phase 1 was *sounder but thinner*. **ALL deterministic, key-free layers SHIPPED** (~17 commits, **145 tests + 3 SQL regressions green**, **migrations → V70**):
    - **Unified reachability** — one CTE (carried ∪ location-sited, descending containment); on-site storage contents + racked tools are now reachable; every sourcing/tool/grade path routed through it. Fixed the "documents on a shelf can't be read" class. Regression `reachability-onsite-storage.sql`.
    - **Perception names real life** — `ExaminationService.presentLife(chunk, acuity)` names the actual flora/wildlife/fish/insects present, scaled by attention+perception; wired into OBSERVE + the examination verbs. Fixes "can't hunt what you can't see" (#37/#33).
    - **Thick objects (V68)** — additive `object_attribute` + `object_modification`; REFINE records *what* changed; examination surfaces an object's evolving history. Never touches the catalogue or mass balance.
    - **Documents/maps read-back** — unified `LiteratureService` reachability, so a map in an on-site store lists and reads (was the "content returned nothing" bug).
    - **Narration overhaul (#30)** — `ground()` scales immersion by attention (land + light + a sound/smell on deliberate looking); flagship craft success prose; **rejection prose** that says *why* and *where the lack is* ("what is missing is not here — it lies wherever you last set it down").
    - **Workstations (V69)** — `station_kind` on `material_process`; a reachable bench/loom gives **efficiency (yield) + a minor bounded quality assist** (attempt +1 step, still capped by materials) — **never a gate, never carries the grade**; buildable benches/loom via `CRAFT_WORKSTATION`.
    - **Multi-zone settlements (V70)** — many named zones per chunk (relaxed the PK), `chronicle.current_zone` label, "go to \<zone\>" intra-chunk walk, `survey()` lists the settlement's zones. **Reachability stayed chunk-wide** (the lighter model — no zone→chunk ripple).
    - **Quantified AI context (F2)** — `reachableInventory` feeds the Interpreter/Architect "dry_branch x5", closing the counts-blind gap. **Cross-chunk reject names the store** ("what there is of it sits at your Wood Store, not here"). Quick fixes **#32** (bathe at any fresh water) + **#36** (net≠fishing).
    - **Locked design principles (do not re-litigate)** — DR-0022 → "Principles settled during Layer 4b": (A) reachability is **knowledge-scoped & capped at the chunk/locality** (Layer 1 chunk reach is correct — do NOT tighten to the zone; other chunks are a journey the player takes, never auto-walked); (B) **quality is majorly the craftsman** (bare hands make superior work), a workstation is efficiency + a minor bounded assist, never a gate.
    - **Remaining (all non-blocking or key-gated):** craft fetch-time + workstation time/effort efficiency (need a small `resolve()` restructure — `ticks.advanceBy` runs before the process resolves); per-action success-core narration breadth; Interpreter memoization/convergence (F9). **AI-on verification is the operator's launcher run** (`DRAUGR_AI_ENABLED` + the encrypted password-gated key; the agent never handles the key/password) — the whole deterministic substrate it rides on is now in place.

13. **DONE — playtest bugs #29–#44 all fixed & closed; IN PROGRESS — Milestone 1 (autonomous build).** The GitHub milestones (M1–M4) are the live plan; M1 is active. Work on `development`, pushed, as `devosphere.tech` (never the `johncalado` account). **EPIC #64 Action Catalogue is now complete across all 9 stories; EPIC #54 supply chains started (#56 advanced). Migrations → V75. 158 backend tests + 11 SQL regressions green; routing probe clean.** (This entry records the playtest-bug sweep and M1 kickoff; the ▶ ACTIVE NOW banner above carries the authoritative per-story scorecard and next steps.)
    - **All 13 playtest [BUG] issues resolved and CLOSED** (`fcb3e2f`, `e83d134`, `ebd1f89`, `d997346`, integration `1020c0f`). These came from a playthrough run *after* the DR-0022 work, so each was reproduced against the live code, not assumed fixed:
      - **#43/#36/#44 nets, #35 belt** — catalogued as technique knowledge but with no runnable make-route. Added explicit crafts `CRAFT_NET` (mesh from cordage; **V71** `fishing_net`/`landing_net`) and `CRAFT_BELT` (`utility_belt` from cordage + fibre). *Using* a net (cast/haul) routes to `FISH`; *making* one does not.
      - **#34/#31 baskets** — `craftBasket` demanded `plant_fiber ×8` only. Now weaves from any flexible stock: plant fibre or vine (1 weave unit) or cordage (2 units), eight units total (`basketWeaveUnitsInReach`).
      - **#42 vine→berries** — a named-but-absent gather target (vine/bark/root/reed/sap…) fails with a wrong-site fact instead of substituting the nearest FOOD (berry).
      - **#29/#41/#40 logistics** — drop already grounded objects correctly; added `survey()` ground-object listing (visible), `PICK_UP` (from ground or a reachable container), and `STORE` (into a container, capacity/nesting pre-checked so a full one fails gracefully, not as a hard rollback).
      - **#33 inspect** — "inspect the &lt;subject&gt;" now routes to the subject-scoping `EXAMINE` (resolves the named reachable item, falls back to the place); scenery stays `OBSERVE`.
      - **#39 mark-and-name** — `markLandmark` upserted on the pre-V70 `(chronicle,chunk)` key; fixed to the relaxed `(chronicle,chunk,name)` conflict target (the reported raw persistence error).
      - **#32 bathe** — confirmed already working (WASH routing + `wash()` effect + `waterInReach` covers wetland/river-bank/spring/stream) and now pinned by a classifier test.
    - **M1 EPIC #64 Action Catalogue — started, two stories advanced:**
      - **#67** (`d8b3ced` aliases; `6dea580` access-state, **V72**) — full take/place/store/retrieve/pack/unpack alias coverage + container **open/close/seal** gating store & retrieve (recorded as an `ACCESS_CHANGED` transition). *Remaining:* tie/untie, stack/pile, move/drag/push.
      - **#65** (`987249f`) — sensory verbs **search/listen/smell/feel** via `ExaminationService.sense`, grounded in the chunk's real weather/water/fire/life/ground. *Remaining:* read/review_record, measure, identify.
    - **Resume:** finish #65 (read/measure/identify), then #68 → #69 → #70 → #71 → #72 → #73, then EPIC #54, #191, #123 — see the ▶ ACTIVE NOW banner. New regression/integration tests live in `backend/.../action/IntentClassificationRegressionTest.java`, `tests/regression/dr-bugs-*.sql`, and `backend/.../persistence/PlaytestBugFixesIntegrationTest.java`.

#### Why coverage, not correctness, is the cost driver

Four procedure simulations (15 m² cabin, fish preservation, bow production, leather armor) returned 0/12, 1/10, 3/12, 3/12 COVERED — and several of those were *false* COVERED. True coverage is roughly 5–10%. With only 20 processes, nearly every meaningful action still routes to the Architect.

**M1/M2 improves correctness, not cost.** Routing hardening makes the gaps honest; it does not make them fewer. V56 makes them countable. Step 2 makes them fewer.

The unlock: V53's review gate already makes machine-authored processes safe to accept — it caught 9 of 20 hand-authored recipes creating matter, deterministically and with no AI involved. Bounded one-time offline cost instead of an unbounded per-action one. Caveat: mass balance catches physics errors but not *plausibility* errors — a recipe can conserve mass and still be bad primitive technology. Needs tightly grounded generation prompts plus sampled human review (design rule #5).

**THEN: Task #21 — AI narration (the Simulation Agent's voice).** The seam is built: `NarrationRouter` decides whether to call, `NarrationEngine` supplies the `backendNarration` the refinement prompt builds on. See `docs/architecture/narration-engine.md` for the prompt template and cost model.

### Intents implemented (ChronicleActionService)
GATHER, HARVEST, CRAFT, EQUIP, UNEQUIP, DROP, BUILD, REPAIR, ABANDON, RESUME, LIGHT_FIRE, ADD_FUEL, MAKE_CHARCOAL, COOK, SLEEP, STRIP_BARK, GATHER_CLAY, GATHER_STONE_SLAB, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, WRITE, EDIT_DOCUMENT, SKETCH_MAP, DESIGNATE, MARK, TRAVEL, CONFRONT_WILDLIFE, REFINE, OBSERVE, GATHER_PLANT, FELL_TREE, RAID_HIVE, COLLECT_INSECTS, FISH, SNARE, TRACK, TAME, LURE, SET_TRAP, CHECK_TRAP, ADVANCE_ASSEMBLY, INSPECT, EXAMINE, ANALYZE, INVESTIGATE, REWORK, CRAFT_WORKSTATION (V69 benches/loom), PERSONAL_ACT, AGGRESSION_WILDLIFE, AGGRESSION_INANIMATE (multi-output & preserved-food outputs handled inside PROCESS_MATERIAL), **CRAFT_NET, CRAFT_BELT** (V71), **PICK_UP, STORE, OPEN_CONTAINER, CLOSE_CONTAINER** (M1 #67; V72 access-state), **SEARCH, LISTEN, SMELL, FEEL, READ, MEASURE** (M1 #65 perception; identify folds into EXAMINE), **WARM_BODY, DRY_BODY, COOL_BODY, SHELTER_BODY, STRETCH** (M1 #66 body care), **REPAIR_ITEM** (M1 #69 mend a worn/broken item), **DISMANTLE, REPAIR_STRUCTURE** (M1 #70 take a construction apart / mend any standing structure), **EXTINGUISH_FIRE, BANK_FIRE, COLLECT_WATER, BOIL_WATER, FILTER_WATER, MAKE_BED, MAINTAIN_CAMP** (M1 #71 fire/water/camp — closed), **DISENGAGE** (M1 #72 retreat/flee/hide)

### Known limitations (acceptable for now)
- Friction fire kit never wears out — infinite fires once made (wear/degradation future task)
- Three-layer model only wired into LIGHT_FIRE and CONFRONT_WILDLIFE — other complex intents (wound treatment, crafting) still binary
- stone_shelf is a container but documents placed in a location-container aren't reachable via owner-tree CTE (reading from shelves not yet wired)
- Named locations not shown in frontend header
- Furniture has no dedicated UI panel
- `fire_piston` catalogued but unreachable (no acquisition path); `field_journal` and `hand_drawn_map` likewise — left deliberately
- Chronicle Archive styling has never had visual/screenshot verification

---

## Three-AI Architecture (WIRED — gated off until an API key is set)

The full mechanism is built and inert-until-keyed; see [architecture/ai-integration.md](architecture/ai-integration.md) for the plan, boundaries, config, and surfaces. Package `com.devosphere.draugr.ai`: a provider-agnostic `LanguageModel` seam on the official `com.anthropic:anthropic-java` SDK, `AiProperties` (`draugr.ai.*`), and per-agent models (the launch-tiering lever). To go live: `DRAUGR_AI_ENABLED=true` + `ANTHROPIC_API_KEY`.

**1. Simulation Agent** (Haiku 4.5) — narration voice. `SimulationNarrator` is wired into `ChronicleActionService.resolve()`: on moments `NarrationRouter` judges worth a call, it appends one atmospheric sentence to the deterministic prose; on disabled/timeout/error it returns the deterministic prose unchanged, so the world's own narration always stands.

**2. Persistent State Architect** (Opus 4.8) — authoring-time. `PersistentStateArchitect` reads the `routing_miss_backlog` and **drafts** a migration proposal per gap; it proposes, never commits (a human saves an approved draft as a `V*.sql` through the V53 gate). Surface: `GET /api/architect/backlog`, `POST /api/architect/propose-top`, Overseer UI.

**3. Persistent State Auditor** (Sonnet 4.6) — read-only. `AuditorSummarizer` turns a read-only `AuditReport` into a plain operator summary; it only describes, never repairs. Surface: `GET /api/audit/summary`, Overseer UI. `PersistentStateAuditor.java` remains the invariant engine.

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
- **Task #21** — AI narration API integration: **DONE** (Simulation Agent narration wired; whole three-AI mechanism built). Only live activation remains — set `DRAUGR_AI_ENABLED=true` + `ANTHROPIC_API_KEY` and tune prompts against real output.
- Prior world/material deferrals (`SALT_DEPOSIT` placement, FINE-grade sources, apply-a-proposal workflow) are all **cleared** — see the resume point.

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
