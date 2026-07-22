# 06-Materials-Schema.md

# Project Draugr — Materials System Schema

> **Materials are the foundation of civilization. Every structure, tool, and object begins as a resource.**

---

# Purpose

This document defines the persistent materials system of Project Draugr.

The Materials System manages:

- discovered resources
- harvested resources
- raw materials
- processed materials
- material availability
- resource transformation
- depletion and regeneration

---

# Authority

This schema controls:

database/world/materials.json

The Materials Database is the authoritative source of truth for available raw and processed materials.

---

# Materials Philosophy

Materials are not items.

A material represents a resource category or available quantity.

An item represents a unique physical object.

---

# Example Difference

## Material

Wood Logs
Quantity:
Available

Represents:

A resource supply.

---

## Item

ITEM-000001
Wooden Handle
Condition:
Good

Represents:

A specific physical object.

---

# Material Categories

Materials are divided into:

1. Raw Materials
2. Processed Materials
3. Refined Materials
4. Specialized Materials

---

# Raw Materials

Raw materials exist naturally in the world.

Examples:

- wood
- stone
- clay
- sand
- soil
- fibers
- plants
- animal products
- ores

---

# Raw Material Structure

Example:

```json
{
 "material_id":

 "MAT-000001",

 "name":

 "Hardwood Logs",

 "category":

 "Raw Material",

 "source":

 "Forest",

 "quantity":

 "Available"
}
Raw Material Examples
Wood
Sources:
forests
fallen trees
branches
driftwood
Used for:
tools
buildings
furniture
fuel
Stone
Sources:
mountains
riverbeds
exposed rock
Used for:
tools
foundations
structures
processing
Clay
Sources:
riverbanks
wetlands
Used for:
pottery
bricks
construction materials
Fibers
Sources:
plants
vines
grasses
Used for:
rope
clothing
weaving
bindings
Animal Materials
Sources:
hunting
farming
wildlife
Examples:
hide
bone
sinew
feathers
fur
Used for:
clothing
tools
crafting components
Processed Materials
Processed materials require transformation.
Examples:
charcoal
prepared timber
rope
leather
bricks
treated fibers
Processed Material Rules
Processing requires:
knowledge
tools
workstation
time
Example:
Raw:
Wood Logs
↓
Processing:
Woodworking
↓
Processed:
Prepared Timber
Material Transformation
Material processing follows:
Raw Material

↓

Processing Method

↓

Processed Material

↓

Crafting / Construction
Example
Input:
Hardwood Logs

Quantity:

10
Process:
Sawing

Workbench Required
Output:
Prepared Wooden Boards

Quantity:

20
Material Database Structure
Example:
{
 "materials":

 [

  {

   "material_id":

   "MAT-000001",

   "identity": {},

   "classification": {},

   "availability": {},

   "location": {},

   "history": []

  }

 ]
}
Material Identity
Defines the material.
Example:
{
 "material_id":

 "MAT-000001",

 "name":

 "Oak Wood",

 "category":

 "Raw Material",

 "description":

 "Dense hardwood suitable for construction and tools."
}
Material Classification
Every material has classifications.
Example:
{
 "classification":

 {

 "type":

 "Wood",

 "quality":

 "High",

 "properties":

 [
  "Strong",
  "Durable",
  "Heavy"
 ]

 }
}
Material Properties
Properties influence crafting and construction.
Examples:
Wood
Properties:
strength
flexibility
durability
weight
burn quality
Stone
Properties:
hardness
sharpness potential
fracture tendency
Metal
Properties:
hardness
conductivity
durability
corrosion resistance
Material Quality
Materials may have quality states.
Examples:
Poor

Common

Good

High Quality

Exceptional
Quality Factors
Affected by:
source location
harvesting method
processing skill
tools
environment
Material Quantity
Materials do not use individual IDs unless transformed into physical objects.
Example:
Valid:
MAT-000001

Oak Logs

Quantity:

50
Not required:
ITEM-000001

Oak Log
for every individual log.
When Materials Become Items
Materials become Item Entities when they become distinct objects.
Example:
Before:
Wood Material

Quantity:

20
After crafting:
ITEM-000100

Wooden Chair

Condition:

Good
Resource Location
Materials must have a source location.
Example:
{
 "location":

 {

 "region":

 "Northern Forest",

 "zone":

 "Oak Grove"

 }
}
Resource Availability
Material availability states:
Abundant

Available

Limited

Rare

Depleted
Resource Depletion
Resources decrease through:
harvesting
mining
hunting
gathering
consumption
Example:
Forest:
Before:
Hardwood:

Abundant
After repeated logging:
Hardwood:

Limited
Resource Regeneration
Some materials may regenerate.
Examples:
plants
forests
animals
fish populations
Regeneration depends on:
environment
ecosystem health
time
Non-Regenerating Resources
Some resources may become permanently depleted.
Examples:
mined ores
rare minerals
destroyed ecosystems
Material Consumption Rules
When crafting:
Crafting Request

↓

Check Material Availability

↓

Reserve Materials

↓

Consume Materials

↓

Create Result
Construction Material Relationship
Construction consumes materials.
Example:
Building:
Stone Cabin
Requires:
Stone

Wood Beams

Fiber Rope
After completion:
Materials are removed.
Infrastructure is created.
Material Processing Relationship
Example:
MAT-000001

Raw Wood

↓

CRAFT-000010

Timber Processing

↓

MAT-000020

Prepared Timber
Material History
Materials maintain history.
Example:
[
 {
  "event":

  "Harvested",

  "date":

  "Day 5",

  "location":

  "Northern Forest"
 },

 {
  "event":

  "Processed",

  "date":

  "Day 8"
 }
]
Material History Events
Examples:
discovered
harvested
gathered
processed
refined
depleted
regenerated
Material Discovery
A material cannot be used before discovery.
Example:
Unknown:
Rare Blue Ore
↓
Observation:
Mineral discovered
↓
Knowledge Update:
Ore identification learned
Material Discovery Relationship
Discovery affects:
Geography
Where it exists.
Knowledge
What the Chronicle understands.
Materials
What resource becomes available.
Material Update Pipeline
Whenever materials change:
Examples:
gathering
harvesting
processing
crafting
construction
depletion
regeneration
Process:
Player Action

↓

Validate Resource Interaction

↓

Update Material Database

↓

Update Related Systems

↓

Record History

↓

Generate Narration
Materials Rules
The simulation must never:
create materials from nothing
ignore resource locations
duplicate resources
regenerate impossible resources
consume materials without updating records
create crafted objects without material consumption
Future Expansion
Possible future materials systems:
metallurgy
chemistry
alloys
agriculture chains
ecosystems
resource economy
industrial production
Final Principle
The Materials System answers:
"What resources exist?"

The Crafting System answers:
"How are resources transformed?"

The Item System answers:
"What physical objects exist?"

The Construction System answers:
"What permanent things are built?"

Materials are the foundation from which civilization emerges.
```