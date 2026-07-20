# 05 – Technical Architecture

> **Project Draugr**
>
> *Building a persistent world.*

---

# Purpose

This document defines the high-level software architecture of Project Draugr.

Its objective is to ensure that every technical decision supports the project's creative vision.

The architecture exists to build a believable persistent world simulation—not merely a multiplayer game.

---

# Architectural Philosophy

Project Draugr is built around one fundamental principle:

> **The world is the primary system.**

Players are clients connected to the world.

The world is not created for players.

It already exists.

Every technical system should reinforce this philosophy.

---

# Architectural Layers

Project Draugr is divided into independent layers.

```text
Presentation Layer

↓

Gameplay Layer

↓

World Simulation Layer

↓

Persistence Layer

↓

Infrastructure Layer
```

Each layer has clearly defined responsibilities.

---

# Presentation Layer

Responsible for everything presented to the player.

Examples include:

- User Interface
- HUD
- Menus
- Inventory
- Maps
- Chronicle Viewer
- Animations
- Audio
- Visual Effects

The presentation layer never contains simulation logic.

---

# Gameplay Layer

Responsible for player interaction.

Examples include:

- movement
- crafting
- construction
- combat
- gathering
- farming
- inventory
- equipment
- player actions

Gameplay requests changes.

It does not directly modify the world.

---

# World Simulation Layer

The heart of Project Draugr.

Responsible for continuously simulating:

- ecology
- wildlife
- monsters
- weather
- resource regeneration
- environmental changes
- civilization growth
- historical progression

The simulation runs independently of player presence.

Players influence it.

They do not drive it.

---

# Persistence Layer

Responsible for preserving the world's state.

Examples include:

- geography
- player Chronicles
- settlements
- buildings
- resources
- ecology
- history
- journals
- discoveries

Persistence guarantees that the world survives server restarts and future Chronicles.

---

# Infrastructure Layer

Responsible for operating the platform.

Examples include:

- networking
- authentication
- storage
- backups
- monitoring
- deployment
- scalability
- security

This layer should remain invisible to gameplay.

---

# World State

The world exists as one persistent canonical state.

Every connected player observes the same world.

There are no private instances.

There are no duplicated worlds.

Every meaningful action modifies the shared simulation.

---

# State-Driven Simulation

Project Draugr uses state-driven simulation.

Systems continuously evaluate the current world state.

They do not wait for player triggers.

Example:

The simulation asks:

> "What naturally happens next?"

rather than

> "What event should happen because the player arrived?"

---

# Event System

Meaningful actions generate world events.

Examples include:

- tree cut down
- structure completed
- settlement founded
- wildfire started
- player death
- bridge constructed

Events update the world state.

They also become historical records whenever appropriate.

---

# Historical Persistence

History is treated as first-class data.

Examples include:

- discovered locations
- player journals
- abandoned settlements
- ruins
- roads
- graves
- civilizations

History should never be reconstructed from gameplay.

It should already exist within the persistent world.

---

# Modular Architecture

Every major system should remain modular.

Examples include:

World Simulation

Gameplay

Construction

Combat

Crafting

Weather

Ecology

Civilization

History

Modules communicate through clearly defined interfaces.

Changes within one module should minimize impact on others.

---

# Server Authority

The persistent world is authoritative.

Clients request actions.

The server validates them.

The simulation determines the outcome.

Clients never define world state.

---

# Scalability

The architecture should support gradual expansion.

Initial versions focus on a single persistent region.

Future expansion may introduce:

- additional regions
- larger simulations
- more simultaneous players
- deeper ecological systems
- more complex civilization simulation

Expansion should not require architectural redesign.

---

# Performance Philosophy

Performance should prioritize simulation integrity.

Optimization must never compromise world consistency.

The world should continue behaving logically even under heavy load.

When trade-offs become necessary:

Simulation consistency always takes priority over visual complexity.

---

# Technology Independence

This architecture intentionally avoids dependency on specific technologies.

Programming languages.

Frameworks.

Databases.

Rendering engines.

Networking solutions.

May change throughout the project's lifetime.

The architectural principles documented here should remain valid regardless of implementation.

---

# Future Artificial Intelligence

Artificial intelligence is not a core dependency.

The persistent simulation must remain complete without it.

Future iterations may gradually introduce intelligent existences.

These should integrate into the existing simulation rather than replace it.

The simulation remains the foundation.

Artificial intelligence becomes another participant within that foundation.

---

# Development Principles

Every technical decision should satisfy the following questions.

## Persistence

Does it preserve the world's history?

---

## Modularity

Can it evolve independently?

---

## Simulation First

Does it strengthen the world rather than the player?

---

## Scalability

Will it continue working as the world expands?

---

## Simplicity

Is this the simplest solution that preserves the vision?

---

# Final Principle

Project Draugr is not engineered as a traditional multiplayer game.

It is engineered as a persistent world simulation.

Everything else exists to support that world.