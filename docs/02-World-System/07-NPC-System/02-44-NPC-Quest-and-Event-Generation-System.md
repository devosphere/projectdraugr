# Project Draugr

# 02-44 - NPC Quest and Event Generation System

| Field | Value |
|--------|-------|
| Document ID | PD-002.44 |
| Version | 1.0.0 |
| Status | Approved |
| Category | NPC System |
| Depends On | 02-36 - NPC Life and Behavior System, 02-43 - NPC Intelligence and Decision System |
| Related Systems | World Simulation System, Civilization System, Player Agency System, Event System |

---

# Purpose

This document defines how NPC-driven quests, events, and opportunities emerge naturally from world simulation.

It establishes:

- dynamic events;
- NPC-generated requests;
- emergent stories;
- world-driven opportunities;
- non-scripted experiences.

---

# Design Philosophy

Stories are consequences of a living world.

---

# Core Principle

## The World Creates Situations. The Player Creates The Story.

---

# Quest Definition

In Project Draugr, a quest is not an artificial objective.

A quest is:

A meaningful situation requiring action.

---

# Quest Origin

Events originate from:

```
NPC Needs

+

World Conditions

+

Player Actions

+

Civilization Changes
```

---

# NPC-Generated Situations

NPCs create opportunities through their lives.

Examples:

A farmer:

- needs protection;
- requires resources;
- seeks knowledge.

A merchant:

- needs safer routes;
- seeks rare goods.

A scholar:

- searches for information.

---

# No Quest Dependency

NPCs do not exist to provide quests.

They exist first.

The player may discover their problems.

---

# Event Generation Model

Events are created through:

```
Trigger

↓

Situation

↓

NPC Response

↓

Player Interaction

↓

Consequences
```

---

# Trigger Sources

Events may begin from:

## Personal Events

Examples:

- illness;
- family problems;
- disputes.

---

## Social Events

Examples:

- conflicts;
- celebrations;
- community needs.

---

## Environmental Events

Examples:

- disasters;
- weather changes;
- resource shortages.

---

## Civilization Events

Examples:

- wars;
- political changes;
- migrations.

---

# Event Visibility

Not all events are visible.

---

# Local Events

Known only by nearby NPCs.

Example:

A missing villager.

---

# Regional Events

Known by multiple settlements.

Example:

A spreading conflict.

---

# Historical Events

Impact large areas.

Example:

The fall of a civilization.

---

# Player Discovery

Players discover events through:

- conversation;
- observation;
- exploration;
- reputation;
- investigation.

---

# Quest Formation

A situation becomes a quest when:

```
Player Awareness

+

Possible Action

+

Meaningful Consequence
```

---

# Example

Situation:

A village lacks medicine.

Player learns about it.

Possible actions:

- gather herbs;
- trade supplies;
- ignore it.

Result:

The world changes.

---

# Quest Types

---

# Survival Events

Related to:

- food;
- shelter;
- danger;
- environment.

---

# Social Events

Related to:

- relationships;
- conflicts;
- communities.

---

# Exploration Events

Related to:

- discoveries;
- unknown locations;
- mysteries.

---

# Economic Events

Related to:

- trade;
- resources;
- production.

---

# Political Events

Related to:

- factions;
- diplomacy;
- conflicts.

---

# Historical Events

Related to:

- civilization changes;
- major world events.

---

# Player Choice

Events should allow multiple solutions.

---

# Example

A settlement lacks resources.

Possible responses:

Solution A:

Trade.

Solution B:

Explore new resources.

Solution C:

Relocate.

Solution D:

Ignore the problem.

---

# No Failure State

Ignoring an event is also a choice.

The world continues.

---

# Consequence-Based Design

Actions create outcomes.

---

# Example

Player helps a village.

Future:

- village grows;
- player gains reputation;
- descendants remember.

---

Player ignores village.

Future:

- village declines;
- migration occurs;
- history changes.

---

# Quest Memory

The world remembers completed events.

---

# Example

A player solves a conflict.

Years later:

NPCs remember the resolution.

---

# Repeated Events

Events should evolve.

Avoid:

- identical quests;
- repeated dialogue;
- predictable outcomes.

---

# NPC Motivation

Events are influenced by:

- personality;
- goals;
- relationships;
- culture.

---

# Example

Two NPCs request help.

NPC A:

Wants revenge.

NPC B:

Wants peace.

Same conflict.

Different motivations.

---

# Player Reputation Influence

Reputation affects available situations.

---

# Trusted Player

NPCs may:

- request assistance;
- reveal information.

---

# Unknown Player

NPCs may:

- hesitate;
- refuse interaction.

---

# Negative Reputation

NPCs may:

- avoid;
- oppose;
- spread warnings.

---

# Event Scaling

Events adapt to world state.

Not player level.

---

# Example

A new player may encounter:

A starving family.

A veteran player may encounter:

A collapsing region.

---

# Integration With Other Systems

## NPC Intelligence System

Creates motivations.

---

## Civilization System

Creates large-scale events.

---

## Player Agency System

Allows different responses.

---

## World Continuity System

Preserves consequences.

---

# Developer Notes

Avoid creating checklist gameplay.

The player should feel:

"I discovered something happening."

Not:

"I accepted another task."

---

# Edge Cases

## Player Never Finds An Event

The world continues.

---

## Player Fails To Help

Consequences remain.

---

## Player Creates Unexpected Solution

The world adapts.

---

# Future Expansion

Potential future systems:

- rumor-based discovery;
- procedural storytelling;
- historical event chains;
- world-scale narratives.

---

# Final Statement

The best stories are not given.

They happen.

A child becomes a leader.

A village becomes a city.

A forgotten road becomes a trade route.

A simple decision becomes history.

The Awakened Soul does not follow a story written by developers.

They live through a story created by the world.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of NPC Quest and Event Generation System. |