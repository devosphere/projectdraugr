# 04-Construction-Schema.md

# Project Draugr — Construction System Schema

> **Nothing appears instantly. Everything built has a process.**

---

# Purpose

This document defines the persistent construction project system of Project Draugr.

The Construction System manages the process of creating infrastructure and large-scale physical structures.

It tracks:

- planning
- material requirements
- labor requirements
- construction progress
- engineering decisions
- completion state

---

# Authority

This schema controls:

database/progression/construction.json

The Construction Database is the authoritative source of truth for all active and completed construction projects.

---

# Construction Philosophy

Construction is a process.

The simulation must never skip directly from:

Idea

to:

Completed Structure

Every major construction requires:

- planning
- resources
- tools
- labor
- time
- environmental conditions

---

# Construction Relationship

Construction connects multiple systems.

Knowledge System
        |
        ↓
Construction Planning
        |
        ↓
Materials System
        |
        ↓
Labor Progress
        |
        ↓
Infrastructure System
        |
        ↓
World State Update

---

# Construction Entity Identity

Every construction project receives a permanent Construction ID.

Example:

BUILD-000001

---

# Construction ID Rules

Construction IDs are:

- globally unique
- permanent
- immutable
- never reused

Cancelled projects remain in history.

Example:

BUILD-000001
Abandoned Cabin Project
Cancelled
↓
BUILD-000002
Stone Workshop Project

---

# Construction Database Structure

Example:

```json
{
  "construction_projects": [

    {
      "construction_id":
      "BUILD-000001",

      "identity": {},

      "classification": {},

      "location": {},

      "requirements": {},

      "progress": {},

      "history": []
    }

  ]
}
Identity Data
Defines the project.
Example:
{
 "construction_id":

 "BUILD-000001",

 "name":

 "Main Camp Workshop",

 "description":

 "A wooden structure for crafting and manufacturing."
}
Construction Categories
Every project belongs to a category.
Shelter Construction
Examples:
temporary shelter
lean-to
cabin
house
bunker
Production Construction
Examples:
workshop
forge
kiln
furnace
smokehouse
Storage Construction
Examples:
storage shed
warehouse
granary
cellar
Agriculture Construction
Examples:
farm plots
irrigation
animal pens
greenhouses
Transportation Construction
Examples:
roads
bridges
docks
pathways
Defensive Construction
Examples:
walls
gates
watch towers
barriers
Location Data
Every construction project requires a planned location.
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
Location Validation
Construction must consider:
terrain
available space
resources
environment
hazards
Example:
Invalid:
Build underground cellar

Location:

Solid mountain cliff
Possible:
Build underground cellar

Location:

Soft soil near settlement
Construction States
Every construction project has one state.
Valid States
Concept

Planned

Materials Gathering

Foundation Started

Under Construction

Paused

Completed

Failed

Abandoned

Cancelled
State Progression
Normal flow:
Concept

↓

Planned

↓

Materials Gathering

↓

Foundation Started

↓

Under Construction

↓

Completed
Concept State
Represents an idea.
Example:
Build Wooden Cabin
No physical work has started.
Planned State
Contains:
design
location
requirements
estimated effort
Example:
Wooden Cabin Plan

Location:
Northern Clearing

Materials:
Wood
Fiber
Stone

Labor:
High
Materials Requirement
Construction requires materials.
Example:
{
 "materials_required":

 [
  {
   "material":
   "Wood Logs",

   "required":
   50,

   "available":
   20
  },

  {
   "material":
   "Stone",

   "required":
   30,

   "available":
   5
  }
 ]
}
Material Rules
Materials are consumed from:
database/world/resources.json
or:
database/entities/items.json
depending on the material type.
Raw Materials
Examples:
logs
stone
clay
fibers
Tracked by:
Materials System.
Processed Components
Examples:
wooden beams
stone blocks
metal parts
Tracked as:
Item Entities.
Example:
ITEM-000100

Prepared Wooden Beam
Labor System
Construction requires labor.
Labor is affected by:
worker count
skills
tools
fatigue
environment
weather
Labor Data
Example:
{
 "labor":

 {
   "estimated_hours":

   80,

   "completed_hours":

   25,

   "workers":

   [
    "CHR-000001"
   ]
 }
}
Construction Progress
Progress is not displayed as percentages to the Chronicle.
Internal example:
{
 "progress":

 {
   "completed_work":

   25,

   "required_work":

   80
 }
}
Player View
The Chronicle sees:
Workshop Construction

Foundation completed.

Walls partially built.

Roof materials still required.
Not:
Construction:

62%
Construction Phases
Large structures are divided into phases.
Phase 1 — Planning
Includes:
design
location selection
material estimation
Phase 2 — Foundation
Includes:
ground preparation
structural base
support systems
Phase 3 — Structure
Includes:
walls
framework
major components
Phase 4 — Installation
Includes:
doors
storage
equipment
internal features
Phase 5 — Completion
Includes:
final adjustments
inspection
operational readiness
Construction Failure
Projects may fail due to:
insufficient materials
environmental damage
poor planning
lack of labor
abandonment
Example:
Wooden Bridge Project

Failed

Cause:

Flood destroyed foundation.
Construction Abandonment
Abandoned projects remain in the world.
Example:
BUILD-000010

Abandoned Watch Tower

Location:

Northern Ridge

Condition:

Collapsed
Completion Process
When construction completes:
Construction Project

↓

Generate Infrastructure Entity

↓

Create INFRA ID

↓

Transfer Construction History

↓

Update Infrastructure Database

↓

Update World State

↓

Record History
Example
Construction:
BUILD-000001

Stone Workshop

Completed
Creates:
INFRA-000001

Stone Workshop

Operational
Construction Does Not Create Items Automatically
Example:
Building a workshop does NOT automatically create:
chairs
tables
tools
shelves
Those require:
Crafting System
or
Item Placement.
Workstation Construction
Some infrastructure provides crafting bonuses.
Examples:
Woodworking Workshop

Provides:

Woodworking Efficiency
Sewing Workshop

Provides:

Textile Production Efficiency
Stone Workshop

Provides:

Stone Processing Efficiency
Workstation Relationship
Infrastructure may contain workstation items.
Example:
Infrastructure:
INFRA-000001

Wood Workshop
Contains:
ITEM-000010

Wood Working Table
The table remains an item.
The workshop remains infrastructure.
Maintenance Relationship
Completed infrastructure may generate maintenance requirements.
Example:
Wood Cabin

Condition:

Worn

Required:

Repair Roof
Replace Wall Sections
Database Update Pipeline
Whenever construction changes:
Examples:
start project
gather materials
add labor
pause
complete
fail
abandon
Process:
Player Action

↓

Validate Construction Rules

↓

Update Construction Database

↓

Update Materials Database

↓

Update Infrastructure Database

↓

Record History

↓

Generate Narration
Construction Rules
The simulation must never:
instantly create buildings
ignore material requirements
ignore labor requirements
ignore tools
complete projects without time passing
create infrastructure without records
Future Expansion
Possible future construction systems:
blueprints
engineering specialization
architecture knowledge
NPC workers
settlements
automation
industrial chains
civilization development
Final Principle
The Construction System answers:
"How is something being created?"

The Infrastructure System answers:
"What now exists in the world?"

The Item System answers:
"What physical objects exist?"

Construction is the bridge between imagination and reality.
```