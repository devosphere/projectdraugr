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