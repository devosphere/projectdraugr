# Project Draugr

# 02 - Game Design Document

| Field | Value |
|--------|-------|
| Document ID | PD-002 |
| Version | 2.0.0 |
| Status | Approved |
| Owner | Project Draugr Development Team |
| Last Updated | July 2026 |

---

# Purpose

The Game Design Document (GDD) defines every gameplay and world system that governs Project Draugr.

Unlike the Vision Bible, which establishes the philosophy of the project, this document specifies how that philosophy is translated into mechanics, simulation, and player experience.

To keep the documentation maintainable, every major system is documented independently under the `/docs/02/` directory.

This document serves as the master index and roadmap for the complete design of Project Draugr.

---

# Relationship to Other Documents

The recommended reading order is:

```
README.md
    ↓
00 - Project Overview
    ↓
01 - Vision Bible
    ↓
02 - Game Design Document
    ↓
Individual World Systems
    ↓
03 - Technical Requirements
    ↓
04 - System Architecture
```

Each document builds upon the understanding established by the previous one.

---

# Design Philosophy

Every system described within this document must reinforce the principles defined in the Vision Bible.

When designing or implementing a feature, developers should always ask:

> **"Would this make sense if this world actually existed?"**

Simulation takes priority over convenience.

Logic takes priority over tradition.

Consistency takes priority over spectacle.

---

# World-First Design

Project Draugr is designed from the perspective of the world rather than the player.

The world does not react because the player exists.

Instead, the player enters an already living world and must adapt to it.

Every system should be designed under the assumption that:

- the world existed before the player's arrival;
- the world continues after the player's death; and
- the world continues while the player is absent.

---

# Documentation Standards

Every World System document must follow the same structure.

1. Purpose
2. Design Philosophy
3. Overview
4. Core Rules
5. Gameplay Flow
6. System Components
7. Interactions with Other Systems
8. Player Experience
9. Developer Notes
10. Edge Cases
11. Future Expansion

This structure ensures every document remains implementation-ready while maintaining consistency across the project.

---

# Chronicle Lifecycle

Every Chronicle follows the same lifecycle.

```
Earth

↓

The Overseer

↓

Arrival Protocol

↓

Adaptive Assessment

↓

Transportation

↓

Awakening

↓

Observation

↓

Learning

↓

Survival

↓

Civilization

↓

Legacy

↓

Death

↓

The World Continues
```

Every World System supports one or more stages of this lifecycle.

---

# World Systems

The following documents collectively define Project Draugr.

---

## Part I — Arrival

These systems govern the beginning of every Chronicle.

| ID | Document |
|----|----------|
| 02.01 | The Overseer |
| 02.02 | Arrival Protocol |
| 02.03 | Adaptive Assessment |
| 02.04 | Transportation |
| 02.05 | Awakening |

---

## Part II — The Living World

These systems define how the world exists independently of the player.

| ID | Document |
|----|----------|
| 02.06 | Real-Time System |
| 02.07 | World Continuity System |
| 02.08 | Weather System |
| 02.09 | World Structure |
| 02.10 | Regions |
| 02.11 | Zones |
| 02.12 | Locations |
| 02.13 | Ecosystem |
| 02.14 | Corruption |
| 02.15 | World Events |

---

## Part III — The Human

These systems govern the player's physical existence within the world.

| ID | Document |
|----|----------|
| 02.16 | Observation System |
| 02.17 | Player Action System |
| 02.18 | Survival Needs |
| 02.19 | Health System |
| 02.20 | Knowledge System |
| 02.21 | Skill System |
| 02.22 | Discovery System |
| 02.23 | Research System |
| 02.24 | Experimentation System |

---

## Part IV — Survival

These systems define how humans survive and shape the world.

| ID | Document |
|----|----------|
| 02.25 | Resources |
| 02.26 | Inventory System |
| 02.27 | Equipment System |
| 02.28 | Crafting System |
| 02.29 | Shelter System |
| 02.30 | Farming System |
| 02.31 | Hunting System |
| 02.32 | Fishing System |
| 02.33 | Cooking System |

---

## Part V — Civilization

These systems describe society and interaction.

| ID | Document |
|----|----------|
| 02.34 | Creatures |
| 02.35 | NPC System |
| 02.36 | Relationships |
| 02.37 | Reputation |
| 02.38 | Companions |
| 02.39 | Settlements |
| 02.40 | Trade |

---

## Part VI — Legacy

These systems preserve history across generations.

| ID | Document |
|----|----------|
| 02.41 | Dream System |
| 02.42 | Chronicle System |
| 02.43 | World Persistence |
| 02.44 | Death |

---

# Development Order

The recommended implementation sequence is:

1. Arrival
2. The Living World
3. The Human
4. Survival
5. Civilization
6. Legacy

Each phase builds upon the systems implemented before it.

---

# Documentation Rules

Every World System must answer the following questions.

- Why does this system exist?
- What problem does it solve?
- How does it support the Vision Bible?
- How does it interact with other systems?
- What are its governing rules?
- What happens in exceptional situations?
- How can it evolve without breaking previous systems?

If these questions cannot be answered, the document is incomplete.

---

# Design Principles

Every World System should satisfy the following principles.

- The world exists independently of the player.
- Every Chronicle lives only once.
- Every life leaves a mark.
- The world never waits.
- Actions define identity.
- Knowledge is progression.
- Preparation is rewarded.
- Simulation is authoritative.
- Narrative supports simulation.
- Mystery should remain mysterious.

---

# Final Statement

Project Draugr is not a collection of gameplay mechanics.

It is the specification of a living world.

Every document within this section should contribute to one illusion:

> **That another world truly exists alongside our own, and that every Chronicle is simply another life lived within it.**

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 2.0.0 | July 2026 | Complete rewrite of the Game Design Document to support the final world-first design philosophy. |
