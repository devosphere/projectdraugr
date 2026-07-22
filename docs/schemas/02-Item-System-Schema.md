02-Item-System-Schema.md
# 02-Item-System-Schema.md

# Project Draugr — Item Entity System Schema

> **Every object has a history. Every object has a place. Every object matters.**

---

# Purpose

This document defines the persistent item entity system of Project Draugr.

The Item System manages every physical object that exists in the world.

Examples:

- tools
- weapons
- clothing
- armor
- containers
- furniture
- documents
- equipment
- consumables
- crafted objects
- harvested objects
- manufactured components

---

# Authority

This schema controls:

database/entities/items.json

The Item Database is the authoritative source of truth for all physical objects.

Nothing physical may exist outside this registry.

---

# Core Philosophy

Project Draugr does not use anonymous items.

Incorrect:

The Chronicle has a stone axe.

Correct:

ITEM-000001
Stone Axe
Created:
Day 1
Current Owner:
CHR-000001
Condition:
Good

Every object is an individual world entity.

---

# Item Entity Identity

Every physical object receives a permanent Item ID.

Example:

ITEM-000001

---

# Item ID Rules

Item IDs are:

- globally unique
- permanent
- immutable
- never reused

Destroyed items retain their history.

Example:

ITEM-000001
Stone Axe
Destroyed
↓
ITEM-000002
Bone Knife

Never:

ITEM-000001
New Object

---

# Item Database Structure

Example:

```json
{
  "items": [
    {
      "item_id": "ITEM-000001",

      "identity": {},

      "classification": {},

      "physical_state": {},

      "ownership": {},

      "location": {},

      "history": []
    }
  ]
}
Identity Data
Defines what the object is.
Example:
{
  "item_id": "ITEM-000001",

  "name": "Stone Axe",

  "category": "Tool",

  "subcategory": "Axe",

  "version": "Primitive",

  "description":
  "A wooden handle with a sharpened stone head."
}
Item Categories
Every item belongs to a category.
Tools
Examples:
knife
axe
hammer
pickaxe
shovel
saw
Weapons
Examples:
spear
bow
arrow
dagger
club
Weapons are treated as physical tools.
The simulation does not restrict crafting based on category.
Wearables
Examples:
clothing
armor
footwear
accessories
Wearables require equipment compatibility rules.
Containers
Examples:
baskets
backpacks
bags
storage boxes
Containers may contain other item entities.
Furniture
Examples:
chairs
tables
shelves
beds
Documents
Examples:
maps
blueprints
written procedures
stone records
Documents are physical entities.
Components
Examples:
handles
bindings
stone blades
fibers
processed parts
Consumables
Examples:
food
water
medicine
materials consumed during processes
Physical State
Every item has a current physical state.
Example:
{
 "state":

 "Carried"
}
Valid Item States
Stored

Carried

Equipped

Placed

Installed

Mounted

In Use

Dropped

Buried

Lost

Destroyed

Consumed
State Rules
State changes must always be recorded.
Example:
Before:
State:

Dropped
Player action:
Pick up stone axe
After:
State:

Carried
Ownership
Tracks possession.
Example:
{
 "owner":

 "CHR-000001"
}
Ownership Rules
Items may belong to:
Chronicle
NPC
Settlement
Faction
No owner
Example:
{
"owner":

"None"
}
A dropped object is not automatically owned.
Location System
Every item must always have a location.
Location contains:
General Location
Examples:
Main Camp

Forest

River

Mountain
Specific Location
Examples:
Workshop Table

Storage Shelf

Ground Near Firepit

Backpack Slot
Example:
{
 "location":

 {
   "zone":
   "Main Camp",

   "specific":
   "Wood Storage Shelf"
 }
}
Container Relationship
Items may exist inside containers.
Example:
Backpack:
ITEM-000010

Leather Backpack
Contains:
ITEM-000011

Stone Knife

ITEM-000012

Dry Food Bundle
Container relationship:
{
 "container":

 "ITEM-000010"
}
Equipment Relationship
Equipped items require:
equipment slot
compatibility check
Example:
{
 "equipment":

 {
  "slot":
  "Left Hand",

  "compatible":
  true
 }
}
Equip Validation
Before equipping:
Check:
Item category
Body slot compatibility
Item state
Ownership
Condition
Invalid:
Equip Horse

Slot:
Head
Reason:
Horse category incompatible with equipment slot.
Valid:
Equip Fur Hood

Slot:
Upper Head
Item Condition
Represents physical integrity.
Examples:
New

Good

Worn

Damaged

Broken

Destroyed
Condition Changes
Caused by:
use
impact
weather
decay
maintenance
repair
Example:
Stone Axe

Good

↓

Repeated chopping

↓

Worn
Item Durability Philosophy
The MVP avoids numerical durability.
Incorrect:
Durability:
73%
Correct:
Condition:
Worn
Item History
Every item keeps historical events.
Example:
[
 {
  "event":

  "Crafted",

  "date":

  "Day 1",

  "actor":

  "CHR-000001"
 },

 {
  "event":

  "Repaired",

  "date":

  "Day 15"
 }
]
History Events
Examples:
Crafted
Modified
Repaired
Maintained
Equipped
Stored
Dropped
Lost
Found
Destroyed
Consumed
Item Versioning
Items may evolve.
Example:
Primitive Stone Axe:
Version 1.0
Modified with better binding:
Version 1.1
Improved stone head:
Version 2.0
The original identity remains.
Item Destruction
Destroyed items are not deleted.
Example:
{
 "state":

 "Destroyed",

 "destroyed_date":

 "Day 50"
}
Destroyed Item Rule
Destroyed items:
remain searchable
retain history
cannot be used
cannot be equipped
Item Creation Pipeline
Whenever a new object is created:
Action

↓

Validate Creation

↓

Generate Item ID

↓

Create Entity

↓

Register Location

↓

Register Owner

↓

Register History

↓

Generate Narration
Item Modification Pipeline
Whenever an item changes:
Examples:
repair
upgrade
refinement
damage
Process:
Action

↓

Find Item Entity

↓

Update State

↓

Append History

↓

Save Database

↓

Generate Narration
Required Item Fields
Every item must contain:
Item ID

Name

Category

Description

Condition

State

Owner

Location

Container

Created Date

Last Modified

History
World Persistence Rule
Items persist until:
destroyed
consumed
lost permanently
Examples:
A dropped axe remains.
A broken chair remains.
A written stone tablet remains.
A harvested tree does not return unless the world system allows regeneration.
Database Update Priority
Whenever an action involves an item:
Examples:
craft
pick up
drop
equip
unequip
store
consume
repair
destroy
modify
The order is:
Player Action

↓

Item Database Update

↓

Other System Updates

↓

Narration Generation
Narration is never the source of truth.
Final Principle
The Item System exists because the world remembers objects.
A stone axe is not just:
"an axe."
It is:
ITEM-000001

A specific object.

Created by someone.

Used somewhere.

Changed by time.

Carrying a history.
Every object is part of the world's memory.