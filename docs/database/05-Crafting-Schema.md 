# 05-Crafting-Schema.md

# Project Draugr — Crafting System Schema

> **Crafting is not creation from nothing. Crafting is knowledge applied to materials.**

---

# Purpose

This document defines the persistent crafting system of Project Draugr.

The Crafting System manages:

- known recipes
- production methods
- refinement processes
- manufacturing techniques
- crafting requirements
- active crafting operations
- technological development

---

# Authority

This schema controls:

database/progression/crafting.json

The Crafting Database is the authoritative source of truth for all known crafting methods and active crafting processes.

---

# Crafting Philosophy

Crafting requires:

- knowledge
- materials
- tools
- workstation
- time
- physical effort

The simulation must never treat crafting as an instant action.

---

# Crafting Relationship

Crafting connects:

Knowledge System
↓
Crafting Knowledge
↓
Material Availability
↓
Tools / Workstations
↓
Crafting Process
↓
Item Creation
↓
Item Database

---

# Crafting Does Not Create Knowledge Automatically

Example:

The Chronicle crafts a stone axe.

Result:

Item Created:
ITEM-000001
Stone Axe

Knowledge:

Primitive Axe Crafting
Unlocked

only if the Chronicle learned the technique.

---

# Crafting Entity Identity

Every crafting recipe receives a permanent Crafting ID.

Example:

CRAFT-000001

---

# Crafting ID Rules

Crafting IDs are:

- globally unique
- permanent
- immutable
- never reused

Removed or obsolete recipes remain in history.

---

# Crafting Database Structure

Example:

