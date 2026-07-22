# 00-Character-State.md

# Project Draugr — Character State Database

> **The body is the first record of survival.**

---

# Purpose

This document is the authoritative database for the current physical and biological state of the Chronicle.

The Body HUD does not store information.

The Body HUD only displays information from this file.

```
00-Character-State.md

        ↓

18.2-Body-HUD.md

        ↓

Player Awareness
```

---

# Authority

This file controls:

- current health
- physical condition
- biological needs
- injuries
- exposure
- temporary effects
- recovery state

Any system requiring knowledge of the Chronicle's body must reference this file.

---

# Core Rule

The body changes only through:

- actions
- time
- environment
- biological processes
- external events

Nothing improves automatically without a physical cause.

---

# Character Identity

```yaml
Character ID:
CHR-000001

Name:

Age:

Sex:

Height:

Weight:

Body Type:

Origin:
```

---

# Current Status

```yaml
Health:
Healthy

Condition:
Healthy

Hunger:
Satisfied

Thirst:
Hydrated

Energy:
Rested

Temperature:
Comfortable

Wetness:
Dry

Bladder:
Comfortable

Bowel:
Comfortable

Hygiene:
Clean
```

---

# Health System

## Purpose

Health represents the overall survivability of the Chronicle.

Health is a summary state.

It does not replace detailed conditions.

---

## Health States

```
Healthy

↓

Injured

↓

Wounded

↓

Critical

↓

Dying

↓

Dead
```

---

# Health Factors

Health is affected by:

- injuries
- blood loss
- illness
- poisoning
- infection
- temperature failure
- starvation
- dehydration
- environmental hazards

---

# Condition System

## Purpose

Condition records specific physical problems affecting the Chronicle.

Multiple conditions may exist simultaneously.

---

## Condition Examples

```
Minor Cut

Bruised Rib

Broken Arm

Burn

Food Poisoning

Fever

Infection

Hypothermia

Venom Exposure
```

---

## Condition Record Format

Example:

```yaml
Condition:

- Name:
  Minor Cut

  Severity:
  Minor

  Location:
  Left Hand

  Cause:
  Stone Fragment

  Status:
  Open

  Treatment:
  None
```

---

# Hunger System

## Purpose

Represents nutritional availability.

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

## Hunger Changes

Increases through:

- time
- physical activity
- calorie deficit
- illness

Improves through:

- eating food
- consuming nutrition sources

---

# Thirst System

## Purpose

Represents hydration status.

---

## Thirst States

```
Hydrated

Thirsty

Very Thirsty

Dehydrated

Severely Dehydrated

Critical Dehydration
```

---

## Thirst Changes

Increases through:

- time
- heat
- sweating
- physical labor
- illness

Improves through:

- drinking water
- consuming hydration sources

Water sources do not automatically hydrate the Chronicle.

The Chronicle must intentionally drink.

---

# Energy System

## Purpose

Represents immediate physical capability.

Energy is different from Condition.

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

## Energy Decreases From

- walking
- running
- gathering
- crafting
- construction
- combat
- training
- carrying weight

---

## Energy Recovery

Improves through:

- sitting
- resting
- sleeping
- reducing activity

---

# Temperature System

## Purpose

Represents thermal state.

---

## Temperature States

```
Cold

Comfortable

Warm

Hot

Hypothermic

Hyperthermic
```

---

## Temperature Factors

Affected by:

- weather
- clothing
- shelter
- fire
- wind
- water exposure
- wetness

---

# Wetness System

## Purpose

Represents moisture on:

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

## Wetness Sources

Examples:

- rain
- swimming
- rivers
- storms
- falling into water

---

## Wetness Effects

Wetness influences:

- temperature loss
- drying time
- comfort
- disease risk

---

# Bladder System

## Purpose

Represents urinary accumulation.

Humans experience bladder pressure naturally.

The simulation exposes this awareness through the Body HUD.

---

# Bladder States

```
Empty

Comfortable

Need To Urinate

Urgent

Critical
```

---

# Bladder Increase

Bladder increases through:

- drinking water
- consuming liquids
- digestion
- biological time

