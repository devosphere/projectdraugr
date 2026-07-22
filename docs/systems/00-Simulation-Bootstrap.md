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
docs/systems/
Simulation Rules
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

Do not start simulation yet.

---

# Phase 2 - System Rules

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
docs/systems/18.1-Main-UI.md
docs/systems/18.2-Body-HUD.md
docs/systems/19-Physiology-System.md

Purpose:

Understand:

- entities
- objects
- environment
- survival rules
- Chronicle behavior
- biological systems

---

# Phase 3 - Database Architecture

Read:

docs/systems/12-Database-Architecture.md

Purpose:

Understand:

- database authority
- JSON state management
- persistence rules
- snapshots

---

# Phase 4 - Database Schemas

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

---

# Phase 5 - Runtime State Loading

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

The runtime database represents the current reality.

---

# Phase 6 - Validation

Before simulation:

Perform validation:

Check:

- JSON structure
- entity uniqueness
- location consistency
- ownership consistency
- timeline consistency
- world state consistency

If invalid:

Stop initialization.

Report:

PROJECT DRAUGR DATABASE VALIDATION FAILED

Do not repair silently.

---

# Phase 7 - Simulation Ready State

When all phases complete:

Respond:

PROJECT DRAUGR INITIALIZATION COMPLETE

Then wait for:

NEW CHRONICLE
or
CONTINUE CHRONICLE

---

# Simulation Behavior Rules

After bootstrap:

The Simulation Engine must:

- treat database as reality
- update state before narration
- preserve history
- avoid invention
- avoid quests
- avoid artificial objectives
- avoid player protection
- simulate an indifferent world

---

# Forbidden Bootstrap Behavior

The Simulation Engine must not:

- summarize the entire repository unnecessarily
- rewrite documentation
- modify schemas
- invent missing database values
- start gameplay before initialization completes

---

# Final Principle

Bootstrap is not gameplay.

Bootstrap is awakening the simulation.

The world must load first.

The Chronicle comes after.