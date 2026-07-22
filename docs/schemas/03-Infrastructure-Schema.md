# 03-Infrastructure-Schema.md

# Project Draugr — Infrastructure System Schema

> **Items exist inside the world. Infrastructure becomes part of the world.**

---

# Purpose

This document defines the persistent infrastructure system of Project Draugr.

The Infrastructure System manages permanent or semi-permanent structures that modify the world.

Examples:

- shelters
- houses
- workshops
- storage buildings
- farms
- roads
- bridges
- wells
- production areas
- defensive structures
- permanent facilities

---

# Authority

This schema controls:

database/world/infrastructure.json

The Infrastructure Database is the authoritative source of truth for all constructed world structures.

---

# Infrastructure Philosophy

Infrastructure is different from items.

An item can be:

- carried
- stored
- moved
- traded
- lost

Infrastructure is:

- attached to the world
- location-dependent
- persistent
- part of the environment

---

# Example Difference

## Item

ITEM-000001
Wooden Chair
Location:
Main Camp Storage
State:
Stored

The chair can move.

---

## Infrastructure

INFRA-000001
Main Camp Workshop
Location:
Eastern Clearing
State:
Operational

The workshop exists as part of the world.

---

# Infrastructure Identity

Every infrastructure receives a permanent Infrastructure ID.

Example:

INFRA-000001

---

# Infrastructure ID Rules

Infrastructure IDs are:

- globally unique
- permanent
- immutable
- never reused

Destroyed infrastructure retains historical records.

Example:

INFRA-000001
Old Wooden Cabin
Destroyed
↓
INFRA-000002
New Stone Cabin

Never reuse:

INFRA-000001

---

# Infrastructure Database Structure

Example:

```json
{
  "infrastructure": [

    {
      "infrastructure_id":
      "INFRA-000001",

      "identity": {},

      "classification": {},

      "location": {},

      "construction": {},

      "condition": {},

      "contents": [],

      "history": []
    }

  ]
}
Identity Data
Defines the infrastructure.
Example:
{
 "infrastructure_id":

 "INFRA-000001",

 "name":

 "Main Camp Workshop",

 "category":

 "Workshop",

 "description":

 "A wooden structure used for crafting and manufacturing."
}
Infrastructure Categories
Every infrastructure belongs to a category.
Shelter
Examples:
temporary shelter
lean-to
cabin
house
underground shelter
Purpose:
protection
sleeping
storage
survival
Workshop
Examples:
general workshop
woodworking workshop
stone workshop
metal workshop
sewing workshop
Purpose:
crafting efficiency
manufacturing
processing
Storage Facility
Examples:
storage house
warehouse
granary
cellar
supply room
Purpose:
organized item storage
preservation
protection
Production Facility
Examples:
furnace
kiln
smokehouse
forge
mill
Purpose:
material transformation
advanced production
Agriculture Infrastructure
Examples:
farms
gardens
irrigation systems
animal pens
Transportation Infrastructure
Examples:
roads
bridges
pathways
docks
Defensive Infrastructure
Examples:
walls
watch towers
gates
traps
barriers
Utility Infrastructure
Examples:
wells
water systems
fire pits
lighting systems
Location System
Every infrastructure must have a world location.
Example:
{
 "location":

 {
   "region":
   "Northern Forest",

   "zone":
   "Main Camp",

   "coordinates":
   {
     "x":120,
     "y":45
   }
 }
}
Location Rules
Infrastructure cannot exist without location.
Invalid:
Workshop created.

Location:

Unknown
Valid:
Workshop created.

Location:

Eastern Clearing
Construction State
Represents development progress.
Valid States
Planned

↓

Foundation Started

↓

Under Construction

↓

Completed

↓

Operational

↓

Damaged

↓

Abandoned

↓

Destroyed
Construction Data
Example:
{
 "construction":

 {
   "started":

   "Day 10",

   "completed":

   "Day 18",

   "builder":

   "CHR-000001",

   "materials":

   [
    "Wood",
    "Stone",
    "Fiber"
   ]
 }
}
Infrastructure Condition
Represents physical state.
States:
Excellent

Good

Worn

Damaged

Critical

Destroyed
Condition Changes
Infrastructure degrades through:
weather
time
neglect
damage
disasters
attacks
Maintenance
Infrastructure requires maintenance.
Example:
Wooden Cabin

Condition:

Worn

Required:

Repair roof
Replace damaged beams
Maintenance Record
Example:
{
 "maintenance":

 [
  {
   "date":
   "Day 100",

   "action":
   "Roof repaired",

   "actor":
   "CHR-000001"
  }
 ]
}
Infrastructure Contents
Infrastructure may contain items.
Example:
Workshop:
INFRA-000001

Main Workshop

Contains:

ITEM-000021

Woodworking Table


ITEM-000022

Stone Working Table
Item Relationship
Infrastructure does not own items permanently by default.
Instead, it stores references.
Example:
{
 "contents":

 [
   "ITEM-000021",
   "ITEM-000022"
 ]
}
The actual item data remains inside:
database/entities/items.json
Example
Workshop:
INFRA-000001

Wood Workshop
Contains:
ITEM-000001

Wood Working Table


ITEM-000002

Wooden Chair
The chair remains an item.
The workshop remains infrastructure.
Infrastructure Destruction
Infrastructure is never deleted.
Example:
INFRA-000001

Destroyed

Date:

Day 250
Destruction Effects
When infrastructure is destroyed:
Update:
infrastructure.json
Update:
world-state.json
Update:
history/events.json
Possible item effects:
items destroyed
items buried
items recovered
items scattered
Construction Relationship
Construction System creates infrastructure.
Flow:
Construction Project

↓

Required Materials

↓

Labor Progress

↓

Completion

↓

Infrastructure Created
Construction vs Infrastructure
They are different systems.
Construction
Temporary process.
Contains:
plans
progress
requirements
labor
Infrastructure
Permanent result.
Contains:
completed structure
location
condition
history
Example
Construction:
PROJECT-000001

Build Workshop

Progress:

80%
After completion:
Creates:
INFRA-000001

Workshop

Operational
World Persistence
Infrastructure remains after:
abandonment
Chronicle death
years passing
ownership changes
Future Chronicle Interaction
A future Chronicle may discover:
INFRA-000001

Ancient Stone Workshop

Condition:

Damaged

History:

Created 200 years ago
The world remembers.
Database Update Pipeline
Whenever infrastructure changes:
Examples:
build
repair
upgrade
damage
destroy
abandon
Process:
Action

↓

Validate Change

↓

Update Infrastructure Database

↓

Update Related Systems

↓

Record History

↓

Generate Narration
Infrastructure Rules
The simulation must never:
create infrastructure without construction
move infrastructure instantly
repair infrastructure without resources
destroy infrastructure without cause
forget abandoned structures
Engineering Principle
Infrastructure represents technological advancement.
As the Chronicle develops:
Simple:
Campfire
becomes:
Workshop
becomes:
Manufacturing Complex
becomes:
Settlement Infrastructure
The world evolves through physical development.
Final Principle
The Infrastructure System answers:
"What has been built into the world?"

Items answer:
"What objects exist?"

Construction answers:
"How is something created?"

Infrastructure answers:
"What permanent changes now exist?"

The world remembers every structure ever built.
```