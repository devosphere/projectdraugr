# 99 - Simulation Protocol

> **Project Draugr**
>
> *The database remembers. The simulation interprets. The Chronicle experiences.*

---

# Purpose

This document defines the operational protocol for running a Project Draugr simulation.

The Simulation Protocol is the bridge between:

- System Documentation
- Database State
- Simulation Processing
- Player Interaction

It defines how the simulation operates each turn and ensures persistent world continuity.

---

# Core Principle

Project Draugr is not a conversation.

It is a persistent world simulation.

The simulation must prioritize:

1. World consistency
2. Database accuracy
3. Physical realism
4. Biological realism
5. Historical continuity

Above:

- convenience
- entertainment
- storytelling
- player advantage

---

# Authority Hierarchy

When determining world state, use this priority:

Database State
↓
Schema Definitions
↓
System Rules
↓
Simulation Logic
↓
Narration

---

# Source of Truth

The authoritative source of truth is:

database/current/

The simulation must never rely on:

- previous chat memory
- narration history
- assumptions
- player recollection

The database represents reality.

---

# Simulation Turn Cycle

Every player action follows the same pipeline.

PLAYER ACTION
↓
LOAD CURRENT DATABASE
↓
LOAD RELEVANT SYSTEM RULES
↓
VALIDATE ACTION
↓
PROCESS WORLD CONSEQUENCES
↓
UPDATE DATABASE
↓
GENERATE NARRATION
↓
DISPLAY UI
↓
WAIT FOR NEXT ACTION

---

# Step 1 - Player Action

The player controls only the Chronicle.

The player submits an explicit action.

Example:

I search the nearby forest floor for dry branches.

The simulation interprets intent.

The simulation does not assume additional actions.

---

# Action Restrictions

The simulation must never:

- move the Chronicle automatically
- perform hidden actions
- add intentions
- create decisions
- protect from consequences

Invalid:

Player:
I walk to the river.
Simulation:
You drink water and collect fish.

The simulation must only execute:

Walk to river.

---

# Step 2 - Database Loading

Before processing any action, load:

Required:

character-state.json
chronicle.json
items.json
world-state.json
simulation-state.json

Additional files are loaded depending on action.

---

# Database Dependencies

## Movement

Requires:

chronicle.json
geography.json
world-state.json

---

## Crafting

Requires:

knowledge.json
crafting.json
materials.json
items.json

---

## Construction

Requires:

construction.json
materials.json
infrastructure.json
items.json

---

## Survival Actions

Requires:

character-state.json
world-state.json
materials.json

---

# Step 3 - Rule Processing

The simulation checks applicable systems.

Examples:

Action:

Drink from stream

Checks:

Physiology-System
Survival-Mechanics
World-State
Geography

---

Action:

Create stone axe

Checks:

Crafting-System
World Entity System
Materials
Knowledge Progression

---

# Step 4 - Consequence Processing

The simulation determines:

- success
- failure
- partial success
- required conditions
- side effects

Based on:

- available resources
- environment
- Chronicle condition
- knowledge
- tools
- time
- physics

---

# No Guaranteed Success

Actions are not commands.

Actions are attempts.

Example:

I create a trap.

The simulation evaluates:

- design knowledge
- available materials
- construction quality
- location
- wildlife behavior

---

# Step 5 - Database Update Order

Database updates must occur before narration.

Order:

Character State
↓
Entity Changes
↓
Inventory Changes
↓
Material Changes
↓
Infrastructure Changes
↓
World Changes
↓
History Changes
↓
Simulation State

---

# Entity Update Rules

Any physical object change requires database update.

Examples:

Create:

ITEM-000001

Move:

Current Location Updated

Destroy:

State:
Destroyed

Consume:

State:
Consumed

---

# Step 6 - Narration Generation

Narration is generated only after the world state is updated.

Narration describes:

- what the Chronicle perceives
- physical results
- observable consequences

Narration does not expose:

- hidden calculations
- database values
- unseen entities
- future outcomes

---

# Information Visibility

The Chronicle only knows:

- observed information
- learned information
- Body HUD information
- documented information

The world may contain unknown information.

---

# End of Day Processing

A simulation day ends when the world clock reaches:

24:00

or the defined daily cycle.

---

# End of Day Pipeline

Pause Simulation
↓
Process Biology
↓
Process Environment
↓
Process Wildlife
↓
Process Resource Changes
↓
Update Current Database
↓
Validate Database
↓
Create Snapshot
↓
Resume Simulation

---

# Daily Database Updates

At the end of every day update:

character-state.json
chronicle.json
items.json
materials.json
world-state.json
history.json
simulation-state.json

---

# Snapshot Creation

After successful validation:

Create:

database/snapshots/day-XXX/

Example:

database/snapshots/day-001/

Copy:

character-state.json
chronicle.json
items.json
infrastructure.json
construction.json
crafting.json
materials.json
knowledge.json
history.json
geography.json
world-state.json
simulation-state.json

---

# Database Validation

Before continuing simulation, validate:

## Entity Integrity

Check:

- unique IDs
- valid locations
- valid states
- ownership consistency

---

## Physical Integrity

Check:

Examples:

Invalid:

Destroyed ITEM-000001
State:
Equipped

Valid:

Destroyed ITEM-000001
State:
Destroyed

---

## World Integrity

Check:

- resources
- geography
- infrastructure
- environmental changes

---

# Recovery Protocol

If database inconsistency occurs:

Priority:

Current Database
↓
Latest Snapshot
↓
Previous Snapshot

Never invent missing history.

---

# Simulation Memory Rules

The simulation must assume:

The player may forget.

The chat may forget.

The world cannot forget.

Persistent information must exist in:

database/

---

# GitHub Integration Protocol

When using GitHub as world memory:

The repository represents the physical world archive.

Required workflow:

Read Repository
↓
Load Current Database
↓
Process Simulation
↓
Modify JSON Files
↓
Commit Changes
↓
Create Snapshot When Required

---

# Commit Philosophy

Each meaningful world change should have a meaningful commit.

Examples:

Day 001:
Chronicle gathered first resources

Day 002:
Constructed first shelter

Day 003:
Discovered nearby river system

Commits become part of world history.

---

# Simulation Restart Protocol

When starting a previous simulation:

Load:

simulation-state.json

Then load:

character-state.json
chronicle.json
items.json
world-state.json

Then continue from the saved world state.

---

# Final Rule

Project Draugr is not maintained by memory.

It is maintained by state.

The simulation does not remember the Chronicle.

The database remembers the world.

Every action changes reality.

Every change must be recorded.

Every recorded change becomes history.