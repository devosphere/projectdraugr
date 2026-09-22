# 13 – Construction

> **Project Draugr**
>
> *Construction is the deliberate transformation of the world into civilization.*

---

# Purpose

This document defines the construction system of Project Draugr.

Construction allows Chronicles to permanently modify the world by creating infrastructure.

Unlike crafting, which creates portable objects, construction creates structures that become part of the world itself.

Construction is persistent.

It survives:

- player logout
- Chronicle death
- multiplayer sessions
- passage of time

until physically destroyed.

---

# Design Philosophy

Construction follows one principle:

> **Every structure should have a believable purpose.**

Construction is never instantaneous.

Every project requires:

- planning
- materials
- labor
- tools
- workstation (when applicable)
- time

The world remembers every completed structure.

---

# Construction Lifecycle

Every construction project progresses through distinct stages.

```text
Idea
        │
        ▼
Planning
        │
        ▼
Material Gathering
        │
        ▼
Site Preparation
        │
        ▼
Construction
        │
        ▼
Completed Infrastructure
```

Incomplete projects remain in the world.

They do not disappear simply because work stops.

---

# Construction Requirements

Every construction project may require one or more of the following:

## Materials

Examples:

- Timber
- Stone
- Clay
- Rope
- Fiber
- Leather
- Nails (future)

---

## Tools

Examples:

- Stone Axe
- Hammer
- Chisel
- Saw
- Shovel
- Pickaxe

---

## Knowledge

Construction requires appropriate knowledge.

Example:

A Chronicle without roofing knowledge cannot properly construct a weatherproof cabin.

Knowledge determines:

- available designs
- construction efficiency
- structural quality
- failure probability

---

## Workstations

Certain components require workstations before construction can begin.

Examples:

Workbench

Produces:

- beams
- planks
- handles

Stone Workbench

Produces:

- cut stone
- stone blocks

Weaving Workbench

Produces:

- cloth
- rope
- woven components

Construction itself happens at the construction site.

Workstations prepare the required components.

---

# Construction Sites

Construction occurs at a physical location.

Every site exists within the world.

Example:

```text
Forest Clearing

↓

Cabin Construction Site
```

The site records:

- progress
- stored materials
- assigned workers (future)
- unfinished components

---

# Construction Progress

Progress is continuous.

Projects may be interrupted.

Examples:

- lack of materials
- exhaustion
- weather
- attacks
- player decision

Construction resumes from the previous state.

No progress is lost unless the structure is damaged.

---

# Construction Categories

## Shelter

Examples:

- Lean-to
- Tent
- Cabin
- Longhouse
- Stone House

---

## Infrastructure

Examples:

- Bridge
- Road
- Stairway
- Dock
- Well

---

## Production

Examples:

- Workshop
- Forge
- Kiln
- Loom House
- Smokehouse

---

## Storage

Examples:

- Warehouse
- Storage Rack
- Granary
- Root Cellar

---

## Agriculture

Examples:

- Farm Plot
- Irrigation Channel
- Animal Pen
- Compost Area

### A shelter holds the bodies that fit in it (#108, V369)

`construction_kind.shelters_stock` is read in five places — whether stock settle to breed, whether a birth loses young, whether the young survive a frost, whether a grown beast burns feed to keep warm, and whether sickness runs through a herd. It was species-blind, so **a hen house on the ground was what let a keeper's aurochs breed**, and a brooder shelter — a warmed box for day-old chicks — counted as a birthing house for a water buffalo, with perinatal loss set to zero.

Every structure that holds a body now declares `shelters_up_to_size`, the largest `wildlife_species.size_tier` it can actually hold, and it holds that body and every smaller one:

| ceiling | structures |
|---|---|
| `SMALL` | brooder shelter, poultry coop |
| `LARGE` | goat fold, farrowing shelter, foaling stall, pig sty |
| `HUGE` | cattle byre, timber barn, animal pen, bull isolation yard, sick animal shelter, hitching post, tether line, **ox shed** |

A **ceiling, not a list**, because that is what is physically true: a byre roofs a hen perfectly well, and a coop cannot roof an ox however many hens it was built for. `body_size_rank()` orders the tiers and ranks an unrecognised one above every shelter, so a species nobody sized is refused shelter rather than quietly given the best of it. The `sized_for_the_stock_it_holds` constraint requires the answer of any structure flagged `shelters_stock`, `shelters_birth` or `isolates_sick`.

Honest ceilings left exactly one hole — nothing sheltered the **birth** of a HUGE animal, and aurochs and water buffalo are the world's two HUGE breeders — so the **ox shed** goes in with the same migration: wide enough to lead a yoked pair through, bedded to calve in, with `shelters_gear` for the yokes along the back wall. It is the only structure added, because a catalogue entry that changes nothing is the defect and not the fix.

---

## Defensive

Examples:

- Fence
- Palisade
- Watch Tower
- Gate
- Wall

---

## Utility

Examples:

- Fire Pit
- Cooking Area
- Water Collection Basin
- Drying Rack

---

# Construction Quality

Construction quality depends upon:

- Chronicle knowledge
- material quality
- tool quality
- environmental conditions
- workmanship

Possible results:

- Poor
- Common
- Good
- Excellent
- Masterwork

Higher-quality construction generally provides:

- greater durability
- improved efficiency
- reduced maintenance

---

# Maintenance

Infrastructure naturally deteriorates.

Factors include:

- weather
- fire
- flooding
- neglect
- heavy use
- age

Without maintenance, structures gradually lose integrity.

Eventually they may collapse.

---

# Destruction

Structures may be destroyed through:

- fire
- storms
- combat
- demolition
- natural collapse

Destroyed structures may yield recoverable materials depending on their remaining integrity.

---

# World Persistence

Every completed structure becomes part of the world.

Future Chronicles may discover:

- abandoned settlements
- ruined buildings
- forgotten roads
- collapsed bridges
- ancient workshops

Construction permanently changes history.

---

# Relationship with Other Systems

Construction depends upon:

- Materials
- Crafting
- Infrastructure
- Knowledge Progression
- World Entity System
- Item System

Construction produces Infrastructure.

Infrastructure is documented separately in:

**15 – Infrastructure**

---

# Future Expansion

Future versions may introduce:

- structural engineering
- load-bearing simulation
- construction teams
- NPC labor
- blueprints
- modular buildings
- architectural styles
- automated production buildings
- siege damage
- seasonal construction penalties

---

# Final Principle

Construction is one of the primary ways a Chronicle leaves a permanent mark upon the world.

Items may be lost.

Chronicles may die.

Civilizations endure through the structures they leave behind.