# 00 - Simulation Initialization

> **Project Draugr**
>
> *Every Chronicle begins as a small event inside a world that already exists.*

---

# Purpose

This document defines the initialization process for a new Project Draugr simulation.

The initialization system creates the starting conditions of the world before the Chronicle begins interacting with it.

The world must exist independently from the Chronicle.

The Chronicle is introduced into an already functioning world.

---

# Initialization Philosophy

Project Draugr does not generate a story.

It generates a world state.

The simulation must create:

- environment
- geography
- weather
- resources
- wildlife
- physical laws
- Chronicle condition
- initial possessions
- world history

The simulation does not create:

- quests
- objectives
- scripted events
- guaranteed resources
- convenient discoveries

---

# Simulation Creation Flow

A new simulation follows:

Create Simulation
↓
Generate World Seed
↓
Generate Environment
↓
Generate Geography
↓
Generate Initial Resources
↓
Generate Wildlife
↓
Create Chronicle
↓
Initialize Database
↓
Start Day 0
↓
Begin Simulation

---

# Simulation Identity

Every simulation receives a unique identifier.

Example:

SIM-000001

Rules:

- globally unique
- permanent
- never reused

Stored in:

database/current/simulation-state.json

Example:

```json
{
  "simulation_id": "SIM-000001"
}
World Seed
Each simulation receives a world seed.
The seed determines:
terrain generation
climate patterns
resource distribution
wildlife distribution
environmental conditions
Example:
WORLD-SEED-847392
The same seed should produce the same initial world.
World Initialization
Before the Chronicle awakens, generate:
Geography
Includes:
terrain
regions
water sources
forests
mountains
plains
caves
resource zones
Stored in:
geography.json
Environment
Includes:
current weather
season
temperature
time cycle
natural conditions
Stored in:
world-state.json
Wildlife
Includes:
existing species
animal populations
migration patterns
predator locations
prey locations
The world contains life before the Chronicle exists.
Resources
Includes:
available materials
natural resources
resource density
resource locations
Stored in:
materials.json
Chronicle Initialization
The Chronicle is created after the world exists.
The Chronicle receives:
identity
physical condition
starting location
survival state
initial knowledge
initial possessions
Chronicle Identity
Stored in:
chronicle.json
Initial values:
{
  "chronicle_id": "CHR-000001",
  "name": null,
  "status": "Awakened"
}
Starting Condition
The MVP begins with a healthy human baseline.
Stored in:
character-state.json
Initial state:
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
Starting Inventory
Default MVP:
The Chronicle begins without guaranteed equipment.
Possible starting states:
Bare Survival
Inventory:
None
Controlled Start
Inventory may contain:
clothing
basic survival items
predefined equipment
The starting inventory must be explicitly configured.
No hidden items exist.
Stored in:
items.json
Starting Location
The Chronicle begins at a generated location.
Example:
Region:
Northern Forest

Area:
Unknown Forest Clearing

Coordinates:
Generated
The Chronicle does not know all surrounding information.
Only discovered information becomes available.
Initial Knowledge
The Chronicle begins with baseline human knowledge.
Examples:
Known:
walking
basic observation
basic interaction
basic survival instincts
Unknown:
local geography
resource locations
wildlife behavior
advanced crafting
regional history
Stored in:
knowledge.json
Day 0
Day 0 represents the awakening moment.
The world clock begins:
Example:
Day:
0

Time:
06:00

Season:
Generated

Weather:
Generated
Initial Database Creation
After initialization, create:
database/current/
Required files:
character-state.json

chronicle.json

items.json

infrastructure.json

construction.json

crafting.json

materials.json

knowledge.json

history.json

geography.json

world-state.json

simulation-state.json
Initial History Record
The first event is recorded.
Stored in:
history.json
Example:
{
  "event": "Simulation Initialized",

  "day": 0,

  "description":
  "A new world was generated and a Chronicle awakened."
}
Initialization Validation
Before simulation begins, verify:
World
geography exists
weather exists
resources exist
wildlife exists
Chronicle
identity exists
body state exists
location exists
Database
all required files exist
schema versions match
no invalid entities exist
Simulation Start State
After successful initialization:
Simulation Status:

ACTIVE
The first player interaction begins.
First Response Format
The first simulation response must contain:
Environmental Header
weather icon
season icon
date
time
Body HUD
Current biological state.
Narration
Only what the Chronicle can perceive.
Action Composer
>
Waiting for Chronicle action.
No Previous History
A new simulation has:
no discoveries
no settlements
no construction
no crafted items
no written records
no Chronicle achievements
The world begins empty of Chronicle influence.
Final Rule
Initialization creates the world.
It does not create the adventure.
The world exists first.
The Chronicle simply enters it.
```