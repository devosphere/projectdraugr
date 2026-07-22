# 14 – Crafting

> **Project Draugr**
>
> *Crafting is the deliberate transformation of materials into functional objects.*

---

# Purpose

This document defines the crafting system used throughout Project Draugr.

Crafting allows Chronicles to transform raw and processed materials into usable physical objects.

Unlike Construction, which permanently alters the world, Crafting produces portable objects that become World Entities.

Every crafted object exists physically within the world.

---

# Design Philosophy

Crafting follows one principle:

> **Nothing appears from nothing.**

Every crafted object requires:

- materials
- knowledge
- appropriate tools
- time
- labor
- suitable workstations (when required)

Crafting never bypasses physical reality.

---

# Crafting Lifecycle

Every crafted object follows the same production pipeline.

```text
Need
        │
        ▼
Knowledge
        │
        ▼
Material Preparation
        │
        ▼
Crafting
        │
        ▼
Finished Item
        │
        ▼
World Entity Registration
```

After completion, the crafted object is registered as a permanent World Entity.

---

# Crafting Requirements

Every recipe specifies one or more requirements.

## Materials

Examples:

- Stone
- Timber
- Leather
- Rope
- Cloth
- Iron Ingot

Materials are consumed during crafting.

---

## Tools

Examples:

- Stone Knife
- Hammer
- Chisel
- Axe
- Needle
- Pickaxe

Some tools are mandatory.

Others simply improve efficiency or quality.

---

## Knowledge

The Chronicle must possess sufficient knowledge.

Examples:

- Stone Knapping
- Leatherworking
- Sewing
- Carpentry
- Blacksmithing

Without knowledge, crafting either:

- cannot begin, or
- produces extremely poor results.

---

## Workstations

Some recipes require specialized workstations.

Examples:

General Workbench

- handles
- wooden bowls
- furniture

Stone Workbench

- stone blades
- polished stone
- stone bricks

Weaving Workbench

- cloth
- rope
- baskets

Sewing Workbench

- clothing
- bags
- leather equipment

Forge (future)

- metal tools
- weapons
- armor

Simple recipes may be crafted by hand.

---

# Crafting Categories

## Tools

Examples:

- Stone Axe
- Hammer
- Knife
- Hoe
- Shovel

---

## Weapons

Examples:

- Spear
- Bow
- Arrow
- Club
- Sling

---

## Clothing

Examples:

- Shirt
- Hood
- Boots
- Gloves
- Cloak

---

## Containers

Examples:

- Backpack
- Basket
- Sling Carrier
- Sack
- Barrel

---

## Furniture

Examples:

- Chair
- Table
- Shelf
- Bed
- Storage Rack

---

## Utility

Examples:

- Torch
- Rope
- Bucket
- Fishing Net
- Water Skin

---

## Documents

Examples:

- Maps
- Blueprints
- Training Manuals
- Procedures
- Engineering Drawings

Documents are physical crafted objects.

---

# Crafting Quality

Crafted quality depends on:

- knowledge
- material quality
- tool quality
- workstation quality
- Chronicle condition
- environmental conditions

Possible quality levels:

- Poor
- Common
- Good
- Excellent
- Masterwork

Quality affects:

- durability
- efficiency
- appearance
- resale value (future)

---

# Crafting Time

Crafting consumes simulation time.

Factors include:

- recipe complexity
- available tools
- workstation
- Chronicle fatigue
- injuries
- skill level

Time always progresses naturally.

---

# Failure

Crafting is not guaranteed to succeed.

Failure may result from:

- insufficient knowledge
- missing materials
- damaged tools
- poor workmanship
- interruptions
- environmental hazards

Failure may:

- waste materials
- damage tools
- produce inferior items
- require restarting

---

# Repair

Many crafted objects may be repaired.

Repair requires:

- appropriate materials
- tools
- knowledge

Repair restores integrity but may not completely recover an item depending on damage.

---

# Refinement

Some items may undergo refinement.

Examples:

Log

↓

Prepared Timber

↓

Polished Timber

↓

Furniture

Or:

Stone

↓

Rough Blade

↓

Sharpened Blade

↓

Stone Knife

Refinement increases quality before final crafting.

---

# World Entity Creation

Every successfully crafted object immediately becomes a World Entity.

The simulation automatically:

1. Creates a new Entity ID.
2. Registers the object in the World Entity Registry.
3. Assigns its owner.
4. Assigns its location.
5. Places it into the appropriate inventory or container.

Crafted objects never exist outside the world's persistent database.

---

# Relationship with Other Systems

Crafting depends upon:

- Materials
- Knowledge Progression
- Item System
- World Entity System
- Storage System
- Physiology

Crafting produces Items.

Construction consumes both Items and Materials.

---

# Future Expansion

Future versions may introduce:

- batch crafting
- apprentices
- production queues
- automated workshops
- machine-assisted manufacturing
- quality specialization
- regional crafting techniques
- experimental recipes
- recipe discovery

---

# Final Principle

Crafting is the process through which knowledge becomes physical reality.

Materials alone build nothing.

Knowledge without labor creates nothing.

Only when both come together does a new object permanently enter the world.