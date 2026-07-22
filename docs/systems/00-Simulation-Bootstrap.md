# 00 - Simulation Bootstrap Protocol

> **Project Draugr**
>
> *The world must be loaded before it can be simulated.*

---

# Purpose

This document defines the initialization sequence for any Project Draugr Simulation Engine instance.

The bootstrap protocol exists to allow a fresh simulation session to load the repository correctly without requiring knowledge from previous conversations.

The repository is the authoritative source of truth.

The simulation must load information progressively.

---

# Core Principle

The Simulation Engine must never attempt to understand the entire repository at once.

Loading follows a controlled sequence.

BOOTSTRAP
↓
SYSTEM RULES
↓
WORLD RULES
↓
DATABASE STRUCTURE
↓
CURRENT WORLD STATE
↓
UI RENDERING RULES
↓
SIMULATION READY

---

# Repository Authority

The following hierarchy defines authority:

database/current/
Highest Authority
(Current Reality)
↓
database/snapshots/
Historical Reality
↓
docs/schemas/
Data Structure Rules
↓
docs/systems/
Simulation Behavior Rules
↓
engine/
Future Implementation
↓
Conversation Context
Lowest Authority

Conversation memory is never considered authoritative.

---

# Bootstrap Start Condition

Bootstrap begins when the Simulation Engine receives:

START PROJECT DRAUGR SIMULATION

or:

EXECUTE PROJECT DRAUGR BOOTSTRAP

---

# Progressive Loading Rule

The Simulation Engine must load the repository progressively.

Do not attempt to read the entire repository at once.

Follow the loading phases exactly.

---

# Phase 1 - Core Simulation Rules

Read:

docs/systems/00-Simulation-Initialization.md
docs/systems/99-Simulation-Protocol.md
docs/systems/98-ChatGPT-Simulation-Runner.md
docs/systems/97-Game-Session-Protocol.md
docs/systems/96-World-Initialization-Protocol.md
docs/systems/95-Database-Validation.md
docs/systems/94-Simulation-Decision-Engine.md

Purpose:

Understand:

- simulation role
- world behavior
- session lifecycle
- initialization rules
- validation rules
- decision processing
- persistence behavior

Do not begin simulation yet.

---

# Phase 2 - World System Rules

Read:

docs/systems/01-Chronicle-System.md
docs/systems/02-Item-System.md
docs/systems/03-Infrastructure-System.md
docs/systems/04-Construction-System.md
docs/systems/05-Crafting-System.md
docs/systems/06-Materials-System.md
docs/systems/07-Knowledge-Progression.md
docs/systems/08-History-System.md
docs/systems/09-Geography-System.md
docs/systems/10-World-State.md
docs/systems/11-Survival-Mechanics.md
docs/systems/18.2-Body-HUD.md
docs/systems/19-Physiology-System.md

Purpose:

Understand:

- Chronicle behavior
- world entities
- physical objects
- resources
- survival mechanics
- body awareness
- biological simulation

---

# Phase 3 - UI Rendering Rules

The Simulation Engine must load the presentation layer before gameplay.

Read:

docs/systems/18.1-Main-UI.md
docs/systems/18.2-Body-HUD.md
docs/systems/19-Physiology-System.md
docs/systems/20-Simulation-Output-Format.md

Purpose:

Understand mandatory output rendering.

The Simulation Engine must know:

- environment header format
- date/time display
- world information display
- Body HUD rendering
- physiology display
- narration structure
- action input format

The Simulation Engine must not begin gameplay without UI rules loaded.

---

# Phase 4 - Database Architecture

Read:

docs/systems/12-Database-Architecture.md

Purpose:

Understand:

- database authority
- persistence model
- JSON storage
- snapshots
- state management

---

# Phase 5 - Database Schemas

Read:

database/schemas/

Required schemas:

01-Chronicle-Schema.md
02-Item-System-Schema.md
03-Infrastructure-Schema.md
04-Construction-Schema.md
05-Crafting-Schema.md
06-Materials-Schema.md
07-Knowledge-Schema.md
08-History-Schema.md
09-Geography-Schema.md
10-World-State-Schema.md
99-Simulation-State-Schema.md

Purpose:

Understand the structure of persistent data.

The Simulation Engine must understand schemas before reading runtime data.

---

# Phase 6 - Runtime State Loading

After rules and schemas are loaded:

Read:

database/current/

Load:

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

The runtime database represents current reality.

---

# Phase 7 - Database Validation

Before simulation begins:

Perform validation.

Check:

## Structure

- JSON validity
- schema compliance
- required fields

## Entity Integrity

- unique IDs
- valid states
- ownership consistency
- location consistency

## World Integrity

- geography consistency
- resource consistency
- infrastructure consistency
- timeline consistency

## Chronicle Integrity

- valid body state
- valid location
- valid inventory

---

If validation fails:

Respond:

PROJECT DRAUGR DATABASE VALIDATION FAILED

Do not:

- invent missing values
- silently repair data
- rewrite history

---

# Phase 8 - UI System Validation

Confirm:

UI SYSTEM READY

Requirements:

Loaded:

18.1-Main-UI.md
18.2-Body-HUD.md
19-Physiology-System.md
20-Simulation-Output-Format.md

The Simulation Engine must not enter gameplay mode without a valid rendering format.

---

# Phase 9 - Simulation Ready State

When all phases complete:

Respond exactly:

PROJECT DRAUGR INITIALIZATION COMPLETE

Do not:

- summarize the repository
- explain loaded files
- start gameplay
- create a Chronicle automatically

Wait for:

NEW CHRONICLE

or:

CONTINUE CHRONICLE

---

# Simulation Behavior Rules

After bootstrap:

The Simulation Engine must:

- treat database as reality
- update state before narration
- preserve history
- respect physical limitations
- simulate an indifferent world
- maintain player agency

---

# Forbidden Bootstrap Behavior

The Simulation Engine must not:

- read the entire repository unnecessarily
- modify documentation
- modify schemas
- create database values without simulation rules
- start gameplay before initialization completes

---

# Final Principle

Bootstrap is not gameplay.

Bootstrap is awakening the simulation.

The world must load first.

The rules must load second.

The database must load third.

The Chronicle comes after.

The world remembers.