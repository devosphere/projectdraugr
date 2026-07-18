# Project Draugr

# 02-29 - Survival System

| Field | Value |
|--------|-------|
| Document ID | PD-002.29 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Player System |
| Depends On | 02-26 - Awakened Soul, 02-28 - Skill and Progression System |
| Related Systems | World Continuity System, Weather System, Health System, Life Cycle System |

---

# Purpose

This document defines the Survival System within Project Draugr.

It establishes the physical requirements and challenges faced by the Awakened Soul.

The purpose is to create a realistic survival experience where decisions have meaningful consequences.

---

# Design Philosophy

Survival is not a meter management system.

Survival is maintaining a life.

---

# Core Principle

## The Body Has Needs Whether The Player Is Online Or Not.

The Awakened Soul exists continuously.

The world does not stop when the player leaves.

---

# Survival Needs

The player must manage:

```
Hunger

+

Thirst

+

Fatigue

+

Temperature

+

Health

+

Mental Condition
```

---

# Time Continuity

Project Draugr follows the World Continuity System.

The world clock matches real-world time.

A full day cycle represents:

```
24 Real World Hours
```

---

# Offline Survival

The player's character continues existing while offline.

The character remains in the state where they logged out.

---

# Logout Philosophy

Logging out is not teleportation.

It is equivalent to:

- sleeping;
- resting;
- leaving one's body unattended.

---

# Safe Logout

A responsible player prepares before leaving.

Examples:

- secure shelter;
- sufficient food;
- water supply;
- protection from threats;
- appropriate temperature.

---

# Unsafe Logout

A player who disconnects in danger may suffer consequences.

Examples:

Logging out:

- during a storm;
- inside hostile territory;
- without shelter;
- while starving.

---

# Possible Offline Outcomes

The player may return to:

- hunger;
- dehydration;
- injury;
- illness;
- environmental damage;
- death.

---

# Hunger System

Food provides energy required for survival.

Hunger affects:

- stamina;
- recovery;
- physical performance.

---

# Hunger Stages

## Satisfied

Normal performance.

---

## Hungry

Reduced efficiency.

---

## Starving

Severe physical impairment.

---

## Critical Starvation

Possible death.

---

# Thirst System

Water is more critical than food.

Dehydration affects:

- movement;
- concentration;
- endurance.

---

# Thirst Stages

## Hydrated

Normal condition.

---

## Thirsty

Reduced performance.

---

## Dehydrated

Severe impairment.

---

## Critical Dehydration

Possible death.

---

# Fatigue System

The body requires rest.

Fatigue affects:

- reaction speed;
- decision making;
- physical capability.

---

# Sleep System

Sleep restores:

- stamina;
- recovery;
- mental condition.

---

# Sleep Requirements

Sleep quality depends on:

- safety;
- comfort;
- environment.

---

# Poor Sleep

Examples:

Sleeping:

- outdoors;
- in danger;
- exposed to weather.

May reduce recovery.

---

# Temperature System

The environment affects body temperature.

Factors include:

- weather;
- clothing;
- shelter;
- location.

---

# Cold Exposure

Effects:

- reduced movement;
- fatigue;
- health damage.

---

# Heat Exposure

Effects:

- dehydration;
- exhaustion;
- reduced performance.

---

# Clothing Importance

Clothing provides:

- protection;
- temperature regulation;
- environmental adaptation.

---

# Health System

Health represents physical condition.

Health is affected by:

- injuries;
- illness;
- starvation;
- dehydration;
- environmental hazards.

---

# Injury System

Injuries affect capability.

Examples:

- fractures;
- wounds;
- exhaustion;
- infections.

---

# Injury Recovery

Recovery depends on:

- treatment;
- rest;
- nutrition;
- time.

---

# Illness System

Illness may occur through:

- environment;
- exposure;
- infection;
- poor survival conditions.

---

# Mental Condition

The Awakened Soul experiences psychological effects.

Possible conditions:

- stress;
- fear;
- isolation;
- trauma.

---

# Mental Adaptation

Mental condition changes through:

- experiences;
- relationships;
- achievements;
- failures.

---

# Survival Knowledge

The player must learn:

- environment;
- resources;
- dangers.

The world does not provide complete information.

---

# Survival Preparation

Before traveling or logging out, players should consider:

## Location

Is the area safe?

---

## Resources

Do I have enough supplies?

---

## Weather

Will conditions change?

---

## Threats

What exists nearby?

---

# Survival And Skills

Survival improves through:

- experience;
- knowledge;
- preparation.

---

# Survival And Civilization

Civilizations provide:

- shelter;
- food;
- knowledge;
- protection.

---

# Survival And The Chronicle

Every survival decision becomes part of the Chronicle.

Examples:

- surviving the first night;
- discovering shelter;
- escaping danger;
- overcoming hardship.

---

# Death Conditions

The Awakened Soul may die through:

- starvation;
- dehydration;
- illness;
- injury;
- environmental exposure;
- creatures;
- corruption.

---

# Integration With Other Systems

## World Continuity System

Maintains offline progression.

---

## Weather System

Creates environmental challenges.

---

## Skill System

Allows survival improvement.

---

## Life Cycle System

Defines mortality.

---

# Developer Notes

Avoid making survival feel like punishment.

The player should understand:

"Why did this happen?"

Every consequence should be logical.

---

# Edge Cases

## Player Disconnects Unexpectedly

The world still continues.

---

## Player Returns After Long Absence

The character condition reflects elapsed time.

---

## Player Has A Secure Base

Preparation reduces risk.

---

# Future Expansion

Potential future systems:

- diseases;
- medicine;
- advanced shelter mechanics;
- survival professions;
- mental resilience system.

---

# Final Statement

The Awakened Soul does not control time.

They live inside it.

The world continues while they sleep.

The world continues while they are away.

Every logout is a decision.

Every preparation is a survival choice.

Every surviving day becomes part of the Chronicle.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial implementation of the Survival System. |