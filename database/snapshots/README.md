# Project Draugr Snapshot Archive

This directory contains historical frozen snapshots of the simulation state.

Snapshots represent the complete state of the world at the end of each simulated day.

---

# Purpose

The snapshot system preserves world continuity.

The `current/` directory represents the active simulation state.

The `snapshots/` directory represents historical states.

The world may change.

The snapshots never change.

---

# Snapshot Structure

Each completed simulation day creates a new snapshot folder.

Example:

snapshots/
├── day-001/
│
├── day-002/
│
└── day-003/

Each snapshot contains a complete copy of the database state:

day-001/
├── character-state.json
├── chronicle.json
├── items.json
├── infrastructure.json
├── construction.json
├── crafting.json
├── materials.json
├── knowledge.json
├── history.json
├── geography.json
├── world-state.json
└── simulation-state.json

---

# Snapshot Rules

Snapshots are:

- immutable
- historical
- read-only
- never overwritten
- never deleted automatically

Once a snapshot is created, it becomes part of the permanent world history.

---

# Snapshot Creation Process

Snapshots are created automatically at the end of every simulated day.

Process:

End of Simulation Day
↓
Validate Current Database State
↓
Freeze Current State
↓
Create Snapshot Folder
↓
Copy All Database Files
↓
Record Snapshot Metadata
↓
Continue Simulation

Example:

Day 001 Complete
↓
Create:
database/snapshots/day-001/

---

# Current vs Snapshot

## Current State

Location:

database/current/

Purpose:

The active simulation database.

Contains the present state of the world.

Changes continuously.

Examples:

- Chronicle location changes
- inventory changes
- resource depletion
- infrastructure updates
- biological changes
- weather progression

---

## Snapshot State

Location:

database/snapshots/

Purpose:

Historical archive of previous world states.

Never modified after creation.

Examples:

day-001

represents exactly how the world existed after the first simulation day.

---

# Snapshot Integrity

Snapshots must represent a complete and consistent world state.

A snapshot must include:

- character state
- inventory state
- item registry
- infrastructure
- construction progress
- crafting knowledge
- materials
- geography
- world changes
- history
- simulation metadata

A partial snapshot is invalid.

---

# Recovery System

If the active simulation state becomes corrupted:

A previous snapshot may be used to restore the world.

Example:

database/snapshots/day-050/
↓
Restore
↓
database/current/

Restoration should preserve:

- Entity IDs
- History records
- World changes
- Construction records
- Knowledge progression

---

# Debugging Purpose

Snapshots allow analysis of world progression.

Examples:

Compare:

day-001

against:

day-100

to determine:

- resource consumption
- settlement growth
- infrastructure development
- ecosystem changes
- Chronicle progression

---

# Multiplayer Synchronization

Future multiplayer systems may use snapshots for:

- world synchronization
- player state recovery
- conflict resolution
- server restoration

---

# AI Memory Persistence

Snapshots provide historical references for AI systems.

The AI may use snapshots to understand:

- previous world conditions
- completed projects
- historical events
- extinct resources
- abandoned locations
- Chronicle progression

---

# Naming Convention

Snapshot folders use:

day-XXX

Format:

day-001
day-002
day-003

Rules:

- three digit numbering
- sequential creation
- never reused

---

# Final Rule

Current State = Present World
Snapshots = Recorded History

The current database changes.

The snapshot archive remembers.