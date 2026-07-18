# Project Draugr

# 02-13 - Locations

| Field | Value |
|--------|-------|
| Document ID | PD-002.13 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Environment System |
| Depends On | 02-12 - Zones |
| Related Systems | 02-14 - Weather and Seasons, 02-15 - Ecosystem, 02-16 - Corruption System, Chronicle System |

---

# Purpose

This document defines the Location System within Project Draugr.

Locations represent meaningful places that exist within Zones.

They are the primary points where exploration, discovery, interaction, and emergent stories occur.

---

# Design Philosophy

Locations are not created only for gameplay objectives.

They exist because the world logically contains them.

A location may have:

- a history;
- a purpose;
- inhabitants;
- resources;
- secrets;
- dangers.

The Awakened Soul discovers locations.

They do not activate them.

---

# Core Principle

## A Location Is A Story That Already Exists.

The Awakened Soul does not create meaning.

They uncover meaning.

---

# World Hierarchy

The world follows:

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

---

# Purpose of Locations

Locations provide:

## Exploration

Places where the Awakened Soul can:

- investigate;
- discover;
- learn;
- interact.

---

## Resources

Locations may contain:

- materials;
- equipment;
- knowledge;
- shelter;
- rare discoveries.

---

## History

Locations preserve evidence of the past.

Examples:

- abandoned settlements;
- ruins;
- research facilities;
- graves;
- forgotten structures.

---

## Interaction

Locations may contain:

- NPCs;
- creatures;
- environmental events;
- mysteries.

---

# Location Types

Locations are categorized by function.

---

# Natural Locations

Places formed naturally.

Examples:

- caves;
- rivers;
- forests;
- mountains;
- underground formations.

---

# Artificial Locations

Places created by intelligent beings.

Examples:

- houses;
- cities;
- factories;
- laboratories;
- fortifications.

---

# Historical Locations

Places connected to important events.

Examples:

- battlefield ruins;
- disaster sites;
- ancient civilizations;
- forgotten settlements.

---

# Mysterious Locations

Places with unknown origins or unusual properties.

Examples:

- impossible structures;
- unexplained phenomena;
- locations connected to the Overseer.

---

# Corrupted Locations

Places significantly affected by corruption.

Examples:

- mutated landscapes;
- corrupted ruins;
- unstable environments.

---

# Location Identity

Every significant location should have:

## Origin

Why does this place exist?

---

## History

What happened here before discovery?

---

## Current State

What exists here now?

---

## Potential Future

How can this place change?

---

# Location States

Locations are not static.

They may exist in different states.

Example:

A settlement:

```
Founded

↓

Growing

↓

Abandoned

↓

Ruined

↓

Rediscovered
```

---

# Discovery System

Locations are discovered through:

- exploration;
- observation;
- NPC information;
- research;
- investigation.

Discovery may reveal:

- name;
- history;
- resources;
- dangers;
- possible interactions.

---

# Hidden Locations

Some locations may remain unknown.

Hidden locations may require:

- specific knowledge;
- exploration;
- environmental clues;
- special conditions.

---

# Location Persistence

Every discovered location remains part of the world.

Changes persist.

Examples:

The Awakened Soul:

Builds a shelter.

Later:

The shelter may become:

- abandoned;
- occupied;
- discovered by others;
- part of history.

---

# Player-Created Locations

The Awakened Soul may create locations.

Examples:

- homes;
- farms;
- workshops;
- settlements;
- communities.

Over time, player creations may become part of world history.

---

# Location Interaction

Interaction depends on:

- location type;
- current state;
- available resources;
- player knowledge.

A location should not reveal everything immediately.

---

# Overseer Interaction

Certain locations may become vessels for Overseer encounters.

The Overseer may appear as:

- a place;
- a structure;
- a natural phenomenon;
- an object;
- another form.

The appearance should match the world's logic.

---

# Location Risk

Locations may contain:

- environmental dangers;
- hostile creatures;
- structural hazards;
- unknown effects.

Risk should be logical.

---

# Integration With Other Systems

## Zones

Determine the environmental conditions around locations.

---

## Ecosystem

Determines life around locations.

---

## Corruption System

Determines corruption influence.

---

## Chronicle System

Records meaningful player interactions.

---

## World History

Preserves location changes over time.

---

# Developer Notes

Locations are where the procedural world gains personality.

Generation should prioritize:

- logical placement;
- environmental consistency;
- historical meaning.

A location should answer:

"Why is this here?"

"What happened here?"

"Why would someone care?"

---

# Edge Cases

## Abandoned Locations

Should still contain evidence of previous existence.

---

## Destroyed Locations

Destruction does not remove history.

The remains become part of the world.

---

## Unknown Locations

Unknown locations must still follow simulation rules.

Mystery should come from missing information, not randomness.

---

# Future Expansion

Potential future systems:

- location ownership;
- settlement development;
- archaeology;
- location reputation;
- Chronicle landmarks.

---

# Final Statement

The world contains countless places.

But only some places leave memories.

Locations are where the world tells stories without speaking.

The Awakened Soul does not enter empty spaces.

They enter places where something already happened.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of the Location System. |