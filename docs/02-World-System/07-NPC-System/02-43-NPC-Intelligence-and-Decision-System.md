# Project Draugr

# 02-43 - NPC Intelligence and Decision System

| Field | Value |
|--------|-------|
| Document ID | PD-002.43 |
| Version | 1.0.0 |
| Status | Approved |
| Category | NPC System |
| Depends On | 02-36 - NPC Life and Behavior System, 02-37 - NPC Memory and Relationship System |
| Related Systems | AI Behavior System, World Simulation System, Civilization System |

---

# Purpose

This document defines how NPCs process information, make decisions, and adapt to situations.

It establishes:

- NPC reasoning;
- priorities;
- decision-making;
- learning;
- adaptation;
- limitations.

---

# Design Philosophy

NPCs should appear intelligent without becoming unrealistic.

---

# Core Principle

## NPCs Make Decisions Based On What They Know, Not What The Player Knows.

---

# NPC Intelligence Model

NPC decisions are influenced by:

```
Needs

+

Knowledge

+

Memory

+

Personality

+

Goals

+

Environment
```

---

# Information Limitation

NPCs do not have complete world knowledge.

They only know:

- personal experiences;
- learned information;
- rumors;
- observations.

---

# Example

The player knows:

"A dangerous creature lives beyond the forest."

An NPC may only know:

"Travelers have disappeared there."

---

# Decision Process

NPC decisions follow:

```
Observe

↓

Evaluate

↓

Consider Options

↓

Choose Action

↓

Experience Result

↓

Update Memory
```

---

# Observation

NPCs gather information through:

- senses;
- conversations;
- experience;
- reports.

---

# Evaluation

NPCs compare situations against:

- needs;
- risks;
- beliefs;
- goals.

---

# Option Generation

NPCs consider possible actions.

Example:

A farmer sees poor harvest.

Possible choices:

- seek help;
- change crops;
- trade for food;
- leave area.

---

# Decision Selection

NPCs select actions based on priorities.

---

# Priority System

NPC priorities generally follow:

```
Survival

↓

Security

↓

Needs

↓

Relationships

↓

Goals

↓

Ambitions
```

---

# Survival Decisions

When threatened, NPCs prioritize:

- escaping;
- defending;
- finding safety.

---

# Social Decisions

NPCs consider:

- relationships;
- reputation;
- obligations.

---

# Economic Decisions

NPCs consider:

- resources;
- opportunity;
- risk.

---

# Personality Influence

Personality affects decisions.

---

# Example

Two NPCs encounter danger.

Brave NPC:

May investigate.

Cautious NPC:

May avoid the area.

---

# Emotional Influence

Emotions affect reasoning.

Examples:

Fear:

May increase caution.

Anger:

May increase aggression.

Grief:

May affect motivation.

---

# Goal System

NPCs have:

- short-term goals;
- long-term goals.

---

# Short-Term Goals

Examples:

- obtain food;
- complete work;
- repair equipment.

---

# Long-Term Goals

Examples:

- improve social status;
- build wealth;
- protect family.

---

# Personal Ambition

Some NPCs seek:

- leadership;
- discovery;
- recognition;
- knowledge.

---

# Learning System

NPCs improve through experience.

---

# Learning Sources

Examples:

- success;
- failure;
- observation;
- teaching.

---

# Adaptive Behavior

NPCs adjust when circumstances change.

---

# Example

A merchant loses a trade route.

Possible adaptation:

- finds another route;
- changes goods;
- stops traveling.

---

# Mistake System

NPCs can make incorrect decisions.

Reasons:

- incomplete information;
- emotional state;
- personality;
- poor judgment.

---

# Why Mistakes Matter

Perfect NPCs feel artificial.

Mistakes create:

- stories;
- consequences;
- unpredictability.

---

# Social Intelligence

NPCs evaluate others.

Factors:

- reputation;
- memories;
- relationships;
- cultural values.

---

# Trust Evaluation

NPCs ask:

"Can this person be trusted?"

Based on:

- past actions;
- reputation;
- behavior.

---

# Group Decision Making

Communities make collective decisions.

Influenced by:

- leaders;
- members;
- resources;
- culture.

---

# Example

A village faces a threat.

Possible responses:

- fight;
- flee;
- negotiate;
- prepare defenses.

---

# Civilization Intelligence

Large societies make decisions through:

- governments;
- factions;
- institutions.

---

# NPC Communication

NPCs exchange information.

Methods:

- speech;
- writing;
- trade;
- observation.

---

# Information Accuracy

Information quality depends on:

- source;
- distance;
- interpretation.

---

# Rumor-Based Decisions

NPCs may act on incorrect information.

---

# Player Interaction

The Awakened Soul influences NPC decisions through:

- actions;
- information;
- persuasion;
- reputation.

---

# Persuasion System

Influence depends on:

- relationship;
- credibility;
- evidence;
- cultural acceptance.

---

# NPC Autonomy

NPCs may reject player suggestions.

Reasons:

- disagreement;
- fear;
- personal goals;
- cultural beliefs.

---

# Integration With Other Systems

## Memory System

Provides experience.

---

## Relationship System

Provides social context.

---

## Civilization System

Provides large-scale decisions.

---

## World Simulation System

Provides changing conditions.

---

# Developer Notes

Avoid making NPCs behave like quest machines.

NPCs should have reasons.

---

# Edge Cases

## NPC Makes A Bad Decision

The consequence becomes part of history.

---

## NPC Receives False Information

Their behavior changes based on belief.

---

## NPC Changes Opinion

Possible through experience.

---

# Future Expansion

Potential future systems:

- advanced personality models;
- emotional intelligence;
- individual learning paths;
- NPC creativity;
- autonomous invention.

---

# Final Statement

Intelligence is not knowing everything.

Intelligence is making choices with what you know.

Every NPC sees the world differently.

Every decision creates consequences.

The Awakened Soul is not surrounded by actors pretending to live.

They are surrounded by individuals trying to survive in the same universe.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of NPC Intelligence and Decision System. |