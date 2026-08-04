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
- a suitable workstation *(optional — it eases and speeds the work; never required, see Workstations below)*

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

**As built (V69, DR-0022): a workstation EASES a craft — it never *requires* it, and never decides its quality.**
Skilled hands with basic tools make superior work bare-handed (ancient joiners cut fine mortises, weavers made
fine cloth, masons dressed stone square with the ground, cord, and pegs). So quality is **majorly the craftsman**
— skill/care + materials — and a workstation adds only **efficiency** (less waste; a follow-up will add less
time/effort) plus a **minor, bounded quality assist** (a stable held surface aids precision at the margin: the
attempt is lifted one step, still capped against the materials, so it never rescues poor work). **Bare-handed
always works**, just rougher-yielding. Gating a craft behind a bench would re-introduce exactly the restriction
Phase 1 exists to remove. This is why a settlement builds a manufacturing district: **throughput and less waste**,
not a shortcut to quality.

Detection is via the unified reachability model — a bench standing in your on-site workshop counts. `station_kind`
on `material_process` names which of the 14 precision/large operations each of the three benches eases:

**Woodworking bench** (`woodworking_bench`) — mortise, tenon, dovetail, lap & scarf joints, edge-joined panels,
pegged frames, dowels, wooden components.

**Stoneworking bench** (`stoneworking_bench`) — dressing foundation/construction stone, knapping tool stone.

**Upright loom** (`loom`) — weaving textile and wool cloth.

Build them via **`CRAFT_WORKSTATION`** ("build a woodworking bench", "set up a loom") — they are sited, reachable
structures like other furniture.

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