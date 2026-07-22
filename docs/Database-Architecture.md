# Database-Architecture.md

# Project Draugr Database Architecture

> **The world is not stored in memory.  
> The world is stored in records.**

---

# Purpose

This document defines the persistent database architecture of Project Draugr.

The database exists to maintain the continuity of the simulation across:

- chat sessions
- simulation days
- world changes
- Chronicle actions
- future development into a standalone game engine

The database is the permanent memory of the world.

---

# Core Principle

Project Draugr follows this rule:

> **The simulation may forget. The database must not.**

The player may forget:

- where an item was stored
- what structures were built
- what resources were consumed
- what discoveries were made

The simulation engine may restart.

The world database remains the source of truth.

---

# Database Role

The database stores:

- current world state
- Chronicle condition
- physical entities
- resources
- infrastructure
- crafting progress
- construction progress
- discovered knowledge
- historical events

The database does not store:

- narration
- temporary descriptions
- player emotions
- storytelling elements

---

# Database Location

Project Draugr stores persistent simulation data inside:

```
/database/
```

Example:

```
Project-Draugr/

├── docs/

│   ├── 18.1-Main-UI.md
│   ├── 18.2-Body-HUD.md
│   ├── 19-Physiology-System.md
│   └── ...

└── database/

    ├── 00-Character-State.md
    ├── 01-Chronicle.md
    ├── 02-Items.md
    ├── 03-Infrastructure.md
    ├── 04-Construction.md
    ├── 05-Crafting.md
    ├── 06-Materials.md
    ├── 07-Knowledge.md
    ├── 08-History.md
    ├── 09-Geography.md
    ├── 10-World-State.md
    ├── 99-Simulation-State.md
    │
    └── snapshots/

        ├── Day-001.md
        ├── Day-002.md
        └── Day-003.md
```

---

# Documentation vs Database

Project Draugr separates:

```
Rules
```

and

```
Reality
```

---

# Documentation Layer

Location:

```
/docs/
```

Contains:

- simulation rules
- system behavior
- UI definitions
- mechanics
- philosophies

Examples:

```
19-Physiology-System.md

14-Crafting.md

15-Infrastructure.md
```

These files explain how the world works.

They rarely change.

---

# Database Layer

Location:

```
/database/
```

Contains:

- current reality
- existing entities
- active conditions
- world progress

Examples:

```
00-Character-State.md

02-Items.md

06-Materials.md
```

These files constantly change.

---

# Simulation Processing Order

Every player action follows this sequence:

```
Player Action

↓

Read Database State

↓

Read Applicable Rules

↓

Calculate World Response

↓

Modify Database

↓

Generate Narration
```

---

# Important Rule

Narration is generated last.

Never:

```
Narration

↓

Database Update
```

Correct:

```
Database Update

↓

Narration
```

The world exists first.

The description comes afterward.

---

# Database Files

---

# 00-Character-State.md

## Authority

Current biological and physical state of the Chronicle.

Contains:

- Health
- Condition
- Hunger
- Thirst
- Energy
- Temperature
- Wetness
- Bladder
- Bowel
- Hygiene
- Injuries
- Status effects
- Temporary conditions

Used by:

```
18.2-Body-HUD.md
```

The Body HUD reads from this file.

---

# 01-Chronicle.md

## Authority

Chronicle identity and long-term progression.

Contains:

- Name
- Origin
- Survival duration
- Achievements
- Milestones
- Discoveries
- Major accomplishments

Does not contain:

- inventory
- current health
- equipment
- temporary conditions

---

# 02-Items.md

## Authority

World Entity Registry.

Contains every physical object.

Examples:

- weapons
- tools
- clothing
- containers
- documents
- furniture
- crafted objects
- consumables

Every object receives:

```
ITEM-ID
```

Example:

```
ITEM-000001
```

No physical object exists outside this registry.

---

# 03-Infrastructure.md

## Authority

Permanent structures attached to the world.

Examples:

- houses
- workshops
- storage facilities
- roads
- bridges
- wells
- manufacturing areas

Every infrastructure receives:

```
INFRA-ID
```

Example:

