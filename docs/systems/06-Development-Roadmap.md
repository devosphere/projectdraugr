# 06 – Development Roadmap

> **Project Draugr**
>
> *Build the world one layer at a time.*

---

# Purpose

This document defines the long-term development strategy of Project Draugr.

The objective is to progressively evolve the project without compromising its core philosophy.

Every phase must produce a playable and complete experience.

Future phases expand the world rather than replace it.

---

# Development Philosophy

Project Draugr is designed as a long-term project.

The vision remains constant.

Implementation evolves gradually.

Each development phase builds upon the previous one.

Nothing is discarded.

Everything becomes part of the final world.

---

# Guiding Principles

Development follows these priorities:

1. Persistent World
2. Living Simulation
3. Meaningful Gameplay
4. Historical Continuity
5. Technical Scalability
6. Intelligent Existence

Artificial Intelligence is intentionally placed last.

The world must first become alive before it becomes intelligent.

---

# Phase 0 — Blueprint

## Objective

Define the complete vision before implementation.

### Deliverables

- Project philosophy
- World design
- Gameplay systems
- Technical architecture
- Documentation
- MVP scope

### Status

Complete — all design documents, schemas, and vision documents authored. Active development has entered Phase 1.

---

# Phase 1 — Minimum Viable World (MVP)

## Status

**In Progress** — as of 2026-07-31

Backend: Java 21 / Spring Boot / PostgreSQL. Migrations V1–V38 applied. All core systems built (physiology, fire, wildlife, items, construction, literature, navigation, cartography, sleep, capability adaptation, three-layer success model, perception frame, domain registry). Foundation tasks F1–F8 all complete. Frontend: React/Vite/TypeScript playable UI.

### Completed in Phase 1 so far

- V1–V38 migrations applied and clean.
- Three-layer success model (physical gate → text specificity → capability familiarity) wired for LIGHT_FIRE and CONFRONT_WILDLIFE.
- PerceptionFrame returned with every action (F1). Tick-progression deltas in frame (F2). ATTENTION-scaled world detail (F3).
- All seven death vectors wired (F4): starvation, dehydration, blood loss, fatal trauma, illness, hypothermia, hyperthermia.
- PersistentStateAuditor: 17 invariant checks (F5). AuditSentinel: launch gate + heartbeat (F6).
- domain_registry (V38) + DomainRegistryService + GET /api/domains (F7). Monotonic schema decision (F8 / DR-0013).

### Ecosystem Expansion (V39–V46) — ✅ COMPLETE

Per DR-0015, the deterministic ecosystem had to reach maximum coverage before AI narration was wired. All eight migrations are applied and validated from scratch against Postgres 16:

| Migration | Scope | Status |
|-----------|-------|--------|
| V39 | Flora system — 33 species; flora_definition + flora_drop + chunk_flora; GATHER_PLANT, FELL_TREE | ✅ |
| V40 | Insect ecosystem — 11 colonies, products, hazards; RAID_HIVE, COLLECT_INSECTS | ✅ |
| V41 | wildlife_species registry (Mammalia/Reptilia/Amphibia) + 6 FSM cascade rules | ✅ |
| V42 | Aves + Pisces + wildlife_drop per-species butchery + aquatic_catch; FISH, SNARE | ✅ |
| V43 | Monster system — monster_profile, 9 species as MONSTRUM, 6 special mechanics | ✅ |
| V44 | chronicle_wildlife_event + wildlife_sign; passive encounters, TRACK | ✅ |
| V45 | wildlife_bond + tamed_yield/production; TAME | ✅ |
| V46 | bait_profile + placed_lure + placed_trap; LURE, SET_TRAP, CHECK_TRAP | ✅ |

**Totals:** 35 domains · 66 wildlife species · 33 flora · 11 insect colonies · 9 monsters · 122 items · 97 wildlife drops · 48 flora drops · 124 signs · 12 baits · 61 DB-free unit tests green.

**ActionInputClassifier** — ✅ pre-pass filter (DR-0019). Personal acts and aggression carry real physiological and ecological consequence; gibberish and the physically impossible are intercepted before the tick at zero cost.

**NarrationEngine** — ✅ deterministic witness prose for 30 intent × outcome scenes plus a safe generic fallback, varied by biome, time of day, weather, species, and wound severity. Correct even with the API unreachable.

