# 17 – Chronicle System

> **Project Draugr**
>
> *A Chronicle is not a character sheet. It is a life that leaves evidence behind.*

---

# Purpose

This document defines the Chronicle System of Project Draugr.

The Chronicle System represents the persistent identity, existence, and history of the player's controlled entity.

The player does not control a statistic container.

The player experiences the life of a Chronicle within a persistent world.

---

# Core Philosophy

The Chronicle System follows one immutable principle:

> **The Chronicle exists before the player observes it, and continues after the player leaves.**

A Chronicle is:

- a living entity
- a physical presence in the world
- a participant in history
- a carrier of knowledge
- a source of decisions and consequences

---

# Chronicle Identity

Every Chronicle has a permanent identity.

A Chronicle identity contains:

- Chronicle ID
- Name
- Origin
- Creation Date
- Current Age
- Biological Sex
- Physical Characteristics
- Current Location
- Survival Duration
- Known History

---

# Chronicle ID

Every Chronicle receives a permanent identifier.

Example:

```
CHR-000001
```

Chronicle IDs are:

- unique
- permanent
- never reused

A Chronicle's identity remains recorded even after death.

---

# Chronicle Record

The Chronicle record contains:

- Identity information
- Life history
- Major achievements
- Discoveries
- Relationships
- Settlements created
- Important events

It does NOT contain:

- current inventory
- current body condition
- current equipment
- temporary resources

Those belong to their respective systems.

---

# System Separation

The Chronicle System connects multiple persistent systems.

```
Chronicle

↓

Character State
(Body)

↓

Inventory
(Possessions)

↓

Equipment
(Worn Items)

↓

Knowledge
(Understanding)

↓

History
(Legacy)
```

Each system has its own authority.

The Chronicle record references them.

It does not replace them.

---

# Chronicle Beginning

A new Chronicle begins with:

- biological state
- basic human capability
- limited knowledge
- no artificial advantages

The Chronicle does not begin with:

- unlocked technology
- hidden knowledge
- guaranteed resources
- special abilities

The world determines circumstances.

---

# Origin

Origin describes the Chronicle's background.

Examples:

```
Unknown Survivor

Wilderness Native

Former Settler

Explorer

Craftsman

Soldier

Researcher
```

Origin influences starting knowledge and familiarity.

It does not provide unrealistic advantages.

---

# Physical Identity

The Chronicle has a physical body.

Physical characteristics include:

- height
- body structure
- appearance
- biological sex
- natural traits

Physical traits influence:

- carrying capability
- endurance
- environmental tolerance
- appearance

They do not determine destiny.

---

# Age

Age affects biological capability.

Age influences:

- recovery
- physical performance
- health risks

Age does not directly determine knowledge.

A young Chronicle may become highly skilled.

An older Chronicle may lack experience.

---

# Chronicle Actions

The Chronicle only performs actions explicitly declared by the player.

The simulation must never:

- decide intentions
- add hidden actions
- protect the Chronicle
- assume motivations

Example:

Player:

> I walk toward the river.

Valid:

The Chronicle moves toward the river.

Invalid:

The Chronicle also collects stones, drinks water, and searches the area.

---

# Chronicle Decisions

Every decision belongs to the player.

The simulation provides:

- environment
- consequences
- available information

The player provides:

- intent
- actions
- priorities

---

# Chronicle Memory

The Chronicle remembers what the Chronicle has experienced.

Memory includes:

- observed locations
- learned skills
- discovered information
- personal events

Memory does not reveal:

- unseen areas
- hidden resources
- unknown creatures
- future events

---

# Experience vs Knowledge

Experience creates opportunities for learning.

However:

Experience is not automatically complete understanding.

Example:

The Chronicle sees lightning.

Result:

```
Experience:

Storm observed
```

Not:

```
Knowledge:

Meteorological science unlocked
```

Understanding develops through observation and study.

---

# Chronicle Inventory Relationship

The Chronicle does not contain items directly.

Inventory is managed by:

```
02-Inventory.md
```

Example:

Chronicle:

```
Owns:
ITEM-000021
```

Inventory:

```
ITEM-000021

Stone Axe

Location:
Carried
```

---

# Chronicle Equipment Relationship

Equipment is a state of Items.

The Chronicle does not store equipment separately.

Example:

```
ITEM-000021

Stone Axe

State:

Equipped

Slot:

Left Hand
```

---

# Chronicle Death

Death is permanent.

When a Chronicle dies:

The simulation records:

- date
- location
- cause
- final condition
- final known actions
- surviving possessions
- created infrastructure

---

# Death Record Example

```
Chronicle Death Record

Chronicle:

CHR-000001

Date:

August 14, 2026

Location:

Northern Forest

Cause:

Critical Dehydration

Final Condition:

Severely Dehydrated

Last Known Action:

Searching for water

Environmental Conditions:

High temperature
No nearby water source
```

---

# Legacy System

A Chronicle leaves evidence behind.

Legacy includes:

- buildings
- tools
- documents
- discoveries
- altered terrain
- knowledge transfer

The world remembers actions even after the Chronicle is gone.

---

# Future Chronicle Interaction

Future Chronicles may discover:

- abandoned settlements
- old tools
- written records
- forgotten technologies
- graves
- ruins

A previous Chronicle becomes part of the world's history.

---

# Chronicle Achievements

Achievements are historical events, not rewards.

Examples:

```
First Shelter Built

First Tool Crafted

First Permanent Settlement

First Metal Tool Produced

First Map Created
```

They are recorded because they changed the world.

---

# No RPG Level System

Project Draugr does not use:

- character levels
- experience points
- skill points
- talent trees

Progression emerges naturally.

Example:

A better carpenter becomes better because:

- they practiced
- they learned
- they improved techniques

Not because:

```
Carpentry Level +1
```

---

# Chronicle Persistence

A Chronicle's existence is always recorded.

The world tracks:

- who existed
- what they did
- what they created
- what they discovered
- how they ended

Nothing important disappears.

---

# Future Expansion

Future versions may introduce:

- family systems
- descendants
- inheritance
- reputation
- culture creation
- civilizations founded by Chronicles
- Chronicle archives
- generational gameplay

---

# Final Principle

The Chronicle is not a player avatar.

The Chronicle is a life inside a persistent world.

The player makes choices.

The world reacts.

History records the result.

The Chronicle may disappear.

The world remembers.