```
INFRA-000001
```

---

# 04-Construction.md

## Authority

Construction projects.

Contains:

- planned structures
- active construction
- required materials
- progress
- completed projects
- engineering notes

Construction progress eventually modifies:

```
03-Infrastructure.md
```

---

# 05-Crafting.md

## Authority

Production knowledge and crafting activity.

Contains:

- recipes
- manufacturing methods
- refinement techniques
- active crafting projects

Crafting knowledge is not a physical item.

Finished products are stored in:

```
02-Items.md
```

---

# 06-Materials.md

## Authority

Available resources.

Contains:

Raw materials:

- logs
- stone
- clay
- fibers
- plants
- ores

Processed materials:

- charcoal
- cordage
- prepared timber
- refined stone

Materials become entities only after transformation into physical objects.

---

# 07-Knowledge.md

## Authority

Chronicle knowledge.

Contains:

- learned skills
- survival techniques
- engineering knowledge
- observations
- procedures
- discoveries

Knowledge is separate from documentation.

Example:

The Chronicle learns:

```
Advanced Bow Construction
```

Knowledge updates.

No physical object exists.

---

# 08-History.md

## Authority

Historical record.

Contains:

- important events
- discoveries
- deaths
- settlements
- disasters
- major changes

History is permanent.

---

# 09-Geography.md

## Authority

Discovered world locations.

Contains:

- rivers
- forests
- mountains
- camps
- settlements
- resource locations

Unknown areas remain unknown.

---

# 10-World-State.md

## Authority

Permanent world changes.

Contains:

Examples:

- harvested forests
- depleted resources
- altered terrain
- destroyed structures
- abandoned settlements
- environmental changes

The world remembers.

---

# 99-Simulation-State.md

## Authority

Current simulation checkpoint.

This file allows continuation between sessions.

Contains:

- Current day
- Current time
- Current season
- Current weather
- Current location
- Active events
- Last processed action
- Pending world processes

Example:

```
Current Day:

Day 14


Current Time:

18:45


Current Location:

Main Camp


Last Action:

Built stone furnace
```

---

# Database Update Rules

Any action involving the world must update the appropriate database.

---

# Item Actions

Examples:

- craft
- pick up
- drop
- equip
- consume
- repair
- modify

Update:

```
02-Items.md
```

---

# Body Actions

Examples:

- eating
- drinking
- sleeping
- injuries
- exposure

Update:

```
00-Character-State.md
```

---

# Construction Actions

Examples:

- building
- repairing
- destroying

Update:

```
03-Infrastructure.md

04-Construction.md
```

---

# Resource Actions

Examples:

- gathering
- harvesting
- processing

Update:

```
06-Materials.md

10-World-State.md
```

---

# Knowledge Actions

Examples:

- learning
- discovering techniques
- mastering skills

Update:

```
07-Knowledge.md
```

---

# End Of Day Processing

At the end of every simulated day:

The engine creates a snapshot.

Example:

```
database/snapshots/Day-015.md
```

The snapshot records:

- Chronicle condition
- inventory summary
- infrastructure status
- material changes
- discoveries
- historical events
- world changes

---

# Snapshot Purpose

Snapshots provide:

- rollback capability
- debugging
- historical analysis
- multiplayer synchronization
- future AI training data

---

# Database Integrity Rules

The following rules are mandatory:

## Rule 1

No physical object exists without an ID.

---

## Rule 2

No world change exists without a record.

---

## Rule 3

No narration may contradict the database.

---

## Rule 4

Destroyed objects retain historical existence.

Example:

```
ITEM-000001

Stone Axe

Destroyed
```

The record remains.

---

## Rule 5

IDs are never reused.

Destroyed:

```
ITEM-000001
```

Next item:

```
ITEM-000002
```

---

# Database Philosophy

Project Draugr is not a story generator.

It is a persistent world simulation.

The database represents:

- what exists
- what happened
- what changed
- what remains

The Chronicle experiences the world.

The database remembers it.

---

# Final Principle

> **Narration tells the player what happened.  
> The database remembers that it happened.**

The world exists independently.

The records preserve its existence.

The simulation continues.