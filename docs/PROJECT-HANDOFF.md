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
| `backend/src/main/resources/db/migration/` | Flyway migrations V1–V56. Next is V57. |
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

## What Is Built (Migrations V1–V56, all applied)

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

**Why routing hardening exists:** matching was lexical and global. "Split the fish" matched `split_planks`; "weatherproof the shell" matched the weather domain. A wrong match doesn't throw — it silently suppresses the Architect call the moment needed, which is worse than an honest gap.

**Correction to the original spec:** category scoping alone only separates 5 of the 8 recorded collisions — "split the fish"/"split the log" are both PROCESS. Scoping needs **two axes**: category *and* subject material. V54 implements both.

**V54 delivers:** `activity_category` (10), `category_term` (~180 weighted terms), `process_subject` (derived from each process's own inputs/outputs so it can't drift), `material_process.category_key` (NOT NULL, backfilled in the same migration so a missed row fails the migration rather than silently ceasing to match). Also broadened V52's subject-bearing keyword phrases to bare verbs — safe only now that category+subject must also agree.

**Resolution rule (documented in full at the foot of V54 — Java must not drift from it):** classify by summed term weight, `precedence` breaks ties; a process matches only if category **and** keyword **and** subject all agree; **if no category term matches, classification is NULL and the category condition is dropped**, not treated as "matches nothing".

**V55 corrects V54's data.** Wiring the Java and probing with ordinary phrasing exposed the opposite failure from the one V54 fixed: `dress_foundation`, `reinforce_timber` and `fire_vessel` had become unreachable by any plausible sentence, and `weave_large_basket` resolved to `weave_textile`. In every case a process's declared category disagreed with the category its own verbs classify to — an axis V54 introduced but never checked. V55 fixes the four and adds the check.

**M1 + M2 are COMPLETE.** `ActivityClassifier` (classification) and `ProcessMatcher` (the full rule) live in `com.devosphere.draugr.routing`; both `PhysicalItemService.runProcess()` and `ArchitectRouter` route through `ProcessMatcher`, so the rule has exactly one implementation. The Auditor carries three new standing invariants. 88 DB-free unit tests green; 56 migrations apply clean from scratch.

**Verified:** 8/8 recorded collisions blocked, 21/21 processes reachable by ordinary phrasing, and every miss correctly diagnosed as vocabulary / keyword / subject / mechanic — `ProcessRoutingTest`.

#### SETTLED: no AI at resolution time — read this before proposing one

**`docs/architecture/routing-and-coverage-strategy.md` is the decision record. Read it rather than re-deriving it.**

The question "should an AI layer help the ActivityClassifier work out what the player meant?" has been asked and answered: **no.** Four reasons, in short — (1) ~39 of ~46 probed actions missed because *the mechanic does not exist*, so a classifier would pay a call to confirm emptiness; (2) an LLM handed 20 candidates finds the nearest one, which manufactures exactly the false COVERED that V54/V55 exist to prevent; (3) `runProcess` writes permanent history, and V53's gate works precisely because what it gates is data sitting still — an inline classifier's output is a decision already executed, with nothing left to gate; (4) it grants a fourth agent authority that `core-agent-boundaries.md` never gave it.

**AI goes at authoring time instead** — one call per novel verb ever, one call per process ever, both permanent and compounding. The strategy doc carries the full argument, the backlog queries, the invariants that must not be broken, and the specific evidence that would justify reopening the question.

#### Resume point

1. **DONE — Step 1: measure.** V56 shipped. The backlog is live and directs the rest.
2. **NEXT — Step 2: bulk foundation generation through the V53 gate.** The actual API-cost lever; M1/M2 bought correctness, not coverage. Target 200–300 processes, generated offline as `DRAFT`, validated by V53 mass balance + V55 category agreement + V51 reachability + derived subject terms, sampled by hand for plausibility, and only then promoted to `VERIFIED`. Scope named by the simulations: staged assembly, joinery, building layers, timber preservation, salt/food preservation, fish processing, bow production, leather armour.
3. **Then Step 3: re-measure** via `routing_miss_backlog` and let the `MECHANIC` share decide what the next cycle does.
4. Then M3 (staged assembly), M3b (graded quality), M4 (vocabulary), M5 (coverage as standing measure).

#### Why coverage, not correctness, is the cost driver

Four procedure simulations (15 m² cabin, fish preservation, bow production, leather armor) returned 0/12, 1/10, 3/12, 3/12 COVERED — and several of those were *false* COVERED. True coverage is roughly 5–10%. With only 20 processes, nearly every meaningful action still routes to the Architect.

**M1/M2 improves correctness, not cost.** Routing hardening makes the gaps honest; it does not make them fewer. V56 makes them countable. Step 2 makes them fewer.

The unlock: V53's review gate already makes machine-authored processes safe to accept — it caught 9 of 20 hand-authored recipes creating matter, deterministically and with no AI involved. Bounded one-time offline cost instead of an unbounded per-action one. Caveat: mass balance catches physics errors but not *plausibility* errors — a recipe can conserve mass and still be bad primitive technology. Needs tightly grounded generation prompts plus sampled human review (design rule #5).

**THEN: Task #21 — AI narration (the Simulation Agent's voice).** The seam is built: `NarrationRouter` decides whether to call, `NarrationEngine` supplies the `backendNarration` the refinement prompt builds on. See `docs/architecture/narration-engine.md` for the prompt template and cost model.

### Intents implemented (ChronicleActionService)
GATHER, HARVEST, CRAFT, EQUIP, UNEQUIP, DROP, BUILD, REPAIR, ABANDON, RESUME, LIGHT_FIRE, ADD_FUEL, MAKE_CHARCOAL, COOK, SLEEP, STRIP_BARK, GATHER_CLAY, GATHER_STONE_SLAB, CRAFT_FIRE_KIT, CRAFT_TINDER, CRAFT_DESK, CRAFT_CHAIR, CRAFT_SHELF, WRITE, EDIT_DOCUMENT, SKETCH_MAP, DESIGNATE, MARK, TRAVEL, CONFRONT_WILDLIFE, REFINE, OBSERVE, GATHER_PLANT, FELL_TREE, RAID_HIVE, COLLECT_INSECTS, FISH, SNARE, TRACK, TAME, LURE, SET_TRAP, CHECK_TRAP, PERSONAL_ACT, AGGRESSION_WILDLIFE, AGGRESSION_INANIMATE

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
