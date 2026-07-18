# Project Draugr

# 04-04 - World State Storage

| Field | Value |
|--------|-------|
| Document ID | PD-004.04 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-01 - Entity Component System, 04-02 - System Scheduler, 04-03 - Data-Oriented Architecture |
| Related Documents | 03-02 - World Simulation Layer, 03-07 - Existence Management Layer |

---

# Purpose

The World State Storage defines how the complete state of Project Draugr is represented, organized, accessed, and maintained throughout simulation.

It serves as the authoritative source of truth for every entity, component, region, civilization, ecosystem, event, and historical record that exists within the simulation.

The World State Storage is not responsible for simulation.

It is responsible for representing reality.

---

# Design Philosophy

Reality exists as state.

Simulation transforms state.

Narrative interprets state.

Persistence preserves state.

The World State Storage provides the shared representation through which every engine subsystem observes and modifies reality.

There is only one authoritative world state.

Every system interacts with that state.

---

# Core Principles

## Single Source of Truth

Every piece of simulation data has exactly one authoritative representation.

Duplicated state should never exist.

---

## State Before Behavior

Behavior belongs to systems.

State belongs to storage.

Storage contains no simulation logic.

---

## Deterministic Representation

The same simulation always produces the same world state.

State should never depend upon execution timing, rendering, or hardware.

---

## Layer Independence

The storage model is independent of:

- rendering;
- networking;
- user interface;
- tooling;
- persistence implementation.

These layers consume world state but do not define it.

---

## Efficient Access

The storage architecture should optimize for:

- sequential access;
- predictable queries;
- incremental updates;
- scalable streaming.

---

# Responsibilities

The World State Storage is responsible for:

- entity storage;
- component storage;
- spatial organization;
- simulation metadata;
- world metadata;
- relationship tracking;
- historical references;
- global simulation state.

It is not responsible for:

- gameplay logic;
- scheduling;
- AI;
- rendering;
- persistence serialization.

---

# Storage Hierarchy

The simulation is organized into hierarchical layers.

```text
World

↓

Regions

↓

Zones

↓

Locations

↓

Entities

↓

Components
```

Each layer provides organizational context without owning simulation behavior.

---

# Global World State

The global state contains information applicable to the entire simulation.

Examples include:

- simulation time;
- calendar;
- planetary conditions;
- astronomical state;
- active global events;
- world configuration;
- simulation version.

This information is accessible to all simulation systems.

---

# Regional State

Each region maintains localized simulation state.

Examples:

- climate;
- population;
- economy;
- political control;
- corruption;
- biome composition.

Regional state is isolated from unrelated regions whenever possible.

---

# Entity State

Each entity owns only its identity.

All mutable information is stored through components.

Example:

```text
Entity

↓

Transform

Health

Inventory

Knowledge

Relationships
```

Entity definitions remain lightweight regardless of simulation complexity.

---

# Component Storage

Components are grouped by type.

Example:

```text
Transform Storage

↓

Transform A

Transform B

Transform C
```

```text
Health Storage

↓

Health A

Health B

Health C
```

This organization follows the Data-Oriented Architecture.

---

# World Metadata

Metadata describes the simulation itself rather than simulated objects.

Examples include:

- simulation seed;
- world generation version;
- ruleset version;
- active feature flags;
- content pack versions.

Metadata allows worlds to remain reproducible and upgradeable.

---

# Simulation Metadata

Runtime metadata tracks simulation execution.

Examples:

- current tick;
- scheduler phase;
- active partitions;
- loaded regions;
- performance statistics.

Simulation metadata is transient.

It is not considered gameplay state.

---

# Spatial Organization

Entities are indexed by spatial location.

Spatial organization supports:

- visibility;
- streaming;
- physics;
- AI perception;
- environmental simulation;
- navigation.

Spatial indexing structures are defined independently.

---

# Relationship Storage

Relationships are stored explicitly rather than inferred.

Examples:

- family;
- ownership;
- trade agreements;
- diplomatic treaties;
- alliances;
- faction membership.

Relationships reference entity identifiers rather than object pointers.

---

# Historical References

Historical records reference immutable entity identities.

Examples:

```text
Kingdom Founded

↓

Founder Entity ID
```

```text
War Declared

↓

Attacker Entity ID

↓

Defender Entity ID
```

Historical records remain valid even after entities cease to exist.

---

# Query Model

The storage layer supports efficient queries.

Examples:

Find:

- all wolves;
- all burning buildings;
- all hungry villagers;
- all settlements within a region;
- all rivers crossing a biome.

Queries operate on component datasets rather than object hierarchies.

---

# State Mutability

State is divided into two categories.

## Immutable

Examples:

- terrain topology;
- world seed;
- species definitions;
- crafting recipes.

---

## Mutable

Examples:

- weather;
- health;
- economy;
- diplomacy;
- crops;
- AI memory.

Mutable state changes only through authorized systems.

---

# Transaction Boundaries

Simulation updates occur within defined transaction boundaries.

A simulation phase either:

- completes successfully; or
- rolls back safely.

Partial updates must never become authoritative world state.

---

# Consistency Guarantees

The storage layer guarantees:

- valid entity references;
- consistent component ownership;
- deterministic queries;
- atomic state publication.

Consistency validation is performed by the Reality Integrity Layer.

---

# World Snapshots

The architecture supports creation of immutable world snapshots.

Snapshots may be used for:

- debugging;
- replay;
- rollback;
- testing;
- persistence;
- analytics.

Snapshots never interrupt active simulation.

---

# Incremental Updates

Simulation systems modify only the data they own.

Example:

Weather System

Updates:

- humidity;
- rainfall;
- temperature.

It does not modify:

- economy;
- diplomacy;
- AI.

This minimizes contention between systems.

---

# Active vs Inactive State

The storage model distinguishes between:

Active State

Currently simulated.

Inactive State

Loaded but dormant.

Archived State

Historically preserved.

This distinction supports world streaming and scalability.

---

# Streaming Integration

World State Storage cooperates with the streaming architecture.

Active partitions maintain full simulation state.

Inactive partitions maintain compressed or deferred representations.

Streaming policies determine when transitions occur.

---

# Persistence Boundary

The storage layer represents runtime state.

Serialization is handled independently by the persistence architecture.

This separation allows multiple storage formats without changing simulation behavior.

---

# Integration with ECS

The ECS defines how entities and components are represented.

World State Storage defines where they reside within the global simulation.

---

# Integration with Simulation Runtime

Simulation systems read world state.

Simulation systems modify world state.

The scheduler coordinates access.

The storage layer itself performs no scheduling.

---

# Performance Considerations

The storage architecture should optimize for:

- cache locality;
- predictable access patterns;
- low fragmentation;
- efficient queries;
- scalable indexing;
- incremental loading;
- deterministic updates.

Performance should scale with world complexity rather than object count alone.

---

# Developer Notes

The World State Storage is the memory of the living world.

Every simulation layer depends upon its consistency.

Engine developers should resist embedding gameplay logic within storage structures.

Storage represents reality.

Simulation transforms reality.

Maintaining this separation keeps the engine modular, testable, and scalable.

---

# Future Expansion

Future versions may introduce:

- distributed world storage;
- hierarchical storage backends;
- compressed inactive state;
- cloud-native simulation storage;
- adaptive indexing;
- memory tiering;
- persistent shared-memory architectures;
- large-scale simulation sharding.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial World State Storage architecture. |