```json
{
  "crafting_methods":

  [

    {
      "crafting_id":
      "CRAFT-000001",

      "identity": {},

      "requirements": {},

      "process": {},

      "knowledge": {},

      "history": []
    }

  ]
}
Identity Data
Defines the crafting method.
Example:
{
 "crafting_id":

 "CRAFT-000001",

 "name":

 "Primitive Stone Axe",

 "category":

 "Tool",

 "description":

 "A basic axe made from stone, wood, and binding materials."
}
Crafting Categories
Every recipe belongs to a category.
Tool Crafting
Examples:
stone axe
hammer
knife
shovel
fishing hook
Weapon Crafting
Examples:
spear
bow
arrows
traps
defensive tools
Weapons are treated as survival technologies.
The simulation evaluates:
materials
knowledge
time
skill
It does not restrict creation based on category.
Clothing Crafting
Examples:
shirts
trousers
footwear
fur clothing
armor layers
Container Crafting
Examples:
baskets
bags
backpacks
storage containers
Furniture Crafting
Examples:
chairs
shelves
tables
beds
Construction Component Crafting
Examples:
beams
boards
stone blocks
ropes
supports
Food Processing
Examples:
dried meat
smoked food
preserved vegetables
cooked meals
Material Processing
Examples:
charcoal
refined stone
processed fibers
treated leather
Recipe Requirements
Every crafting method defines requirements.
Example:
{
 "requirements":

 {

 "materials":

 [

  {
   "material":

   "Wood",

   "amount":

   1
  },

  {
   "material":

   "Stone",

   "amount":

   1
  }

 ],

 "tools":

 [

  "Stone Hammer"

 ],

 "workstation":

 "None"
 }
}
Material Requirements
Materials may come from:
Materials Database
Raw resources:
Examples:
logs
stone
clay
fibers
Item Database
Processed components:
Examples:
ITEM-000010

Prepared Wooden Handle
Tool Requirements
Some crafting requires tools.
Example:
Simple:
Stone Knife

Tool Requirement:

None
Advanced:
Wooden Furniture

Tool Requirement:

Woodworking Tools
Workstation Requirements
Workstations improve crafting efficiency.
A workstation may be:
Item-Based Workstation
Example:
ITEM-000100

Woodworking Table
Infrastructure-Based Workstation
Example:
INFRA-000001

Wood Workshop
Workstation Effects
Workstations may modify:
crafting speed
quality
available recipes
production complexity
Example:
Without workstation:
Basic wooden chair

Long crafting time

Lower consistency
With workstation:
Woodworking table

Reduced crafting time

Improved quality
Crafting Process
Crafting follows:
Intent

↓

Recipe Identification

↓

Requirement Check

↓

Material Reservation

↓

Crafting Time

↓

Skill Evaluation

↓

Item Creation

↓

Database Update

↓

Narration
Active Crafting Projects
Long crafting actions are tracked.
Example:
{
 "active_projects":

 [

 {
  "crafting_id":

  "CRAFT-000010",

  "item":

  "Wooden Chair",

  "progress":

  "Frame completed",

  "started":

  "Day 20"
 }

 ]

}
Crafting Progress
The Chronicle does not see numerical progress.
Incorrect:
Crafting:

75%
Correct:
Wooden Chair

Leg structure completed.

Seat assembly remaining.
Crafting Failure
Crafting may fail.
Possible causes:
insufficient skill
poor materials
wrong technique
damaged tools
environmental problems
Example:
Attempted:

Stone Knife

Result:

Failed

Cause:

Stone fractured during shaping.
Crafting Quality
Items may have different quality states.
Examples:
Poor

Basic

Good

Refined

Exceptional
Quality depends on:
skill
tools
workstation
materials
process
Crafting Does Not Modify Existing Items Automatically
Example:
Crafting:
Better Stone Axe Technique
does not upgrade:
ITEM-000001

Old Stone Axe
The Chronicle must:
repair
modify
rebuild
replace
Item Creation Relationship
Successful crafting creates an Item Entity.
Example:
Crafting:
CRAFT-000001

Stone Axe
Creates:
ITEM-000050

Stone Axe
The item receives:
Item ID
condition
location
owner
history
Crafting History
Every discovered crafting method retains history.
Example:
[
 {
  "event":

  "Discovered",

  "date":

  "Day 5",

  "discoverer":

  "CHR-000001"
 },

 {
  "event":

  "Improved",

  "date":

  "Day 30"
 }
]
Knowledge Relationship
Crafting knowledge is stored separately.
Example:
Crafting Database:
CRAFT-000001

Primitive Stone Axe
Knowledge Database:
CHR-000001 knows:

Primitive Stone Axe Construction
Recipe Discovery
Recipes may be obtained through:
experimentation
observation
teaching
documents
trial and error
inherited knowledge
Documentation Relationship
A recipe may exist as:
Knowledge
Internal understanding.
Document
Physical record.
Example:
Knowledge:
Stone Tool Production
Document:
ITEM-000200

Stone Tablet:

Stone Tool Instructions
Crafting and Technology Progression
Crafting represents technological development.
Example:
Primitive:
Stone Tools
Bone Tools
Fiber Clothing
↓
Developing:
Woodworking
Advanced Storage
Improved Shelter
↓
Advanced:
Metalworking
Engineering
Manufacturing
Crafting Database Update Pipeline
Whenever crafting occurs:
Examples:
start crafting
finish crafting
fail crafting
discover recipe
improve method
Process:
Player Action

↓

Validate Crafting Rules

↓

Check Requirements

↓

Consume Materials

↓

Create / Update Crafting Record

↓

Create Item Entity

↓

Update Knowledge

↓

Record History

↓

Generate Narration
Crafting Rules
The simulation must never:
create items without materials
create items without time
unlock knowledge without discovery
ignore workstation requirements
skip crafting failures
delete crafting history
Future Expansion
Possible future crafting systems:
specialization
apprenticeships
industrial production
automation
NPC craftsmen
production chains
civilization technology trees
Final Principle
The Crafting System answers:
"How does the Chronicle create things?"

The Materials System answers:
"What resources exist?"

The Item System answers:
"What objects exist?"

The Infrastructure System answers:
"What facilities exist?"

Crafting is the bridge between knowledge, resources, and physical reality.
```