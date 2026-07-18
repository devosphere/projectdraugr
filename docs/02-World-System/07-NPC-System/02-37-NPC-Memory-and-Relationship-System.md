# Project Draugr

# 02-37 - NPC Memory and Relationship System

| Field | Value |
|--------|-------|
| Document ID | PD-002.37 |
| Version | 1.0.0 |
| Status | Approved |
| Category | NPC System |
| Depends On | 02-31 - Player Reputation and Relationships, 02-36 - NPC Life and Behavior System |
| Related Systems | Memory System, Civilization System, Chronicle System, Social System |

---

# Purpose

This document defines how NPCs remember experiences and form relationships.

It establishes:

- personal memories;
- emotional connections;
- trust;
- loyalty;
- conflicts;
- social bonds.

---

# Design Philosophy

A world becomes meaningful when actions are remembered.

---

# Core Principle

## People Remember How You Changed Their Lives.

Not every interaction becomes history.

But meaningful interactions leave marks.

---

# NPC Memory Definition

NPC memory represents stored experiences that influence future behavior.

---

# Memory Categories

NPC memories are divided into:

```
Personal Memory

+

Social Memory

+

Historical Memory
```

---

# Personal Memory

Individual experiences.

Examples:

- meeting the player;
- receiving help;
- suffering betrayal.

---

# Social Memory

Shared memories within groups.

Examples:

- village events;
- battles;
- disasters.

---

# Historical Memory

Long-term records.

Examples:

- legends;
- discoveries;
- important figures.

---

# Memory Importance

Not every event has equal impact.

Memory strength depends on:

```
Emotional Impact

+

Personal Importance

+

Event Severity

+

Repetition
```

---

# Minor Memories

Examples:

- short conversations;
- small trades;
- ordinary encounters.

May fade over time.

---

# Significant Memories

Examples:

- rescue;
- betrayal;
- major assistance.

Remain longer.

---

# Life-Changing Memories

Examples:

- saving someone's life;
- destroying their home;
- changing their future.

May remain permanently.

---

# NPC Relationship System

Relationships represent connections between individuals.

---

# Relationship Factors

Relationships are influenced by:

- memories;
- personality;
- shared experiences;
- trust;
- communication.

---

# Relationship States

Relationships are not fixed.

They evolve.

---

# Stranger

Initial state.

The NPC has limited information.

---

# Familiar

The NPC recognizes the individual.

---

# Acquaintance

The NPC has repeated interactions.

---

# Friend

Created through:

- trust;
- support;
- shared experiences.

---

# Trusted Ally

Created through:

- loyalty;
- reliability;
- significant cooperation.

---

# Family-Level Bond

Created through:

- deep emotional connection;
- long-term interaction.

---

# Negative Relationships

---

# Suspicious

The NPC has concerns.

---

# Disliked

The NPC has negative feelings.

---

# Enemy

Created through:

- severe conflict;
- betrayal;
- harm.

---

# Personal Rival

Created through:

- competition;
- opposing goals;
- repeated conflict.

---

# Trust System

Trust represents confidence in another individual's intentions.

---

# Building Trust

Trust grows through:

- honesty;
- consistency;
- assistance;
- keeping promises.

---

# Losing Trust

Trust decreases through:

- lies;
- betrayal;
- abandonment;
- harmful actions.

---

# Trust Recovery

Broken trust may recover.

However:

The process requires:

- time;
- effort;
- meaningful actions.

---

# Forgiveness System

Forgiveness depends on:

- personality;
- severity of action;
- relationship history.

---

# Example

A friend may forgive:

A forgotten promise.

But may never forgive:

A deliberate betrayal.

---

# Memory Sharing

NPCs share information.

Methods:

- conversation;
- trade;
- travel;
- storytelling.

---

# Rumor Propagation

Information spreads through social networks.

However:

Information may change.

---

# Example

Original Event:

"The Awakened Soul saved a village."

Later versions:

"The mysterious stranger defeated an entire army."

---

# NPC Bias

NPC memories are personal.

Two NPCs may remember the same event differently.

---

# Example

A player destroys a corrupted forest.

NPC A:

"The hero saved us."

NPC B:

"The stranger destroyed sacred land."

---

# Relationship With Previous Chronicles

NPCs may remember previous Awakened Souls.

---

# Example

A future Chronicle enters a village.

An elder says:

"My grandfather told stories about someone like you."

---

# Legacy Memories

Previous Chronicles may leave:

- journals;
- monuments;
- descendants;
- traditions.

---

# NPC Aging And Memory

NPC memory changes with time.

Possible effects:

- forgetting details;
- preserving important emotions;
- passing stories to others.

---

# Family Memory

Families preserve important events.

Examples:

- ancestry;
- achievements;
- tragedies.

---

# NPC Death And Memory

When NPCs die:

Their memories may continue through:

- descendants;
- documents;
- culture.

---

# Player Relationship Impact

The player's relationships may influence:

- reputation;
- access;
- opportunities;
- historical perception.

---

# Integration With Other Systems

## Player Reputation System

Defines external perception.

---

## Civilization System

Defines collective memory.

---

## Chronicle System

Preserves historical events.

---

## NPC Life System

Defines individual existence.

---

# Developer Notes

Avoid making NPC relationships feel like numerical meters.

Numbers may exist internally.

The player should experience:

- emotions;
- reactions;
- consequences.

---

# Edge Cases

## NPC Forgets Player

Possible for minor interactions.

---

## NPC Remembers Player After Years

Possible for meaningful events.

---

## NPC Receives False Information

Their opinion may change based on inaccurate knowledge.

---

# Future Expansion

Potential future systems:

- family inheritance;
- NPC journals;
- personal diaries;
- emotional trauma;
- lifelong friendships.

---

# Final Statement

The world remembers through people.

A structure may collapse.

A kingdom may disappear.

A generation may pass.

But stories survive.

The Awakened Soul does not only leave footprints on the land.

They leave memories in the hearts of those who witnessed them.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of NPC Memory and Relationship System. |