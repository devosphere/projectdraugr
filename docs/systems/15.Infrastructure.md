# 15 – Infrastructure

> **Project Draugr**
>
> *Infrastructure is civilization made permanent.*

---

# Purpose

This document defines the Infrastructure System of Project Draugr.

Infrastructure represents permanent, immovable structures that physically alter the world.

Unlike Items, infrastructure cannot normally be carried.

Unlike Construction, infrastructure is the finished result.

Infrastructure becomes part of the world's history and remains until physically destroyed.

---

# Design Philosophy

Infrastructure follows one immutable principle:

> **Civilizations are remembered by what they build.**

Infrastructure exists independently of the Chronicle that created it.

If a Chronicle dies:

- their axe may rust,
- their maps may be lost,

but their bridge may still stand decades later.

---

# Relationship with Other Systems

Infrastructure is created through:

```
Construction

↓

Completed Structure

↓

Infrastructure
```

Infrastructure is NOT an Item.

Infrastructure is NOT a Container.

Infrastructure is its own persistent world entity.

However, infrastructure may:

- contain Items
- contain Containers
- contain Workstations
- contain Storage Systems

---

# Infrastructure ID

Every infrastructure receives a permanent identifier.

Example

```text
INFRA-000001
```

Infrastructure IDs are:

- globally unique
- immutable
- never reused
- permanent

Destroyed infrastructure retains its historical ID.

---

# Infrastructure Record

Every infrastructure contains:

- Infrastructure ID
- Name
- Category
- Description
- Builder
- Construction Date
- Current Condition
- Current State
- World Location
- Structural Integrity
- Last Maintenance
- Notes

Future versions may add:

- ownership
- blueprint reference
- engineering rating

---

# Infrastructure Categories

---

## Shelter

Protects inhabitants from the environment.

Examples

- Lean-to
- Cabin
- Stone House
- Longhouse
- Lodge

---

## Production

Infrastructure used for manufacturing.

Examples

- Workshop
- Forge
- Kiln
- Loom House
- Tannery

---

## Storage

Large permanent storage facilities.

Examples

- Warehouse
- Granary
- Root Cellar
- Armory

---

## Transportation

Allows movement.

Examples

- Road
- Bridge
- Stairway
- Dock
- Pier

---

## Agriculture

Supports food production.

Examples

- Farm
- Irrigation
- Animal Pen
- Compost Area
- Orchard

---

## Utility

General infrastructure.

Examples

- Well
- Water Basin
- Smokehouse
- Drying Rack
- Fire Pit

---

## Defensive

Protects settlements.

Examples

- Fence
- Palisade
- Gate
- Wall
- Watchtower

---

## Research

Supports technological development.

Examples

- Library
- Laboratory
- Observatory
- Archive

---

# Workstations

Workstations are considered Infrastructure.

A workstation is permanently installed and designed to improve crafting efficiency.

Examples include:

- General Workbench
- Carpentry Workbench
- Stone Mason Workbench
- Sewing Table
- Weaving Loom
- Leatherworking Bench
- Forge
- Smelting Furnace
- Pottery Wheel
- Drying Rack

A workstation may:

- unlock recipes
- reduce crafting time
- improve crafting quality
- reduce material waste

Multiple Chronicles may use the same workstation.

---

# Infrastructure States

Infrastructure always exists in exactly one state.

Possible states include:

```
Planned

Under Construction

Operational

Damaged

Abandoned

Collapsed

Destroyed
```

State changes update the infrastructure record.

---

# Structural Integrity

Infrastructure possesses integrity.

Examples

```
Excellent

Good

Worn

Damaged

Critical

Collapsed
```

Integrity decreases through:

- weather
- fire
- flooding
- combat
- neglect
- age

---

# Maintenance

Infrastructure requires periodic maintenance.

Maintenance depends upon:

- material
- weather
- usage
- engineering quality

Well-maintained structures last significantly longer.

Neglected structures eventually deteriorate.

---

# Infrastructure Components

Infrastructure may contain multiple integrated systems.

Example

```
Workshop

├── Carpentry Workbench
├── Stone Workbench
├── Tool Rack
├── Storage Shelf
└── Drying Area
```

These remain part of the same infrastructure.

---

# Interior Organization

Infrastructure defines rooms or functional areas.

Example

```
Warehouse

├── Tool Storage
├── Material Storage
├── Food Storage
└── Document Shelf
```

Organization improves operational efficiency but does not automatically happen.

Chronicles must intentionally arrange infrastructure.

---

# Permanent World Change

Infrastructure permanently modifies geography.

Examples

Before

```
Forest Clearing
```

↓

After

```
Main Camp

• Cabin
• Workshop
• Warehouse
• Fire Pit
• Road
```

Future Chronicles encounter the modified world.

---

# Abandonment

Infrastructure remains after abandonment.

It slowly deteriorates over time.

Examples

```
Operational

↓

Abandoned

↓

Weathered

↓

Ruined

↓

Collapsed
```

Ruins become part of world history.

---

# Destruction

Infrastructure may be destroyed through:

- fire
- flood
- earthquake (future)
- warfare
- deliberate demolition
- structural failure

Destroyed infrastructure may leave salvageable materials.

---

# Interaction with Items

Infrastructure does not replace Items.

Instead, it houses them.

Example

```
Warehouse

↓

Storage Rack

↓

Wooden Crate

↓

Stone Axe
```

The Storage System manages the Items.

Infrastructure provides the physical environment.

---

# Interaction with Knowledge

Infrastructure may enable new knowledge.

Example

Building:

```
Observatory
```

allows:

- Astronomy
- Navigation
- Seasonal Forecasting

The structure enables learning.

It does not automatically grant knowledge.

---

# Future Expansion

Future versions may introduce:

- electricity
- plumbing
- industrial production
- transportation networks
- defensive engineering
- architectural styles
- modular upgrades
- automated infrastructure
- NPC workforce
- city planning

---

# Final Principle

Infrastructure is the physical legacy of civilization.

Items may be carried away.

Chronicles may die.

Infrastructure remains as proof that someone once lived, worked, built, and changed the world.