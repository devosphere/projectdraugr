# 96 - World Initialization Protocol

> **Project Draugr**
>
> *A world must exist before a Chronicle can awaken.*

---

# Purpose

This document defines the process used to transform an empty database into a persistent Project Draugr world.

The World Initialization Protocol creates the foundation required for simulation.

It defines:

- world creation
- simulation identity generation
- geography generation
- environmental initialization
- resource population
- wildlife initialization
- Chronicle placement
- initial database state
- first snapshot creation

---

# Core Principle

The world exists independently from the Chronicle.

The Chronicle is inserted into an already existing world.

The initialization order is:

WORLD
↓
ENVIRONMENT
↓
GEOGRAPHY
↓
RESOURCES
↓
LIFE
↓
CHRONICLE
↓
SIMULATION START

---

# Initialization Trigger

World initialization begins when:

NEW CHRONICLE

is requested and no active simulation exists.

---

# Initialization States

The simulation initialization lifecycle:

UNINITIALIZED
↓
GENERATING
↓
VALIDATING
↓
ACTIVE
↓
ARCHIVED

---

# Initial Database Condition

Before initialization:

database/current/

contains empty structures only.

Example:

simulation-state.json
{
  "status": "UNINITIALIZED"
}

---

# Step 1 - Generate Simulation Identity

Create:

Simulation ID

Format:

SIM-XXXXXX

Example:

SIM-000001

Rules:

- globally unique
- permanent
- immutable
- never reused

---

# Step 2 - Generate World Seed

Every world receives a permanent seed.

Example:

WORLD-SEED-482951

The seed determines:

- geography
- climate patterns
- resource distribution
- wildlife distribution
- environmental conditions

The same seed must always generate the same initial world.

---

# Step 3 - Initialize Simulation State

Update:

simulation-state.json

Example:

```json
{
  "simulation_id": "SIM-000001",
  "world_seed": "WORLD-SEED-482951",
  "status": "GENERATING",
  "day": 0,
  "time": "00:00",
  "active_chronicle": null,
  "created_at": "2026-07-20T00:00:00"
}
Step 4 - Generate World State
Create:
world-state.json

Defines global world conditions.
Includes:
world age
climate state
season
weather
environmental conditions
major world variables
Example:
{
  "world_age": 0,
  "season": "Summer",
  "weather": "Clear",
  "environmental_state": "Stable"
}
Step 5 - Generate Geography
Create:
geography.json
The world must have physical space before entities exist.
Generate:
regions
terrain
water systems
elevation
climate zones
landmarks
Example:
Region

ID:
REGION-000001

Name:
Northern Forest

Type:
Temperate Forest

Features:

- river
- hills
- wildlife zones
- resource areas
Geography Rules
Generated geography must be:
internally consistent
persistent
physically connected
The simulation must avoid:
impossible terrain
disconnected ecosystems
unrealistic resource placement
Step 6 - Generate Climate
Initialize:
temperature patterns
rainfall patterns
seasonal cycle
weather behavior
Weather is dynamic.
Climate is permanent.
Climate Example
Climate:
Temperate Forest
may produce:
Summer:

Warm days
Moderate rainfall

Winter:

Cold temperatures
Snow possibility
Step 7 - Generate Materials
Create:
materials.json
Populate naturally occurring resources.
Examples:
Raw:
trees
stone
clay
plants
water sources
Processed materials do not exist unless generated naturally.
Material Rules
Resources must have:
location
quantity
regeneration rules
ecological relationship
The world does not contain infinite resources.
Step 8 - Generate Wildlife
Wildlife generation creates living ecosystems.
Includes:
species
populations
territories
migration patterns
behavior
Wildlife is not generated for the player.
Wildlife exists independently.
Wildlife Rules
Animals:
move
reproduce
migrate
hunt
die
interact with environment
The player is not the center of the ecosystem.
Step 9 - Generate Infrastructure
Initial world generation may include natural or existing structures.
Examples:
caves
ruins
abandoned structures
natural landmarks
Human-built infrastructure does not automatically exist unless defined by world generation.
Step 10 - Create Chronicle
Only after the world exists:
Create:
CHR-000001
Update:
chronicle.json

character-state.json
Chronicle Initial State
Default:
Healthy Human Baseline
No:
skills
equipment
injuries
knowledge advantages
Unless specified.
Initial Character Creation
Generate:
Identity
Includes:
Chronicle ID
name
age
biological sex
physical baseline
Physiology
Initialize:
health
hunger
thirst
energy
temperature
wetness
bladder
bowel
hygiene
Knowledge
Default:
Basic human awareness only.
No specialized knowledge.
Starting Inventory
Default:
Empty
The Chronicle does not begin with:
tools
weapons
supplies
shelter
unless defined during creation.
Starting Location
The Chronicle is placed based on:
geography
environment
survival conditions
The system must not guarantee:
safety
resources
nearby shelter
water access
Step 11 - Initial Validation
Before activation:
Run validation.
Check:
Identity
Verify:
simulation ID
world seed
Chronicle ID
Geography
Verify:
valid regions
connected terrain
valid locations
Resources
Verify:
valid placement
realistic quantities
Chronicle
Verify:
valid body state
valid location
Step 12 - Activate Simulation
After successful validation:
Update:
simulation-state.json
Change:
GENERATING

↓

ACTIVE
Set:
day = 1
time = 00:00
Step 13 - Create Initial Snapshot
Create:
database/snapshots/day-001/
Store:
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
Create:
history.json
Record:
World Created

Simulation Started

Chronicle Awakened
Initialization Completion
The world is now ready.
Final state:
WORLD EXISTS

↓

CHRONICLE EXISTS

↓

DATABASE VALID

↓

SIMULATION ACTIVE

↓

DAY 001 BEGIN
Initialization Restrictions
World initialization must never:
Create:
quests
objectives
chosen destinies
special abilities
convenient discoveries
guaranteed survival paths
The Chronicle enters the world.
The world does not prepare itself for the Chronicle.
Final Principle
A Project Draugr world is not generated around the player.
The player is generated inside the world.
The environment existed before the Chronicle.
The environment will continue after the Chronicle.
The world remembers.
```