---

# Bladder Reduction

Bladder decreases only through:

- urination

The following do NOT reduce bladder:

Invalid:

```
Standing near river
```

Invalid:

```
Bathing
```

Valid:

```
The Chronicle urinates beside the forest edge.
```

---

# Maximum Bladder Threshold

When Critical bladder is ignored:

The body eventually loses voluntary control.

Possible consequences:

## Stage 1

```
Critical
```

Effects:

- discomfort
- distraction
- reduced focus

---

## Stage 2

```
Loss of Control Risk
```

Effects:

- involuntary urination may occur
- clothing becomes wet
- hygiene decreases

---

## Stage 3

Repeated neglect may cause:

- skin irritation
- hygiene penalties
- infection risk

Bladder itself is not normally fatal.

Death occurs only through secondary complications.

---

# Bowel System

## Purpose

Represents digestive waste accumulation.

Bowel is separate from bladder.

---

# Bowel States

```
Empty

Comfortable

Need To Defecate

Urgent

Critical
```

---

# Bowel Increase

Affected by:

- eating
- digestion
- time
- food composition

Examples:

High fiber:

- faster bowel activity

Large meals:

- increased accumulation

---

# Bowel Reduction

Only through:

- defecation

---

# Defecation Rules

Defecation requires:

```
Find location

↓

Perform action

↓

Dispose waste

↓

Update state
```

---

# Improper Waste Disposal

May cause:

- hygiene decrease
- disease risk
- contamination
- pest attraction

---

# Hygiene System

## Purpose

Represents bodily cleanliness.

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

# Hygiene Decrease

Caused by:

- sweat
- mud
- blood
- animal handling
- fishing
- smoke exposure
- dirty clothing
- lack of washing

---

# Hygiene Improvement

Requires:

- washing
- bathing
- cleaning clothing
- cleaning equipment

Time alone does not improve hygiene.

---

# Sleep System

## Purpose

Represents recovery through rest.

---

# Sleep States

```
Well Rested

Rested

Sleepy

Exhausted

Sleep Deprived
```

---

# Sleep Effects

Sleep improves:

- Energy
- Condition recovery
- Mental performance

Sleep does not instantly heal severe injuries.

---

# Injury System

All injuries require individual records.

Example:

```yaml
Injury ID:

INJ-000001

Type:

Broken Arm

Location:

Right Arm

Severity:

Severe

Cause:

Falling Tree

Treatment:

Splint Applied

Recovery:

Healing
```

---

# Status Effects

Temporary biological effects.

Examples:

```
Poisoned

Bleeding

Fever

Cold Exposure

Overheated

Exhausted

Wet

Pain
```

---

# Pain System

Pain represents physical discomfort.

Pain may come from:

- injuries
- illness
- burns
- trauma

Pain does not directly equal damage.

Example:

Minor injury:

High pain

Severe injury:

Low pain due to shock

---

# Body Knowledge Limitation

The Body HUD reveals sensations.

It does not automatically reveal medical truth.

Example:

Unknown:

```
Condition:

Chest Pain
```

Doctor knowledge:

```
Condition:

Possible Rib Fracture
```

Expert knowledge:

```
Condition:

Multiple rib fractures affecting breathing
```

---

# Update Rules

Every biological change follows:

```
Action

↓

World Processing

↓

Biological Calculation

↓

Update Character State

↓

Generate Body HUD

↓

Generate Narration
```

---

# No Hidden Body Changes

The simulation must never secretly modify the Chronicle's body.

Examples:

Invalid:

```
Time passed.

Health restored.
```

Valid:

```
The Chronicle slept for eight hours.

Energy improved.

Minor wound healing progressed.
```

---

# Death State

When death occurs:

Update:

```
Health:

Dead
```

Then update:

```
08-History.md
```

---

# Death Record

Format:

```yaml
Chronicle Death:

Date:

Location:

Cause:

Final Health:

Final Condition:

Last Action:
```

---

# Final Principle

00-Character-State.md is the biological truth of the Chronicle.

The Body HUD displays it.

The narrator describes its consequences.

The player decides what happens next.

The body remembers everything.