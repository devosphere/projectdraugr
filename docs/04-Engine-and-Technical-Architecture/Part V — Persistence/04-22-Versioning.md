# Project Draugr

# 04-22 - Versioning

| Field | Value |
|--------|-------|
| Document ID | PD-004.22 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-20 - Save System, 04-21 - Serialization |
| Related Documents | 03-07 - Existence Management Layer, 04-23 - World Recovery, 04-32 - Build Pipeline |

---

# Purpose

The Versioning System defines how Project Draugr evolves without invalidating existing Chronicles.

As the engine grows, simulation rules, component layouts, data structures, and gameplay systems will inevitably change.

Versioning ensures those changes never erase the history of an existing world.

A Chronicle should outlive the engine that created it.

---

# Design Philosophy

Worlds are historical artifacts.

The engine evolves.

The Chronicle does not.

The responsibility of the engine is to understand older worlds—not require worlds to understand newer engines.

Backward compatibility is an architectural commitment, not an optional feature.

---

# Core Principles

## Chronicle Preservation

No engine update should unnecessarily invalidate an existing Chronicle.

---

## Explicit Evolution

Every structural change must be intentionally versioned.

---

## Deterministic Migration

The same save migrated twice must always produce the same result.

---

## Immutable History

Original historical data is never rewritten during migration.

---

## Progressive Compatibility

Older versions may be upgraded step-by-step without requiring intermediate engine releases.

---

# Responsibilities

The Versioning System is responsible for:

- schema version tracking;
- compatibility verification;
- migration sequencing;
- deprecation management;
- data transformation coordination;
- version metadata.

It is not responsible for:

- serialization;
- save scheduling;
- corruption recovery;
- gameplay logic.

---

# Version Hierarchy

Project Draugr maintains multiple independent version layers.

Examples:

- engine version;
- save format version;
- component schema version;
- simulation rules version;
- asset version;
- database version.

Each layer evolves independently.

---

# Save Metadata

Every Chronicle stores immutable version metadata.

Example:

```text
Engine Version

Simulation Version

Serialization Version

Schema Version

Asset Version

Creation Version

Current Migration Version
```

This metadata determines the required migration path.

---

# Compatibility Model

Loading a Chronicle follows this decision process.

```text
Read Save

↓

Read Version Metadata

↓

Compatible?

↓

Yes → Load

↓

No

↓

Migration Available?

↓

Yes → Migrate

↓

Validate

↓

Load

↓

No

↓

Reject
```

Compatibility is always verified before reconstruction begins.

---

# Schema Evolution

Every serialized structure possesses a schema identifier.

Example:

```text
HealthComponent

Schema v1

↓

Schema v2

↓

Schema v3
```

Each version explicitly defines how it transforms into the next.

---

# Migration Pipeline

Every migration follows the same sequence.

```text
Load Old Data

↓

Identify Version

↓

Apply Migration Rules

↓

Validate

↓

Update Metadata

↓

Continue Loading
```

Each migration produces a deterministic result.

---

# Component Migration

Each ECS component evolves independently.

Example:

Version 1

```text
Health
```

Version 2

```text
Health
Maximum Health
```

Migration initializes newly introduced fields according to defined rules rather than arbitrary defaults.

---

# Entity Migration

Entities may acquire:

- new components;
- removed components;
- reorganized relationships.

Migration preserves logical identity throughout transformation.

---

# System Migration

Entire simulation systems may evolve.

Examples:

- economy redesign;
- AI improvements;
- weather expansion;
- civilization updates.

System migrations maintain behavioral continuity whenever possible.

---

# Deprecated Data

Deprecated structures are never interpreted indefinitely.

Lifecycle:

```text
Introduced

↓

Supported

↓

Deprecated

↓

Migration Available

↓

Removed
```

Removal only occurs after a deterministic migration exists.

---

# Version Contracts

Every released engine version defines:

- supported save versions;
- supported migration paths;
- unsupported legacy versions.

Compatibility guarantees remain explicit.

---

# Asset Versioning

Assets evolve independently from simulation data.

Examples:

- meshes;
- textures;
- animations;
- materials;
- audio.

Asset replacement must never invalidate world history.

---

# Simulation Rule Versioning

Changes to simulation behavior may require migration.

Examples:

- disease mechanics;
- farming systems;
- AI reasoning;
- combat calculations.

Migration preserves consistency between historical state and new simulation rules.

---

# Historical Authenticity

Migration updates representation—not history.

Example:

A kingdom that collapsed before an update remains collapsed afterward.

Migration changes structure, never historical outcomes.

---

# Rollback

Migration is transactional.

If migration fails:

- discard migrated data;
- preserve original save;
- report failure.

Original Chronicles remain untouched.

---

# Unsupported Versions

Very old versions may eventually become unsupported.

Even then:

- original saves remain intact;
- migration tools may still exist externally;
- unsupported data is never silently modified.

Failure should always be explicit.

---

# Developer Workflow

Whenever persistent structures change, developers must provide:

- new schema version;
- migration logic;
- validation tests;
- compatibility documentation.

Schema changes without migration are prohibited.

---

# Integration with Serialization

Serialization records schema versions.

Versioning interprets them.

Serialization never performs migration.

---

# Integration with Recovery

If migration fails because of damaged data, the Recovery System determines whether repair is possible.

Versioning itself never reconstructs corrupted information.

---

# Debug Support

Developer tools should visualize:

- version metadata;
- migration history;
- schema evolution;
- component compatibility;
- deprecated structures;
- migration performance.

Developers should always know how a Chronicle reached its current version.

---

# Performance Considerations

The Versioning System should optimize:

- incremental migrations;
- lazy migration where appropriate;
- reusable migration stages;
- schema caching;
- validation efficiency.

Migration occurs infrequently, but must remain reliable regardless of world size.

---

# Developer Notes

Project Draugr is intended to outlive many generations of its own engine.

Years from now, a player should be able to load the very first Chronicle they ever created.

The forests should still grow where they planted them.

The cities should still bear the scars of forgotten wars.

The descendants of long-dead heroes should still inherit the consequences of their ancestors.

Versioning exists so that history is never erased simply because technology advanced.

The engine evolves.

The Chronicle remembers.

---

# Future Expansion

Future versions may introduce:

- automatic migration generation;
- schema diff visualization;
- live migration during simulation;
- branch-specific compatibility;
- experimental feature channels;
- long-term archival compatibility;
- distributed migration services;
- community migration extensions.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Versioning architecture defining deterministic long-term compatibility for persistent Chronicles. |