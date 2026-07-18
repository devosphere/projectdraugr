# Project Draugr

# 02-22 - Creature Behavior System

| Field | Value |
|--------|-------|
| Document ID | PD-002.22 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Life and Creatures System |
| Depends On | 02-21 - Life Forms, 02-15 - Ecosystem |
| Related Systems | Creature AI, Ecosystem System, Survival System, Corruption System |

---

# Purpose

This document defines how creatures behave within Project Draugr.

It establishes the rules governing:

- creature decisions;
- survival behavior;
- interactions;
- adaptation;
- responses to the environment.

The purpose of this system is to create creatures that behave as living beings rather than gameplay objects.

---

# Design Philosophy

Creatures should have lives.

They should:

- search for food;
- protect themselves;
- avoid danger;
- compete;
- reproduce;
- adapt.

The Awakened Soul encounters creatures.

They do not activate them.

---

# Core Principle

## Creatures Follow Survival Logic.

A creature's behavior is determined by:

```
Needs

+

Instinct

+

Environment

+

Experience

+

Species Traits
```

---

# Creature Behavior Model

Every creature operates through internal priorities.

Priority order:

```
Survival

↓

Safety

↓

Resources

↓

Territory

↓

Reproduction

↓

Social Behavior
```

Different species may modify this order.

---

# Basic Needs

Creatures have requirements.

Examples:

- food;
- water;
- shelter;
- rest;
- safety.

Failure to satisfy needs changes behavior.

---

# Hunger Behavior

A hungry creature may:

- hunt;
- search;
- migrate;
- become more aggressive.

A normally passive creature may become dangerous when starving.

---

# Fear Behavior

Creatures evaluate threats.

Threats include:

- predators;
- unknown entities;
- environmental danger;
- corrupted areas;
- the Awakened Soul.

---

# Fear Responses

Possible responses:

## Escape

The creature attempts to flee.

---

## Hide

The creature avoids detection.

---

## Defend

The creature protects itself or its territory.

---

## Attack

The creature fights when necessary.

---

# Territorial Behavior

Some creatures establish territories.

Territory behavior includes:

- marking areas;
- defending resources;
- avoiding competitors.

---

# Hunting Behavior

Predators use different hunting strategies.

Examples:

## Ambush Predators

Use:

- stealth;
- patience;
- environmental advantage.

---

## Pursuit Predators

Use:

- speed;
- endurance;
- tracking.

---

## Pack Predators

Use:

- cooperation;
- communication;
- coordination.

---

# Prey Behavior

Prey creatures are not passive.

They may:

- detect threats;
- warn others;
- flee;
- hide;
- adapt routes.

---

# Social Behavior

Some creatures form groups.

Groups may provide:

- protection;
- hunting advantages;
- reproduction benefits.

---

# Creature Communication

Communication may include:

- sounds;
- movements;
- signals;
- chemical markers.

Different species communicate differently.

---

# Migration System

Creatures may move based on:

- seasons;
- resources;
- environmental changes;
- corruption.

Migration affects:

- ecosystems;
- hunting opportunities;
- civilization resources.

---

# Reproduction Behavior

Creatures reproduce according to survival conditions.

Factors include:

- available resources;
- population;
- safety.

---

# Aging Behavior

Creatures change over time.

Effects may include:

- reduced strength;
- changed behavior;
- experience-based adaptation.

---

# Learning System

Some creatures can learn.

Learning may include:

- avoiding dangerous locations;
- recognizing threats;
- adapting hunting methods.

---

# Creature Memory

Advanced creatures may remember:

- player interactions;
- previous encounters;
- environmental changes.

Example:

A creature repeatedly hunted by the Awakened Soul may become:

- more cautious;
- more aggressive;
- avoidant.

---

# Corrupted Creature Behavior

Corruption affects behavior differently.

Possible effects:

- increased aggression;
- altered instincts;
- new survival methods;
- abnormal patterns.

---

# Corruption Does Not Equal Evil

A corrupted creature may simply behave differently.

Example:

A corrupted predator may hunt differently because its biology changed.

---

# Intelligent Creature Behavior

Some creatures may possess advanced intelligence.

They may:

- form societies;
- communicate;
- create tools;
- develop cultures.

---

# Player Interaction

The Awakened Soul affects creature behavior.

Examples:

Repeated hunting:

May reduce population.

---

Ignoring creatures:

May allow populations to increase.

---

Protecting species:

May restore ecosystems.

---

# Offline Creature Simulation

Creatures continue living while the player is offline.

Possible changes:

- migration;
- population changes;
- territory changes;
- predator activity.

---

# Creature Encounters

Encounters should emerge naturally.

Examples:

The player finds:

- animal tracks;
- remains;
- migration paths;
- hunting signs.

Not every encounter begins with combat.

---

# Creature Spawning Philosophy

Creatures should not simply appear.

They should exist because:

- they live there;
- they traveled there;
- they followed resources.

---

# Integration With Other Systems

## Ecosystem System

Defines relationships between life forms.

---

## Weather System

Influences behavior.

---

## Corruption System

Changes biological patterns.

---

## Survival System

Defines player interactions.

---

# Developer Notes

Avoid predictable creature behavior.

Creatures should sometimes surprise players because they are following their own logic.

---

# Edge Cases

## Player Camps Near Wildlife

Animals should react naturally.

Possible outcomes:

- avoidance;
- curiosity;
- attacks;
- migration.

---

## Overhunting

Should affect local ecosystems.

---

## Rare Creatures

Should not appear only for collection purposes.

---

# Future Expansion

Potential future systems:

- advanced creature AI;
- creature personalities;
- animal domestication;
- intelligent species AI;
- ecosystem simulation.

---

# Final Statement

Creatures are not enemies waiting for the Awakened Soul.

They are lives sharing the same world.

They hunt.

They fear.

They adapt.

They survive.

The Awakened Soul is not the author of their existence.

They are simply another creature trying to survive in the Chronicle.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of the Creature Behavior System. |