**NarrationRouter** — ✅ pure Java, no migration; routes AI_REFINE vs DETERMINISTIC per DR-0017. 27 intents never call; significance (death, monsters, taming thresholds, serious wounds, writing, deliberate observation) earns the call. Cost shape is enforced by test.

### Remaining before Phase 1 complete

**Task #21 — AI narration** — Claude API refinement layer, one to two sentences of atmospheric flavour on top of NarrationEngine prose, per DR-0016. Now fully unblocked: the router decides when to call and the engine supplies the `backendNarration` the prompt builds on.

**Ecology tick pass** — one periodic tick advancing insect colonies, traps, and tamed production. Seven deferred behaviours share this single dependency and are grouped in the sprint backlog rather than scattered (mosquito illness, bee pollination, locust flora destruction, autonomous trap catches, livestock production, poison ingestion, scavenger-bird ambient mechanics).

## Objective

Deliver a complete playable persistent world.

The MVP proves the core philosophy.

It is not a prototype.

It is the first complete version of Project Draugr.

---

## World

One persistent region.

Contains:

- forests
- rivers
- lakes
- mountains
- caves
- ruins
- wildlife
- monsters
- resources

No existing human civilization.

---

## Players

Players arrive as transported humans.

One Chronicle.

Permanent death.

Real-time continuity.

Knowledge-based progression.

---

## Simulation

Simulation includes:

- weather
- ecology
- wildlife
- monster territories
- resource regeneration
- construction persistence
- historical persistence

---

## Gameplay

Players can:

- survive
- gather
- craft
- build
- hunt
- fish
- farm
- explore
- construct settlements

---

## Intelligence

No LLMs.

No AI-driven NPCs.

Wildlife and monsters use deterministic programmed behavior.

The world itself remains the primary experience.

---

# Phase 2 — World Expansion

## Objective

Expand the simulation.

Possible additions include:

- additional regions
- new ecosystems
- larger world map
- deeper ecology
- more wildlife
- additional monster species
- improved weather simulation
- expanded construction systems

The simulation becomes deeper rather than fundamentally different.

---

# Phase 3 — Civilization

## Objective

Allow civilization to naturally emerge.

Possible additions include:

- larger settlements
- roads
- player governance
- economies
- diplomacy
- trade
- transportation
- advanced construction

Civilization remains entirely player-created.

---

# Phase 4 — Living World

## Objective

Increase systemic complexity.

Possible additions include:

- advanced ecological interactions
- regional population dynamics
- environmental succession
- natural disasters
- advanced resource simulation
- civilization decline
- abandoned settlements becoming ruins

The world becomes increasingly autonomous.

---

# Phase 5 — Intelligent Existence

## Objective

Introduce selected intelligent autonomous existences.

Only important entities become AI-assisted.

Examples include:

- unique legendary creatures
- mysterious entities
- ancient guardians
- future world-scale intelligences

Artificial intelligence augments the simulation.

It never replaces it.

---

# Phase 6 — Continuous Evolution

Project Draugr has no final version.

Future development continues improving:

- simulation depth
- ecological realism
- civilization complexity
- player interaction
- world persistence
- intelligent existence

The world continuously evolves alongside technology.

---

# Funding Strategy

Project Draugr is intentionally designed so that every phase is independently valuable.

Funding should accelerate development.

It should never determine the project's philosophy.

The project must remain buildable regardless of budget.

---

# Scope Control

Whenever new ideas arise, they should first answer:

## Does this belong in the current phase?

If yes:

Design it.

If no:

Document it.

Move it to a future phase.

Protecting focus is more valuable than adding features.

---

# Success Criteria

Every completed phase should satisfy the following.

## Playable

The game can be played from beginning to end.

---

## Persistent

The world permanently remembers meaningful actions.

---

## Expandable

Future phases build upon existing systems.

---

## Stable

The architecture supports long-term growth.

---

## Faithful

The implementation remains true to the Vision Bible.

---

# Final Principle

Project Draugr should never chase technological trends.

Every phase should move one step closer toward the original vision:

A world that exists independently of its players.

A world where every Chronicle leaves a permanent mark.

The world grows.

The technology evolves.

The vision remains unchanged.