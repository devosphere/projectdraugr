# Project Draugr

# 04-14 - Goal Planning System

| Field | Value |
|--------|-------|
| Document ID | PD-004.14 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-11 - AI Architecture, 04-12 - Decision-Making System, 04-13 - Memory System |
| Related Documents | 02-17 - Player Action System, 02-20 - Knowledge System, 02-35 - NPC System, 03-03 - Causality Framework |

---

# Purpose

The Goal Planning System defines how autonomous entities transform intentions into executable plans.

Where the Decision-Making System determines **what an entity wants to accomplish**, the Goal Planning System determines **how it intends to accomplish it**.

Planning bridges thought and action.

It enables intelligent behavior that extends beyond immediate reactions into coordinated, long-term strategies.

---

# Design Philosophy

A goal is not an action.

A goal is a desired future state.

Planning is the process of transforming the current world into that desired state through a sequence of achievable steps.

Plans are living structures.

They adapt as reality changes.

---

# Core Principles

## Goals Drive Planning

Every plan originates from an active goal.

---

## Hierarchical Decomposition

Complex goals are broken into smaller achievable objectives.

---

## Adaptive

Plans evolve when circumstances change.

---

## Resource-Aware

Planning considers available resources and capabilities.

---

## Interruptible

Plans may pause, resume, or terminate without breaking simulation consistency.

---

# Responsibilities

The Goal Planning System is responsible for:

- plan generation;
- task decomposition;
- prerequisite analysis;
- dependency resolution;
- execution sequencing;
- contingency planning;
- plan revision.

It is not responsible for:

- selecting goals;
- executing actions;
- movement;
- learning.

---

# Planning Pipeline

Every plan follows the same structure.

```text
Goal

↓

Situation Analysis

↓

Prerequisite Identification

↓

Task Generation

↓

Task Ordering

↓

Execution Plan

↓

Monitoring

↓

Completion or Revision
```

Planning converts abstract intentions into actionable sequences.

---

# Goal Hierarchy

Goals exist at multiple levels.

## Immediate Goals

Examples:

- drink water;
- eat food;
- escape danger.

---

## Short-Term Goals

Examples:

- build a campfire;
- sell goods;
- repair equipment.

---

## Long-Term Goals

Examples:

- master swordsmanship;
- found a settlement;
- become king.

---

## Lifetime Goals

Examples:

- avenge a family member;
- discover ancient knowledge;
- establish a lasting legacy.

Goals may persist for decades of simulation time.

---

# Goal Decomposition

Large goals become smaller objectives.

Example:

```text
Build House

↓

Gather Wood

↓

Gather Stone

↓

Craft Tools

↓

Prepare Foundation

↓

Construct Walls

↓

Build Roof

↓

Furnish Interior
```

Each subgoal may generate additional plans.

---

# Prerequisite Analysis

Planning identifies missing requirements.

Example:

Goal:

```text
Forge Iron Sword
```

Prerequisites:

- iron ore;
- furnace;
- hammer;
- blacksmith skill;
- fuel.

Missing prerequisites generate new subgoals.

---

# Task Dependencies

Tasks may depend upon one another.

Example:

```text
Mine Ore

↓

Smelt Iron

↓

Forge Blade

↓

Sharpen Blade
```

Dependencies determine execution order.

---

# Resource Allocation

Planning evaluates available resources.

Examples:

- food;
- tools;
- money;
- labor;
- materials;
- time.

Insufficient resources generate acquisition plans.

---

# Capability Evaluation

Entities evaluate their own abilities.

Examples:

- skills;
- knowledge;
- equipment;
- physical condition.

Plans should remain achievable.

---

# Cooperative Planning

Goals may require multiple participants.

Examples:

- construct buildings;
- defend settlements;
- military campaigns;
- large-scale trade.

Responsibilities may be distributed among participants.

---

# Parallel Planning

Independent tasks may execute simultaneously.

Example:

```text
Gather Stone

||

Gather Wood

||

Prepare Site
```

Parallel execution improves efficiency.

---

# Contingency Planning

Plans anticipate failure.

Examples:

If bridge destroyed →

Find alternate crossing.

If harvest fails →

Purchase food.

If ally dies →

Recruit replacement.

Fallback strategies improve resilience.

---

# Dynamic Replanning

Plans are continuously monitored.

Triggers for replanning include:

- environmental changes;
- new knowledge;
- resource loss;
- interruptions;
- completed subgoals;
- emerging opportunities.

Plans adapt rather than restarting unnecessarily.

---

# Plan Monitoring

Execution progress is continuously evaluated.

Possible states:

- pending;
- active;
- blocked;
- paused;
- completed;
- failed.

Monitoring supports long-term behavior.

---

# Plan Interruption

Urgent situations may interrupt execution.

Examples:

- predator attack;
- fire;
- injury;
- political crisis.

Interrupted plans may later resume.

---

# Plan Persistence

Long-term plans survive:

- sleep;
- travel;
- world streaming;
- save/load operations.

Planning contributes to persistent identity.

---

# Shared Planning

Organizations maintain collective plans.

Examples:

- villages;
- guilds;
- kingdoms;
- military units.

Shared planning coordinates large-scale activities.

---

# Strategic Planning

Advanced entities reason across extended time horizons.

Examples:

- infrastructure investment;
- education;
- diplomacy;
- succession planning;
- technological research.

Strategic planning influences civilization evolution.

---

# Opportunistic Planning

Unexpected opportunities may modify active plans.

Examples:

- abandoned supplies;
- favorable weather;
- unexpected trade;
- discovered shortcut.

Plans remain flexible.

---

# Plan Abandonment

Plans may be discarded.

Examples:

- impossible objectives;
- changing priorities;
- catastrophic failure;
- obsolete goals.

Abandonment should be a rational decision rather than an error state.

---

# Integration with Navigation

Planning determines destinations.

The Navigation System determines routes.

Planning answers:

> "Where should I go?"

Navigation answers:

> "How do I get there?"

---

# Integration with Decision-Making

Decision-Making selects the active goal.

Planning transforms that goal into executable tasks.

These systems remain distinct.

---

# Integration with Memory

Previous experiences influence planning.

Examples:

- successful strategies;
- failed attempts;
- trusted locations;
- reliable allies.

Memory improves future plans without guaranteeing success.

---

# Debug Support

Development tools should visualize:

- active goals;
- task hierarchy;
- dependencies;
- blocked tasks;
- execution order;
- contingency branches;
- plan revisions.

Developers should be able to inspect complete planning chains.

---

# Performance Considerations

The Goal Planning System should optimize:

- incremental planning;
- reusable task graphs;
- partial replanning;
- adaptive planning depth;
- hierarchical decomposition.

Complex planning should be reserved for cognitively advanced entities.

---

# Developer Notes

Planning transforms intelligence into purposeful behavior.

Without planning, AI becomes reactive.

With planning, AI develops ambitions.

Players should observe NPCs pursuing projects that span days, years, or even generations.

The planning system should create the impression that every inhabitant has somewhere they are trying to reach—not merely somewhere they are currently walking.

---

# Future Expansion

Future versions may introduce:

- collaborative multi-agent planning;
- economic optimization;
- political strategy;
- military logistics;
- scientific research planning;
- intergenerational planning;
- civilization-wide objective coordination;
- predictive scenario simulation.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Goal Planning System architecture. |