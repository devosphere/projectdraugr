# Project Draugr

# 02-12 - Zones

| Field | Value |
|--------|-------|
| Document ID | PD-002.12 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Environment System |
| Depends On | 02-11 - Regions |
| Related Systems | 02-13 - Locations, 02-14 - Weather and Seasons, 02-15 - Ecosystem, 02-16 - Corruption System |

---

# Purpose

This document defines the Zone System within Project Draugr.

Zones represent smaller environmental divisions inside larger regions.

They define specific conditions that influence:

- survival;
- exploration;
- resources;
- wildlife;
- civilization;
- player decisions.

---

# Design Philosophy

Zones are not traditional game areas.

They are not designed around:

- player level;
- quest progression;
- difficulty tiers.

Zones exist because the world naturally contains different environments.

---

# Core Principle

## A Region Defines Where You Are.

## A Zone Defines What You Experience.

A Region provides the broader identity.

A Zone provides the immediate reality.

---

# World Hierarchy

The world follows this structure:

```
World

↓

Continent

↓

Region

↓

Zone

↓

Location

↓

Point of Interest
```

Zones exist between geography and individual places.

---

# Purpose of Zones

Zones provide:

## Environmental Variation

A single region may contain multiple conditions.

Example:

A desert region may contain:

- dry dunes;
- underground water systems;
- abandoned settlements;
- salt plains.

---

## Survival Challenges

Different zones create different survival requirements.

Examples:

A frozen zone:

Requires:

- warmth;
- shelter;
- protection.

---

A corrupted zone:

Requires:

- caution;
- preparation;
- understanding.

---

## Exploration Decisions

Zones influence player choices.

The Awakened Soul must consider:

- Is this area worth exploring?
- Is the risk worth the reward?
- Do I have enough supplies?

---

# Zone Classification

Zones are classified through multiple factors.

---

# Environmental Type

Based on physical conditions.

Examples:

- forest zone;
- wasteland zone;
- mountain zone;
- ocean zone;
- underground zone.

---

# Survival Condition

Based on difficulty of existence.

Examples:

## Stable Zone

Life can survive normally.

Characteristics:

- resources available;
- manageable threats;
- possible settlements.

---

## Hostile Zone

Survival is difficult.

Characteristics:

- limited resources;
- dangerous creatures;
- environmental hazards.

---

## Extreme Zone

Survival requires preparation.

Characteristics:

- severe conditions;
- rare resources;
- high danger.

---

## Corrupted Zone

The environment has been altered.

Characteristics:

- unstable conditions;
- mutated life;
- unknown effects.

---

# Zone Boundaries

Zones should transition naturally.

There should not be invisible walls.

A player should experience environmental changes gradually.

Examples:

A forest transition:

```
Healthy Forest

↓

Damaged Woodland

↓

Dead Forest

↓

Corrupted Forest
```

---

# Zone Persistence

Every zone exists permanently.

A zone:

- has history;
- changes over time;
- reacts to events;
- maintains conditions.

---

# Zone Evolution

Zones may change because of:

- corruption spread;
- climate changes;
- player actions;
- civilization growth;
- natural disasters.

Example:

A barren zone may slowly recover.

A fertile zone may become corrupted.

---

# Resources Within Zones

Zones influence resource availability.

A zone determines:

- possible resources;
- abundance;
- regeneration speed;
- rarity.

Example:

A mountain zone may contain:

- minerals;
- caves;
- rare materials.

---

# Wildlife Within Zones

Zones influence:

- creature types;
- population;
- behavior;
- migration.

Creatures exist because the environment supports them.

---

# Civilization Interaction

Civilizations select zones based on survival requirements.

Factors:

- water availability;
- resources;
- defensibility;
- climate;
- corruption level.

---

# Zone Discovery

Zones are not revealed automatically.

The Awakened Soul discovers them through:

- exploration;
- observation;
- maps;
- NPC information;
- research.

---

# Mapping

A player may gradually create knowledge about zones.

Mapping may reveal:

- terrain;
- resources;
- dangers;
- locations.

Knowledge becomes valuable.

---

# Zone Difficulty

Difficulty is not a fixed number.

A zone's danger depends on:

- environment;
- preparation;
- player knowledge;
- current conditions.

A prepared survivor may handle a dangerous zone.

An unprepared survivor may die in a normal zone.

---

# Integration With Other Systems

## Regions

Provide the larger environmental identity.

---

## Locations

Provide specific meaningful places.

---

## Weather System

Changes zone conditions.

---

## Ecosystem

Determines life within zones.

---

## Corruption System

Can transform zones over time.

---

# Developer Notes

Zones should support procedural generation.

A zone should be generated based on:

- geography;
- climate;
- history;
- resources;
- corruption.

Not random placement alone.

---

# Edge Cases

## Uninhabitable Zones

Some zones may be nearly impossible to survive.

However, they should still follow logical rules.

---

## Player-Created Zones

Large-scale player actions may eventually create new zones.

Examples:

- artificial settlements;
- restored forests;
- corrupted territories.

---

## Unknown Zones

Unknown zones still exist and follow simulation rules.

---

# Future Expansion

Future systems may define:

- procedural zone generation;
- zone ownership;
- territorial influence;
- regional conflicts.

---

# Final Statement

A region tells the Awakened Soul where they are.

A zone tells them what kind of world they are standing in.

Every step forward is a decision.

Every zone is a question:

"Are you prepared for what exists here?"

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of the Zone System. |