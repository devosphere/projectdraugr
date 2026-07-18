# Project Draugr

# 04-12 - Decision-Making System

| Field | Value |
|--------|-------|
| Document ID | PD-004.12 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-11 - AI Architecture |
| Related Documents | 02-16 - Observation System, 02-20 - Knowledge System, 02-35 - NPC System, 03-03 - Causality Framework, 04-14 - Goal Planning System |

---

# Purpose

The Decision-Making System defines how autonomous entities transform perception, memory, knowledge, emotions, personality, and goals into concrete decisions.

Where the AI Architecture defines **how cognition is organized**, this document defines **how choices are actually made**.

Every meaningful action within Project Draugr ultimately originates from this system.

---

# Design Philosophy

Decision-making is not selecting the "correct" action.

It is selecting the action that the entity **believes** is best according to its current understanding of reality.

Different entities exposed to the same situation should often make different decisions.

Perfect rationality is neither realistic nor desirable.

Believable reasoning is the objective.

---

# Core Principles

## Subjective Reality

Entities reason using perceived reality rather than objective truth.

---

## Imperfect Information

Unknown information cannot influence decisions.

---

## Dynamic Priorities

Decision priorities continuously evolve as the world changes.

---

## Goal-Driven

Every decision ultimately serves one or more goals.

---

## Explainable

Every decision should be traceable through an observable reasoning chain.

---

# Responsibilities

The Decision-Making System is responsible for:

- evaluating choices;
- prioritizing actions;
- weighing risks;
- balancing competing goals;
- selecting intentions;
- initiating planning.

It is not responsible for:

- movement;
- execution;
- learning;
- memory storage.

---

# Decision Pipeline

Every decision follows the same process.

```text
Perception

↓

Knowledge Retrieval

↓

Situation Analysis

↓

Candidate Generation

↓

Utility Evaluation

↓

Risk Assessment

↓

Priority Resolution

↓

Decision Selection

↓

Goal Planning
```

Each stage narrows the set of possible actions.

---

# Situation Analysis

The entity first evaluates its current circumstances.

Examples:

- health;
- hunger;
- nearby threats;
- available resources;
- weather;
- allies;
- enemies.

This produces the current decision context.

---

# Candidate Generation

Possible actions are generated.

Examples:

```text
Fight

↓

Flee

↓

Hide

↓

Negotiate

↓

Observe

↓

Ignore
```

The entity does not evaluate impossible actions.

Only feasible options become candidates.

---

# Utility Evaluation

Each candidate receives a utility score.

Utility reflects expected benefit.

Factors may include:

- survival;
- reward;
- effort;
- time;
- confidence;
- relationships;
- long-term consequences.

Higher utility does not guarantee selection.

---

# Risk Assessment

Potential danger is evaluated independently.

Examples:

- probability of injury;
- starvation;
- social consequences;
- resource loss;
- political instability.

High utility actions may still be rejected due to excessive risk.

---

# Opportunity Assessment

The system also evaluates positive opportunities.

Examples:

- profitable trade;
- rare resources;
- alliances;
- discoveries;
- technological advancement.

Opportunities compete alongside immediate needs.

---

# Priority Resolution

Multiple needs compete simultaneously.

Example:

```text
Hungry

vs

Tired

vs

Protect Child

vs

Escape Fire
```

The system resolves conflicts according to current circumstances.

Priorities are dynamic.

---

# Decision Factors

Decision scoring may consider:

- physical condition;
- emotional state;
- personality;
- memories;
- skills;
- available tools;
- social obligations;
- environmental conditions;
- cultural norms;
- current goals.

No single factor should dominate universally.

---

# Personality Influence

Personality modifies evaluation.

Example:

Situation:

```text
Unknown Cave
```

Curious Explorer:

> Enter.

Cautious Merchant:

> Avoid.

Experienced Hunter:

> Observe first.

The world is identical.

The decisions differ.

---

# Emotional Influence

Emotions bias utility evaluation.

