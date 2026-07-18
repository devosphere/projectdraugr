# Project Draugr

# 02 - Game Design Document

| Field | Value |
|--------|-------|
| Document ID | PD-002 |
| Version | 0.1.0 |
| Status | Draft |
| Owner | Project Draugr Development Team |
| Last Updated | July 2026 |

---

# Purpose

The Game Design Document (GDD) is the primary gameplay specification for Project Draugr.

Rather than containing every gameplay mechanic in a single document, this file serves as the master index for the complete design documentation.

Each gameplay system is documented in its own file under the `/docs/02/` directory.

This modular structure allows systems to evolve independently while maintaining consistency across the project.

---

# Relationship to Other Documents

This document should be read after:

- `00-Project-Overview.md`
- `01-Vision-Bible.md`

Implementation details are described in:

- `03-Technical-Requirements.md`
- `04-System-Architecture.md`

---

# Character Lifecycle

Every character progresses through the following lifecycle.

```
Earth
    ↓
The Observer
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
Discovery
    ↓
Mastery
    ↓
Legacy
    ↓
Death
    ↓
The World Continues
```

Every gameplay system exists to support one or more stages of this lifecycle.

---

# Design Principles

The gameplay systems documented within this section must always uphold the following principles:

- The world exists independently of the player.
- Actions define identity.
- Knowledge is progression.
- Freedom is preferred over scripted outcomes.
- Consequences should persist whenever practical.
- AI narrates events but does not determine game state.
- Every mechanic should create opportunities for emergent storytelling.

---

# Gameplay Systems

## Part I — Arrival

| ID | Document | Purpose |
|----|----------|---------|
| 02.01 | The Observer | Defines the hidden observer system and evaluations. |
| 02.02 | Adaptive Assessment | Dynamic introduction and personality assessment. |
| 02.03 | Transportation | Rules governing transportation from Earth. |
| 02.04 | Awakening | Initial player experience after arrival. |

---

## Part II — First Days

| ID | Document | Purpose |
|----|----------|---------|
| 02.05 | Observation | Looking, examining, sensing the environment. |
| 02.06 | Player Actions | Natural language action system. |
| 02.07 | Time System | Time progression and schedules. |
| 02.08 | Weather System | Environmental conditions. |
| 02.09 | Survival Needs | Hunger, thirst, fatigue, body condition. |
| 02.10 | Exploration | Discovery and movement. |

---

## Part III — Learning

| ID | Document | Purpose |
|----|----------|---------|
| 02.11 | Knowledge System | Knowledge acquisition and retention. |
| 02.12 | Skill System | Action-based progression. |
| 02.13 | Discovery System | First-time discoveries and insights. |
| 02.14 | Research System | Experimentation and analysis. |
| 02.15 | Experimentation | Learning through trial and error. |

---

## Part IV — Survival

| ID | Document | Purpose |
|----|----------|---------|
| 02.16 | Resources | Materials found in the world. |
| 02.17 | Inventory System | Item storage and management. |
| 02.18 | Equipment System | Wearable items and durability. |
| 02.19 | Crafting System | Creating tools, weapons, and structures. |
| 02.20 | Shelter System | Protection and resting places. |
| 02.21 | Farming System | Cultivation and food production. |
| 02.22 | Hunting System | Tracking and harvesting wildlife. |
| 02.23 | Fishing System | Aquatic food gathering. |
| 02.24 | Cooking System | Food preparation and preservation. |

---

## Part V — The Living World

| ID | Document | Purpose |
|----|----------|---------|
| 02.25 | World | Global world structure. |
| 02.26 | Regions | Large geographical divisions. |
| 02.27 | Zones | Subdivisions within regions. |
| 02.28 | Locations | Individual explorable places. |
| 02.29 | Creatures | Wildlife and monsters. |
| 02.30 | NPC System | Artificial intelligence for inhabitants. |
| 02.31 | Corruption | Environmental corruption mechanics. |
| 02.32 | World Events | Dynamic world events. |
| 02.33 | Ecosystem | Flora, fauna, and ecological simulation. |

---

## Part VI — Society

| ID | Document | Purpose |
|----|----------|---------|
| 02.34 | Relationships | Social interactions and trust. |
| 02.35 | Reputation | How the world perceives the character. |
| 02.36 | Companions | Allies and followers. |
| 02.37 | Settlements | Villages and player-built communities. |
| 02.38 | Trade | Commerce and exchange. |

---

## Part VII — Legacy

| ID | Document | Purpose |
|----|----------|---------|
| 02.39 | Dream System | Symbolic dreams and Observer encounters. |
| 02.40 | Death | Character death and its consequences. |
| 02.41 | Chronicle System | Recording the history of each life. |
| 02.42 | World Persistence | Permanent changes across generations. |

---

# Development Order

The recommended implementation sequence is:

1. Arrival
2. First Days
3. Learning
4. Survival
5. Living World
6. Society
7. Legacy

Each phase builds upon the systems implemented before it.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 0.1.0 | July 2026 | Initial Game Design Document index created. |
