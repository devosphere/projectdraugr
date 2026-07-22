# 20 - Simulation Output Format

> **Project Draugr**
>
> *The interface is not the world. It is the window through which the Chronicle perceives reality.*

---

# Purpose

This document defines the mandatory output structure used by the Project Draugr Simulation Engine.

The Output Format exists to ensure every simulation turn provides consistent information.

It defines:

- information ordering
- UI sections
- Body HUD placement
- environmental awareness
- narration rules
- action input format

The output format represents the Chronicle's perception interface.

---

# Core Principle

The Simulation Engine has two responsibilities:

SIMULATE REALITY

TRANSLATE REALITY INTO PERCEPTION

The output must never replace simulation logic.

The UI only displays the current state.

---

# Output Pipeline

Every simulation response follows:

DATABASE STATE
↓
VALIDATION
↓
SIMULATION PROCESSING
↓
STATE UPDATE
↓
UI RENDERING
↓
NARRATION
↓
PLAYER INPUT

---

# Mandatory Output Order

Every active simulation turn must follow this structure:

Simulation Header

Date and Time

Environmental State

Location Context

Body HUD

Visible Entities

World Observation

Narration

Available Input


---

# Section 1 - Simulation Header

Every response begins with:

══════════════════════════════════════
PROJECT DRAUGR
══════════════════════════════════════

Purpose:

Identify that the response is inside the simulation.

---

# Section 2 - Date and Time

The simulation must display:

DATE
TIME
DAY NUMBER

Example:

DATE:
July 20, 2026
TIME:
06:34
DAY:
001

---

Time must always reflect:

- world progression
- consumed actions
- environmental changes

---

# Section 3 - Environmental State

Displays external conditions.

Required:

WEATHER
TEMPERATURE
SEASON
TIME PERIOD

Example:

ENVIRONMENT
Weather:
Clear
Temperature:
Cool Morning
Season:
Summer
Period:
Morning

---

The environment describes the world.

It does not provide advice.

Incorrect:

The weather is dangerous.

Correct:

Heavy rain falls across the region.

---

# Section 4 - Location Context

Displays the Chronicle's current position.

Required:

REGION
GENERAL LOCATION
SPECIFIC LOCATION

Example:

LOCATION
Region:
Northern Valewood
General:
Forest
Specific:
Small Clearing

---

Location information comes from:

geography.json

and:

character-state.json

---

# Section 5 - Body HUD

The Body HUD is mandatory.

The Body HUD represents internal biological awareness.

Format:

══════════════════════════════════════
BODY
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
Normal
Hygiene:
Clean
══════════════════════════════════════

---

# Body HUD Rules

The Body HUD:

- informs
- does not recommend
- does not warn repeatedly
- does not protect the player

---

Incorrect:

Thirst:
Very Thirsty
You should find water soon.

Correct:

Thirst:
Very Thirsty

---

The player decides what to do.

---

# Section 6 - Visible Entities

Displays physical objects observable by the Chronicle.

Sources:

items.json
infrastructure.json
geography.json

---

Example:

VISIBLE ENTITIES
Nearby:
Fallen Branch
Small Stones
Dense Vegetation
Structures:
None

---

Rules:

Only show what the Chronicle can perceive.

The simulation may know more than the Chronicle.

---

# Knowledge Limitation

The UI must respect Chronicle knowledge.

Example:

World database:

Entity:
Unknown Plant Species
Properties:
Medicinal
Poisonous

Chronicle without knowledge:

Visible:
A small flowering plant.

Chronicle with botany knowledge:

Visible:
A medicinal herb commonly used for fever treatment.

---

# Section 7 - World Observation

This section describes observable environmental details.

Examples:

- sounds
- smells
- visible terrain
- weather effects
- animal traces
- movement

---

Rules:

Observations must be:

- truthful
- sensory-based
- knowledge-limited

---

Incorrect:

A deer is 300 meters east.

unless the Chronicle can perceive it.

---

Correct:

You notice fresh tracks crossing the muddy ground.

---

# Section 8 - Narration

Narration describes consequences and experiences.

It does not duplicate HUD information.

---

Incorrect:

HUD:

Thirst:
Very Thirsty

Narration:

You are very thirsty.

---

Correct:

Narration:

Your mouth feels dry as you continue walking beneath the morning sun.

---

Narration may describe:

- sensations
- events
- environmental reactions
- consequences

---

Narration must never:

- recommend actions
- create objectives
- guide decisions
- provide hints

---

# Section 9 - Available Input

Every turn ends with:

══════════════════════════════════════
ACTION:

══════════════════════════════════════

---

The player may provide:

- simple actions
- complex intentions
- observations
- questions

---

Example:

ACTION:
Examine the nearby stones.


---

# Action Interpretation

The engine does not treat input as commands.

Input represents intent.

Example:

Player:

Build a shelter.


Engine evaluates:

- materials
- knowledge
- environment
- time
- physiology

---

# Multiple Action Handling

If the player provides multiple actions:

Example:

Gather wood, build a fire, and cook food.


The engine separates:

Gather Wood
↓
Build Fire
↓
Cook Food

Each action receives independent evaluation.

---

# No Hidden UI Information

The Simulation Engine must not display:

- hidden entity data
- exact coordinates
- numerical survival percentages
- probability values
- unseen threats

---

Incorrect:

Wolf:
500 meters away
Danger Level:
73%

---

Correct:

You hear movement somewhere beyond the trees.

---

# No Numerical Optimization

The UI avoids game-like statistics.

Incorrect:

Health:
87%
Stamina:
42%
Temperature:
36.8°C

---

Correct:

Health:
Healthy
Energy:
Tired
Temperature:
Cold

---

# Event Display

Major events may receive a separate section.

Example:

WORLD EVENT
A storm system enters the region.

---

Events must originate from:

- world simulation
- environment
- entities
- natural progression

Never from narrative convenience.

---

# Database Relationship

The UI reflects:

database/current/

It does not create reality.

Example:

If:

items.json

contains:

ITEM-000001
Destroyed

The UI cannot display:

You are holding ITEM-000001

---

# Snapshot Compatibility

The output format must always be reproducible from:

database/current/

A future engine should be able to generate the same UI from the same database state.

---

# Simulation Turn Example

══════════════════════════════════════
PROJECT DRAUGR
══════════════════════════════════════
DATE:
July 20, 2026
TIME:
06:34
DAY:
001
ENVIRONMENT
Weather:
Clear
Temperature:
Cool Morning
Season:
Summer
LOCATION
Region:
Northern Valewood
General:
Forest
Specific:
Unknown Clearing
BODY
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
Normal
Hygiene:
Clean
VISIBLE ENTITIES
Nearby:
Fallen Branches
Small Stones
Tall Grass
OBSERVATION
Birds move through the canopy above.
A faint breeze moves the leaves.
NARRATION
Morning light reaches the forest floor.
The world continues around you.
ACTION:

══════════════════════════════════════

---

# Final Principle

The Output Format is the Chronicle's perception layer.

The world exists separately.

The database stores reality.

The simulation determines events.

The UI reveals only what the Chronicle can perceive.

The player chooses what happens next.