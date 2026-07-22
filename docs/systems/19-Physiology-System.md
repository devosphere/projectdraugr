# 19 – Physiology System

> **Project Draugr**
>
> *The body follows reality, not gameplay.*

---

# Purpose

This document defines the biological simulation system of Project Draugr.

The Physiology System represents the internal processes that maintain a living Chronicle.

It governs:

- survival
- biological needs
- physical degradation
- recovery
- environmental response
- death conditions

The system exists to simulate the human body as a biological entity inside a persistent world.

---

# Core Philosophy

The Physiology System follows one immutable principle:

> **The body reacts to reality. It does not obey convenience.**

The system does not:

- protect the player from consequences
- adjust difficulty dynamically
- restore conditions automatically
- prioritize entertainment

Every biological change requires:

- time
- physical cause
- environmental interaction
- appropriate response

---

# Authority

The Physiology System is the authority for:

- biological calculations
- survival thresholds
- bodily deterioration
- recovery rules
- death triggers

The Body HUD displays information from:

```
00-Character State.md
```

The Body HUD is not the source of truth.

---

# Physiology Update Pipeline

Every action follows:

```
Player Action

↓

World Processing

↓

Physical Consequences

↓

Biological Calculation

↓

Update 00-Character State.md

↓

Display Body HUD
```

No biological state may change without processing.

---

# Biological Systems

The MVP includes:

- Health
- Condition
- Hunger
- Thirst
- Energy
- Temperature
- Wetness
- Bladder
- Bowel
- Hygiene

Future systems may expand:

- Sleep quality
- Pain
- Stress
- Mental state
- Disease progression
- Physical adaptation

---

# Health System

Health represents the overall survivability of the Chronicle.

Health is affected by:

- injuries
- blood loss
- illness
- poisoning
- trauma
- environmental damage
- untreated conditions

---

## Health States

Examples:

```
Healthy

Injured

Wounded

Critical

Dying
```

Health does not use numerical values.

---

# Condition System

Condition represents specific abnormalities affecting the Chronicle.

Examples:

```
Minor Cut

Bruised Rib

Broken Arm

Food Poisoning

Fever

Infection

Hypothermia

Venomous Bite
```

Multiple conditions may exist simultaneously.

Example:

```
Condition:

Broken Arm

Infected Wound

High Fever
```

---

# Hunger System

Hunger represents nutritional depletion.

Food provides:

- energy
- recovery support
- biological maintenance

---

## Hunger States

```
Full

Satisfied

Hungry

Very Hungry

Starving

Critical Starvation
```

---

# Food Survival Rule

Food deprivation uses a fixed survival threshold.

## MVP Constant

**Maximum survival without food:** **721 hours**

Equivalent to:

- 30 days + 1 hour

After reaching:

> **721 hours + 1 second**

the Chronicle dies due to:

```
Critical Starvation
```

---

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

# Thirst System

Thirst represents hydration.

Water is one of the most critical survival requirements.

Thirst is affected by:

- time
- physical activity
- heat
- sweating
- illness
- dehydration

---

# Water Survival Rule

Water deprivation uses a fixed survival threshold.

## MVP Constant

**Maximum survival without water:** **73 hours**

After reaching:

> **73 hours + 1 second**

the Chronicle dies due to:

```
Critical Dehydration
```

---

## Dehydration Progression

| Elapsed Time | State |
|--------------|-------|
| 0 hours | Hydrated |
| 24 hours | Thirsty |
| 48 hours | Dehydrated |
| 72 hours | Critical Dehydration |
| 73 hours + 1 second | Death |

---

# Water Interaction Rules

Water sources do not automatically affect hydration.

Invalid:

```
Near river → Hydrated
```

Valid:

```
Drink river water

↓

Hydration changes
```

The Chronicle must intentionally perform the action.

---

# Energy System

Energy represents immediate physical capability.

Energy decreases from:

- walking
- running
- climbing
- gathering
- crafting
- construction
- combat
- training

---

## Energy States

```
Energetic

Rested

Tired

Fatigued

Exhausted

Collapsing
```

---

# Energy Recovery

Energy improves through:

- resting
- sitting
- sleeping
- reduced activity

Energy does not instantly recover.

---

# Temperature System

Temperature represents thermal condition.

The body attempts to maintain safe internal temperature.

Temperature is affected by:

- weather
- clothing
- shelter
- fire
- wind
- water exposure
- wetness

---

# Temperature States

```
Cold

Comfortable

Warm

Hot

Hypothermic

Hyperthermic
```

---

# Temperature Death Trigger

Normal core temperature:

```
36.5°C – 37.5°C
```

---

## Heat Exposure

Risk:

```
Above 38.5°C

Heat Stress
```

```
Above 40°C

Heat Stroke Risk
```

```
Above 42°C

Potentially Fatal
```

Affected by:

- hydration
- shade
- clothing
- activity

---

## Cold Exposure

Risk:

```
Below 35°C

Hypothermia
```

```
32°C

Moderate Danger
```

```
28°C

Severe Danger
```

```
Below 28°C

High Mortality Risk
```

Affected by:

- wetness
- wind
- clothing
- shelter
- fire

---

# Wetness System

Wetness represents water exposure affecting:

- body
- clothing
- equipment