Examples:

Fear:

- increases perceived danger.

Anger:

- reduces caution.

Curiosity:

- increases exploration value.

Grief:

- alters social priorities.

Emotions influence reasoning without replacing it.

---

# Memory Influence

Past experiences affect future choices.

Example:

```text
Village A

↓

Was Attacked Previously

↓

Future Visits Become Less Likely
```

Memory changes decision weights over time.

---

# Knowledge Confidence

Not all knowledge is equally reliable.

Confidence levels may include:

- certain;
- probable;
- uncertain;
- rumor;
- speculation.

Entities make decisions despite uncertainty.

---

# Uncertainty

The system frequently operates with incomplete information.

Examples:

- unseen enemies;
- unknown terrain;
- unreliable rumors;
- hidden diseases.

Reasonable assumptions replace perfect knowledge.

---

# Social Influence

Other entities affect decision-making.

Examples:

- trusted advisors;
- respected leaders;
- family;
- enemies;
- crowds.

Social influence modifies but does not completely override autonomy.

---

# Long-Term Reasoning

Entities evaluate future consequences.

Examples:

- preserve food;
- save money;
- invest in education;
- avoid unnecessary wars;
- establish alliances.

Immediate rewards may be sacrificed for future benefit.

---

# Short-Term Reasoning

Immediate survival may override long-term plans.

Example:

```text
Building Library

↓

Fire Starts

↓

Escape Fire
```

Urgency dynamically interrupts ongoing decisions.

---

# Decision Stability

Entities should avoid constant indecision.

Small environmental fluctuations should not repeatedly reverse decisions.

Commitment thresholds reduce oscillation.

---

# Re-Evaluation

Active decisions are periodically re-evaluated.

Triggers include:

- new information;
- danger;
- opportunity;
- changing goals;
- interrupted plans.

Re-evaluation frequency adapts to simulation complexity.

---

# Decision Failure

Poor decisions are expected.

Examples:

- trusting the wrong person;
- underestimating danger;
- choosing an unsafe route;
- investing in failing trade.

Mistakes produce emergent stories.

Perfect decision-making is undesirable.

---

# Collective Decisions

Groups may make shared decisions.

Examples:

- village councils;
- military leadership;
- merchant guilds;
- families.

Collective reasoning follows similar principles while considering multiple participants.

---

# Deterministic Simulation

Given identical:

- knowledge;
- memories;
- personalities;
- emotions;
- world state;
- random seed,

the same decision should always occur.

Determinism supports replay and debugging.

---

# Debug Support

Development tools should expose:

- candidate actions;
- utility scores;
- rejected options;
- reasoning chain;
- emotional modifiers;
- personality modifiers;
- confidence values;
- final decision.

Developers should be able to answer:

> "Why did this entity choose this action?"

---

# Integration with Goal Planning

The Decision-Making System selects:

> **What should I do?**

The Goal Planning System determines:

> **How should I accomplish it?**

Decision and planning remain independent systems.

---

# Integration with AI Architecture

Decision-making occupies the central reasoning layer of the unified cognitive model.

It receives information from perception, memory, and knowledge.

It produces intentions for planning and execution.

---

# Performance Considerations

The system should optimize:

- incremental evaluation;
- utility caching;
- parallel reasoning;
- adaptive update frequency;
- bounded candidate generation.

Expensive reasoning should be reserved for complex entities.

---

# Developer Notes

The Decision-Making System is the heart of believable intelligence.

Players should never feel that NPCs are following scripts.

Instead, they should appear to be making understandable—even if imperfect—choices based upon their own experiences, beliefs, and personalities.

Believability emerges when players can explain an NPC's decision from that NPC's perspective.

---

# Future Expansion

Future versions may introduce:

- moral reasoning;
- ideological belief systems;
- political strategy evaluation;
- economic forecasting;
- deception planning;
- probabilistic reasoning;
- metacognitive self-evaluation;
- collective intelligence optimization.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Decision-Making System architecture. |