# Project Draugr

# 04-20 - Save System

| Field | Value |
|--------|-------|
| Document ID | PD-004.20 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 03-02 - World Simulation Layer, 04-04 - World State Storage, 04-05 - Simulation Pipeline |
| Related Documents | 03-06 - Player Integration Layer, 03-07 - Existence Management Layer, 04-21 - Serialization, 04-22 - Versioning, 04-23 - World Recovery |

---

# Purpose

The Save System defines how the complete state of a living Chronicle is preserved and later resumed without violating the integrity of the simulation.

Unlike traditional save systems that primarily preserve player progress, Project Draugr preserves an entire universe.

When a save is created, the entire simulation is suspended, recorded, and made restorable.

Nothing is recreated.

Everything is remembered.

---

# Design Philosophy

Saving is not recording the player.

Saving is preserving reality.

Every entity, every relationship, every memory, every unfinished plan, every weather system, every civilization, and every consequence must survive exactly as they existed.

A loaded world should be indistinguishable from one that was never interrupted.

---

# Core Principles

## World First

The world is the unit of persistence.

The player is only one inhabitant within it.

---

## Deterministic Restoration

Loading must restore the exact simulation state that existed at the moment of saving.

---

## Simulation Integrity

Saving must never alter simulation behavior.

---

## Atomic Operations

A save either completes successfully or not at all.

Partial saves are never considered valid.

---

## Engine Independence

Persistence should remain independent of rendering, audio, and presentation systems.

---

# Responsibilities

The Save System is responsible for:

- initiating save operations;
- coordinating snapshot creation;
- validating save integrity;
- managing save metadata;
- coordinating restoration;
- protecting save consistency.

It is not responsible for:

- serialization formats;
- file compression;
- version migration;
- recovery logic.

These responsibilities belong to related persistence systems.

---

# Save Philosophy

Every save represents a complete snapshot of one Chronicle.

It captures:

- the current universe;
- the simulation state;
- player existence;
- civilization history;
- environmental conditions;
- pending future events.

The save is not merely progress.

It is preserved existence.

---

# Save Lifecycle

Every save follows the same sequence.

```text
Save Request

↓

Simulation Pause Point

↓

World Snapshot

↓

Validation

↓

Serialization

↓

Storage

↓

Verification

↓

Resume Simulation
```

Saving occurs only at simulation-safe synchronization points.

---

# Simulation Pause Point

Saving begins only after the current simulation tick completes.

The engine never interrupts an active simulation update.

This guarantees a consistent world state.

---

# World Snapshot

A snapshot contains every authoritative simulation system.

Examples include:

- entities;
- components;
- AI state;
- memories;
- goals;
- weather;
- economy;
- factions;
- terrain modifications;
- active events;
- chronology.

The snapshot becomes the authoritative save source.

---

# Save Scope

Every save includes:

## World State

- terrain
- environment
- structures
- weather
- ecosystems

---

## Entity State

- position
- health
- inventory
- relationships
- current activity

---

## AI State

- memories
- goals
- plans
- emotional state
- learned behaviors

---

## Simulation State

- current time
- pending events
- active simulations
- scheduled tasks

---

## Civilization State

- politics
- economy
- settlements
- technology
- culture

---

## Player State

- location
- equipment
- knowledge
- discoveries
- reputation
- injuries

The player is only one portion of the saved world.

---

# Snapshot Consistency

Every saved system references the same simulation timestamp.

No system may represent an earlier or later state.

Consistency is mandatory.

---

# Save Metadata

Each save records descriptive metadata.

Examples:

- Chronicle name;
- world seed;
- simulation timestamp;
- play duration;
- save version;
- engine version;
- creation date;
- difficulty;
- world age.

Metadata assists management without affecting simulation.

---

# Save Slots

Each Chronicle exists independently.

Multiple save slots may exist simultaneously.

Each slot represents a unique simulation history.

No save may overwrite another without explicit intent.

---

# Autosave

Automatic saves occur at simulation-safe intervals.

Examples:

- sleeping;
- entering settlements;
- major discoveries;
- Chronicle milestones.

Autosaves should never interrupt active gameplay.

---

# Manual Save

Players may request saving when simulation conditions permit.

If saving is temporarily unsafe, the request is queued until the next synchronization point.

---

# Incremental Saving

Where possible, unchanged data may be reused.

Examples:

- static terrain;
- immutable assets;
- archived historical records.

Only modified simulation data requires rewriting.

---

# Background Saving

Large save operations may execute asynchronously.

The simulation continues using immutable snapshots.

Players should experience minimal interruption.

---

# Save Validation

Every save undergoes integrity checks.

Examples:

- missing entities;
- broken references;
- invalid timestamps;
- corrupted component graphs.

Invalid saves are rejected before completion.

---

# Save Security

Save files should protect against:

- incomplete writes;
- corruption;
- unauthorized modification;
- incompatible engine versions.

Security protects simulation integrity.

---

# Save Identity

Every save possesses a globally unique Chronicle identifier.

This identifier remains constant throughout the Chronicle's existence.

---

# Save Deletion

Deleting a save permanently removes that Chronicle.

No simulation continues once its persistent state has been intentionally discarded.

Deletion is irreversible unless external backups exist.

---

# Integration with Serialization

The Save System determines:

> What should be preserved.

The Serialization System determines:

> How it is preserved.

Responsibilities remain separate.

---

# Integration with Versioning

Every save records its originating engine version.

Future engine versions may migrate saves without altering their historical authenticity.

---

# Integration with Recovery

If saving fails unexpectedly, the Recovery System determines whether the previous valid save can be restored.

The Save System itself never performs recovery.

---

# Debug Support

Developer tools should visualize:

- save snapshots;
- active save requests;
- snapshot duration;
- validation status;
- world timestamps;
- entity counts;
- persistence statistics.

Developers should be able to inspect exactly what is being preserved.

---

# Performance Considerations

The Save System should optimize:

- asynchronous snapshot creation;
- incremental saves;
- immutable data reuse;
- compression coordination;
- multithreaded persistence;
- minimal gameplay interruption.

Large worlds should remain saveable without lengthy pauses.

---

# Developer Notes

Saving should feel like preserving a living universe inside a bottle.

Nothing should awaken differently after restoration.

A farmer should still be halfway through repairing a fence.

A wolf should still be tracking prey.

A storm should still be approaching from the west.

A kingdom should still be preparing for war.

The player should never feel that the world was rebuilt.

They should feel that time simply continued from where it paused.

---

# Future Expansion

Future versions may introduce:

- distributed world snapshots;
- cloud persistence;
- differential save chains;
- timeline branching;
- replay reconstruction;
- historical archive compression;
- collaborative persistent worlds;
- continuous persistence without explicit save operations.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Save System architecture defining world-scale simulation persistence. |