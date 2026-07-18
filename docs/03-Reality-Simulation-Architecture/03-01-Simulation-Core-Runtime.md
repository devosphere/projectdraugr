# Project Draugr

# 03-01 - Simulation Core Runtime

| Field | Value |
|--------|-------|
| Document ID | PD-003.01 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Reality Simulation Architecture |
| Depends On | 01 - Vision Bible, 02 - Game Design Document |
| Related Layers | World Simulation Layer, Causality Framework, Reality Integrity Layer |

---

# Purpose

The Simulation Core Runtime is the execution heart of Project Draugr.

It is responsible for continuously advancing the simulated reality by coordinating every simulation layer in a deterministic, scalable, and consistent manner.

Unlike a traditional game loop that primarily serves rendering and player interaction, the Simulation Core Runtime exists to advance the state of reality itself.

The renderer observes the simulation.

The player interacts with the simulation.

The Simulation Core Runtime governs the simulation.

---

# Design Philosophy

Project Draugr does not simulate gameplay.

It simulates existence.

Gameplay emerges from the simulation rather than driving it.

Every update executed by the runtime must reinforce one principle:

> Reality progresses regardless of observation.

---

# Core Responsibilities

The runtime is responsible for:

- advancing simulation time;
- scheduling simulation tasks;
- maintaining execution order;
- coordinating simulation layers;
- guaranteeing deterministic behavior where required;
- distributing workloads across available computing resources;
- ensuring consistency between simulation systems.

The runtime never contains gameplay logic.

It only executes systems that implement reality.

---

# Architectural Position

The Simulation Core Runtime occupies the highest execution layer.

```
Reality Simulation Runtime

        │

        ▼

World Simulation Layer

        ▼

Causality Framework

        ▼

Reality Integrity Layer

        ▼

Emergent Narrative Layer

        ▼

Player Integration Layer

        ▼

Existence Management Layer

        ▼

Rendering / Audio / Networking
```

The simulation always executes before presentation.

---

# Runtime Principles

## Reality First

Simulation advances even when no players are connected.

---

## Deterministic Execution

Whenever identical inputs occur, identical simulation results should be produced unless explicitly marked as probabilistic.

---

## Layer Isolation

Simulation layers communicate through defined interfaces.

No layer directly manipulates another layer's internal state.

---

## Continuous Progression

Reality advances continuously.

There is no concept of "waiting for the player."

---

## Event Driven

Simulation reacts to changes in state rather than arbitrary scripted sequences.

---

# Runtime Components

The runtime consists of four major components:

```
Simulation Scheduler

↓

Tick Manager

↓

Execution Coordinator

↓

Parallel Work Dispatcher
```

Each component has a single responsibility.

---

# Simulation Scheduler

Determines which systems require execution.

Responsible for:

- dependency ordering;
- execution frequency;
- task prioritization.

---

# Tick Manager

Advances simulation time.

Supports:

- real-time progression;
- accelerated simulation;
- paused observation;
- background progression.

Tick frequency is configurable per simulation subsystem.

---

# Execution Coordinator

Coordinates execution between simulation layers.

Ensures:

- correct execution order;
- dependency resolution;
- synchronization barriers.

---

# Parallel Work Dispatcher

Distributes independent workloads across available processing resources.

Examples include:

- NPC AI updates;
- ecosystem simulation;
- weather calculations;
- economic simulation.

Parallel execution must never violate deterministic rules.

---

# Simulation Flow

Every simulation cycle follows the same sequence.

```
Advance Time

↓

Determine Active Systems

↓

Resolve Dependencies

↓

Execute Simulation Layers

↓

Validate Reality

↓

Commit World State

↓

Generate Observable Events

↓

Begin Next Cycle
```

Each completed cycle represents a consistent world state.

---

# Relationship With Other Layers

The runtime does not contain:

- NPC logic;
- economy;
- weather;
- civilizations;
- player systems.

Those belong to higher simulation layers.

The runtime simply executes them.

---

# Developer Notes

The Simulation Core Runtime should remain as small as possible.

Every feature added to the runtime increases the complexity of the entire simulation architecture.

The runtime should focus exclusively on execution orchestration.

All domain-specific logic belongs to the appropriate simulation layer.

---

# Future Expansion

Future versions may introduce:

- distributed simulation clusters;
- server authority partitioning;
- multi-threaded region simulation;
- heterogeneous compute scheduling;
- cloud-backed world simulation.

The runtime architecture should remain flexible enough to support these features without redesign.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Simulation Core Runtime architecture. |