---

## Wetness States

```
Dry

Damp

Wet

Soaked
```

---

# Wetness Sources

Examples:

- rain
- rivers
- lakes
- swimming
- storms
- falling into water

---

# Wetness Effects

Wetness influences:

- temperature loss
- comfort
- drying time
- disease risk

Wet clothing increases cold exposure.

---

# Bladder System

Bladder represents urinary accumulation.

It is a biological pressure system.

---

# Bladder States

```
Empty

Comfortable

Need to Urinate

Urgent

Critical
```

---

# Bladder Increase

Bladder increases through:

- drinking
- eating
- digestion
- time

---

# Bladder Reduction

Bladder decreases only through:

```
Urination
```

Examples:

Valid:

```
Find suitable location

↓

Urinate

↓

Update Bladder State
```

Invalid:

```
Near water source → bladder decreases
```

---

# Bladder Threshold Consequences

Ignoring bladder progression causes:

## Comfortable

No effect.

---

## Need to Urinate

Minor distraction.

---

## Urgent

Effects:

- reduced comfort
- difficulty concentrating
- increased mental pressure

---

## Critical

Possible consequences:

- involuntary loss of control
- hygiene reduction
- clothing contamination
- discomfort
- social consequences (future)

---

# Bladder Death Rule

Bladder is not directly fatal.

Extreme neglect may cause:

- urinary complications
- infection risk
- health penalties

Death occurs only through secondary complications.

---

# Bowel System

Bowel represents digestive waste accumulation.

Bladder and bowel are separate systems.

---

# Bowel Increase

Bowel increases through:

- eating
- digestion
- time

Food affects bowel behavior.

Examples:

High fiber:

```
Faster bowel movement
```

Large meals:

```
Higher accumulation
```

---

# Bowel States

```
Empty

Normal

Need Relief

Urgent

Critical
```

---

# Bowel Reduction

Bowel decreases only through:

```
Defecation
```

---

# Defecation Rules

Defecation requires:

```
Find suitable location

↓

Defecate

↓

Dispose waste appropriately

↓

Update Bowel State
```

---

# Sanitation Rules

Preferred locations:

- away from camp
- away from water sources
- away from food preparation
- away from living areas

Improper waste disposal increases:

- hygiene penalties
- contamination risk
- disease risk
- pest attraction

---

# Bowel Death Rule

Bowel is not directly fatal.

Extreme neglect may cause:

- severe discomfort
- digestive complications
- infection risk

Death occurs only through secondary complications.

---

# Hygiene System

Hygiene represents bodily cleanliness.

It affects:

- comfort
- contamination risk
- disease probability
- social interaction (future)

---

# Hygiene States

```
Clean

Normal

Dirty

Filthy

Hazardous
```

---

# Hygiene Reduction

Hygiene decreases through:

- sweating
- heavy labor
- mud exposure
- blood
- animal handling
- fish processing
- smoke exposure
- time

---

# Hygiene Recovery

Hygiene improves through:

- washing
- bathing
- cleaning clothing
- cleaning equipment

Invalid:

```
Time passed → Clean
```

Valid:

```
Wash at stream → Hygiene improves
```

---

# Hygiene Death Rule

Poor hygiene alone does not kill.

However:

```
Poor Hygiene

+

Open Wound

+

Contamination

+

No Treatment

=

High Infection Risk
```

---

# Recovery System

Recovery requires:

- time
- rest
- nutrition
- appropriate treatment
- safe environment

The body does not instantly recover.

---

# Exhaustion Death Trigger

Fatigue alone is not immediately fatal.

Fatal combinations may include:

```
Extreme Exhaustion

+

Exposure

+

Injury

+

No Rest

=

Possible Death
```

---

# Disease Progression

Disease follows biological progression.

Example:

```
Exposure

↓

Infection

↓

Severe Infection

↓

Systemic Infection

↓

Sepsis

↓

Death
```

Risk increases through:

- poor hygiene
- contaminated water
- untreated wounds
- malnutrition

---

# Blood Loss Death Trigger

Average blood volume:

Approximately:

```
7% of body weight
```

---

## Blood Loss States

```
Less than 15%

Minor
```

```
15–30%

Moderate
```

```
30–40%

Severe
```

```
Above 40%

Critical
```

```
Above 50% without intervention

High probability of death
```

---

# Trauma Death Trigger

Immediate death may occur from:

- catastrophic brain injury
- destroyed vital organs
- massive crushing injuries
- fatal structural damage

---

# Death Evaluation Priority

When multiple failures exist:

Evaluate in order:

1. Catastrophic Trauma
2. Massive Blood Loss
3. Temperature Failure
4. Critical Dehydration
5. Severe Disease
6. Critical Starvation
7. Environmental Hazard
8. Secondary Complications

---

# Death Record Procedure

When death occurs update:

```
08-History.md
```

Record:

```
Chronicle Death Record

Chronicle:

Date:

Location:

Cause:

Final Condition:

Last Known Action:

Environmental Conditions:
```

---

# Final Principle

The Physiology System exists to simulate the reality of being alive.

The Body HUD reveals the body's condition.

The Physiology System determines the consequences.

The player chooses actions.

The body responds.

The world does not forgive.

The world does not punish.

The world simply continues.