# 95 - Database Validation

> **Project Draugr**
>
> *A persistent world must reject impossible realities.*

---

# Purpose

This document defines the validation rules required to maintain database integrity inside Project Draugr.

The validation system ensures:

- world consistency
- entity integrity
- biological consistency
- location accuracy
- historical accuracy
- simulation reliability

Validation prevents the creation of impossible world states.

---

# Core Principle

The database is the authority of reality.

Before the simulation continues:

DATABASE UPDATE
↓
VALIDATION
↓
SIMULATION CONTINUES

Invalid states must never become permanent.

---

# Validation Priority

Validation follows this order:

Simulation State
↓
Chronicle State
↓
Entity State
↓
World State
↓
Infrastructure
↓
Construction
↓
Resources
↓
History

---

# Validation Frequency

Validation occurs:

## Mandatory

- After every database modification
- Before narration generation
- During snapshot creation
- During session loading
- During simulation initialization

---

## Optional

- During debugging
- During development testing
- During engine updates

---

# Validation Result States

Every validation attempt produces:

VALID
WARNING
INVALID
CORRUPTED

---

# VALID

The database contains a consistent world state.

Simulation may continue.

---

# WARNING

The database contains unusual but possible conditions.

Example:

Abandoned structure without owner

Simulation may continue.

---

# INVALID

The database contains impossible conditions.

Simulation must stop processing.

Example:

Destroyed item equipped by player

---

# CORRUPTED

The database cannot reliably represent reality.

Recovery is required.

---

# Simulation State Validation

File:

simulation-state.json

Validate:

- simulation ID
- world seed
- current day
- current time
- active Chronicle

---

## Required Fields

Example:

```json
{
  "simulation_id": "SIM-000001",
  "status": "ACTIVE",
  "day": 1,
  "time": "08:00"
}
Invalid Examples
Missing simulation ID:
{
  "simulation_id": null
}
Duplicate simulation IDs:
SIM-000001
SIM-000001
Chronicle Validation
Files:
character-state.json

chronicle.json
Identity Validation
Check:
Chronicle ID exists
Name exists
Age is valid
Biological profile exists
Invalid:
{
 "chronicle_id": "CHR-000001",
 "age": -5
}

Location Validation
Every active Chronicle must have:
General Location

Specific Location

Example:
{
 "region": "Northern Forest",
 "location": "Riverbank"
}

Invalid:
{
 "location": "Unknown"
}

unless the Chronicle is intentionally lost or unconscious.
Physiology Validation
File:
character-state.json

Validate:
Health
Hunger
Thirst
Energy
Temperature
Wetness
Bladder
Bowel
Hygiene
Body Logic Validation
Examples:
Valid:
Health:
Dying

Energy:
Exhausted

Possible combination.
Invalid:
Health:
Dead

Energy:
Energetic

Item Validation
File:
items.json

Entity Identity Rules
Every item must have:
item_id

Format:
ITEM-XXXXXX

Rules:
unique
permanent
never reused
Invalid:
Duplicate:
ITEM-000001
ITEM-000001
Item State Validation
Every item has exactly one state.
Valid:
Stored

Carried

Equipped

Placed

Destroyed

Consumed
Invalid:
{
 "state": [
   "Stored",
   "Destroyed"
 ]
}
Destroyed Entity Rules
Destroyed entities:
Must have:
state:
Destroyed
Must not have:
owner

container

equipment_slot
Invalid:
{
 "item_id":"ITEM-000001",
 "state":"Destroyed",
 "owner":"CHR-000001"
}
Ownership Validation
If:
owner = Chronicle
The item must exist in Chronicle inventory.
Invalid:
items.json

ITEM-000001
Owner:
CHR-000001
but:
character-state.json

Inventory:
empty
Container Validation
Items inside containers must reference valid containers.
Example:
Backpack
 |
 └── Knife
Requires:
Backpack exists.
Invalid:
Knife

container:
ITEM-999999
when container does not exist.
Location Validation
Every physical entity must have:
location
state
ownership status
Invalid:
ITEM-000001

Location:
null

State:
Stored
Valid:
ITEM-000001

Location:
Forest Clearing

State:
Dropped
Materials Validation
File:
materials.json
Validate:
quantity
location
resource state
Invalid:
Negative quantity:
{
 "stone": -10
}
Resource Conservation
Material changes must follow conservation rules.
Example:
Crafting:
5 Stone

↓

Stone Axe
must remove consumed materials.
Invalid:
Creating:
Stone Axe
without:
Stone removed
Wood removed
Binding removed
Crafting Validation
Files:
crafting.json

items.json

materials.json
Validate:
recipe exists
required materials exist
knowledge exists
output is registered
Invalid:
Creating unknown recipe:
Steel Sword
without knowledge or recipe.
Construction Validation
Files:
construction.json

infrastructure.json

materials.json
Validate:
project exists
materials consumed
construction progress valid
Invalid:
Completed Cabin
with:
0 materials consumed
0 labor
Infrastructure Validation
File:
infrastructure.json
Validate:
Infrastructure ID
location
state
condition
Rules:
Infrastructure cannot:
move without relocation event
exist without location
duplicate IDs
Geography Validation
File:
geography.json
Validate:
regions
coordinates
connections
terrain relationships
Invalid:
River

Location:
Mountain peak

Elevation:
above source
without explanation.
World State Validation
File:
world-state.json
Validate:
season
weather
environmental conditions
Example:
Valid:
Winter

Snow

Cold Temperature
Invalid:
Summer

Heavy Snowstorm
unless weather rules allow it.
History Validation
File:
history.json
History records must:
never be deleted
never be rewritten
maintain timestamps
Invalid:
Removing:
Chronicle discovered river
from history.
Snapshot Validation
Before:
database/snapshots/day-XXX/
creation:
Run full validation.
Snapshot must represent:
A complete possible world state.
Recovery Rules
When validation fails:
Priority:
Fix Current State

↓

Restore Latest Snapshot

↓

Restore Previous Snapshot
Never:
invent missing data
silently repair history
delete contradictions
Validation Logging
Every failed validation creates:
Validation Record
Stored in:
history.json
Example:
Day 004

Validation Failure:

ITEM-000021 had invalid destroyed state.

Resolution:

Restored previous snapshot.
Final Rule
The simulation may create unexpected realities.
It may create difficult realities.
It may create dangerous realities.
But it must never create impossible realities.
Validation protects the world from contradictions.
The database defines what exists.
Validation ensures reality remains possible.
```