# Human Survival Constants (MVP)

## Purpose

Project Draugr MVP uses fixed biological survival constants.

These values represent the baseline survival limits of an average healthy adult human.

The purpose is not to simulate every biological variable.

The purpose is to provide consistent, predictable survival rules that developers can implement accurately.

Future versions may introduce biological variation.

---

# Default Human Baseline

Every transported Chronicle begins with:

## Species

- Human

## Age

- Adult

## Health

- Healthy

## Body Condition

- Normal

## Hydration

- Optimal

## Nutrition

- Optimal

---

# Water Survival Rule

Water deprivation uses a fixed survival threshold.

## MVP Constant

**Maximum survival without water:** **73 hours**

After reaching:

> **73 hours + 1 second**

the Chronicle dies due to **Critical Dehydration**.

## Dehydration Progression

| Elapsed Time | State |
|--------------|-------|
| 0 hours | Hydrated |
| 24 hours | Thirsty |
| 48 hours | Dehydrated |
| 72 hours | Critical Dehydration |
| 73 hours + 1 second | Death |

---

# Food Survival Rule

Food deprivation uses a fixed survival threshold.

## MVP Constant

**Maximum survival without food:** **721 hours**

Equivalent to:

- 30 days + 1 hour

After reaching:

> **721 hours + 1 second**

the Chronicle dies due to **Critical Starvation**.

## Starvation Progression

| Elapsed Time | State |
|--------------|-------|
| 0 hours | Satisfied |
| 72 hours | Hungry |
| 168 hours | Very Hungry |
| 336 hours | Starving |
| 720 hours | Critical Starvation |
| 721 hours + 1 second | Death |

---

# Energy System

Energy represents the Chronicle's immediate physical capability.

Energy is affected by performed actions.

The MVP uses fixed activity categories.

## Rest

### Examples

- Sleeping
- Sitting
- Waiting

### Effect

- Energy Recovery

---

## Light Activity

### Examples

- Walking
- Gathering
- Simple Crafting

### Effect

- Slow Energy Consumption

---

## Heavy Activity

### Examples

- Construction
- Mining
- Hunting
- Combat
- Heavy Carrying

### Effect

- Fast Energy Consumption

---

# Temperature Survival

Temperature uses fixed condition thresholds.

## Cold Progression

1. Comfortable
2. Cold
3. Severely Cold
4. Hypothermia
5. Critical Hypothermia
6. Death

## Heat Progression

1. Comfortable
2. Warm
3. Hot
4. Overheated
5. Critical Overheating
6. Death

Temperature progression depends on:

- Weather
- Clothing
- Shelter
- Water Exposure
- Fire
- Environment

---

# Injury System

The MVP uses predefined injury conditions.

## Example Injuries

- Minor Cut
- Deep Cut
- Bruise
- Broken Bone
- Burn
- Poisoning
- Infection

Each injury contains:

- Severity
- Duration
- Recovery Requirement
- Death Risk

---

# Simulation Rule

The MVP prioritizes deterministic simulation.

Every biological system must define:

- States
- State Transitions
- Thresholds
- Outcomes

Developers must **not** create hidden assumptions.

The rulebook defines all survival behavior explicitly.

---

# Internal Time Representation

Although the user interface may display elapsed time in days (for example, **Day 3**), all biological systems must use continuous time internally.

The backend should track survival using elapsed seconds.

Example internal values:

```text
hydration_seconds
hunger_seconds
energy_seconds
```

Draugr operates as a continuous real-time simulation.

This ensures that:

- Logging out for 10 hours consumes exactly 10 hours of survival time.
- Sleep cycles remain deterministic.
- Offline progression behaves consistently.
- Server downtime can be reconciled accurately.
- Time synchronization remains reliable.
- Weather events affect survival continuously.

Developers should never estimate elapsed survival time.

The simulation rules define the outcome precisely.

---

# Future Expansion

Future versions may introduce:

- Body Composition
- Age Differences
- Genetics
- Metabolism Variation
- Disease Resistance
- Environmental Adaptation
- Permanent Biological Changes

These systems may replace fixed constants only when they improve the simulation without compromising consistency.

---

# Final Rule

During the MVP:

Humans are simulated as average healthy adults with fixed survival constants.

The world provides complexity.

The biology provides consistent rules.

The player's decisions determine survival.