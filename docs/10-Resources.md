10 – Materials
Project Draugr
Civilizations are not built from items. They are built from materials.

Purpose
This document defines the material system used throughout Project Draugr.
Materials represent raw and processed substances that can be gathered, harvested, refined, transformed, and consumed during crafting and construction.
Materials are the foundation of every manufactured object.
Unlike Items, Materials are resources rather than unique world entities.
Design Philosophy
Project Draugr distinguishes three different concepts:
Materials
Resources used to create physical objects.
Examples:
Stone
Clay
Timber
Plant Fiber
Leather
Items
Finished physical objects created from materials.
Examples:
Stone Axe
Wooden Bowl
Rope
Backpack
Spear
Items are defined in:
14.2 – Item System
Infrastructure
Permanent structures built using items and materials.
Examples:
Cabin
Workshop
Storage Rack
Bridge
Infrastructure is defined in:
15 – Infrastructure
Material Lifecycle
Every material follows a natural progression.
Natural Resource
        │
        ▼
Gathered Material
        │
        ▼
Processed Material
        │
        ▼
Crafted Item
        │
        ▼
Infrastructure
Example:
Tree

↓

Log

↓

Prepared Timber

↓

Wooden Plank

↓

Workbench

↓

Workshop
Material Categories
Materials are grouped according to their origin.
Plant Materials
Examples:
Branches
Logs
Timber
Bark
Leaves
Grass
Reeds
Bamboo
Plant Fiber
Cotton
Hemp
Flax
Straw
Stone Materials
Examples:
Rock
Flint
Granite
Limestone
Sandstone
Obsidian
Earth Materials
Examples:
Clay
Mud
Sand
Gravel
Soil
Animal Materials
Examples:
Leather
Fur
Bone
Antler
Horn
Sinew
Tendon
Feather
Fat
Hide
Teeth
Shell
Metal Materials
Examples:
Copper Ore
Iron Ore
Tin Ore
Silver Ore
Gold Ore
Processed:
Copper Ingot
Iron Ingot
Steel
Bronze
Organic Materials
Examples:
Resin
Sap
Charcoal
Ash
Wax
Pitch
Textile Materials
Examples:
Thread
Yarn
Rope
Cloth
Linen
Wool
Silk
Liquid Materials
Examples:
Fresh Water
Salt Water
Oil
Resin
Animal Fat
Material States
Materials may exist in different processing states.
Examples:
Raw
Log
Stone
Clay
Processed
Prepared Timber
Sharpened Stone
Refined Fiber
Refined
Iron Ingot
Bronze Plate
Steel Rod
Manufactured
Rope
Cloth
Leather Sheet
Material Properties
Every material possesses physical properties.
These properties influence crafting, construction, durability, and environmental behavior.
Examples include:
Weight
Volume
Density
Hardness
Flexibility
Brittleness
Elasticity
Combustibility
Water Resistance
Corrosion Resistance
Thermal Conductivity
The exact values are internal simulation data and are not shown directly to players.
Material Quality
Materials may vary in quality depending on their source and processing.
Examples:
Poor
Common
Good
Excellent
Masterwork
Quality influences the final result of crafted items.
Higher-quality materials generally produce higher-quality equipment.
Material Acquisition
Materials enter the world through realistic activities.
Examples:
Gathering
Branches
Grass
Stones
Clay
Harvesting
Trees
Crops
Herbs
Mining
Stone
Ore
Salt
Hunting
Hide
Bone
Meat
Fat
Fishing
Fish
Scales
Bones
Salvaging
Broken Furniture
Ruins
Abandoned Structures
Material Processing
Raw materials often require refinement before use.
Examples:
Log
↓

Prepared Timber

↓

Wooden Planks
Plant Fiber
↓

Twisted Fiber

↓

Cordage

↓

Rope
Iron Ore
↓

Smelting

↓

Iron Ingot
Processing requires:
knowledge
tools
workstation
time
energy
Material Consumption
Crafting and construction consume materials.
Consumed materials are permanently removed from available resources.
Example:
Craft:
Stone Axe
Consumes:
Stone ×1
Wooden Handle ×1
Plant Fiber ×2
After crafting:
The materials no longer exist as independent resources.
They become part of the newly created Item.
Material Recovery
Destroyed items may return some materials to the world.
Example:
Broken Wooden Chair
Recovered:
Timber ×2
Charcoal ×1 (if burned)
Recovery depends on:
item condition
destruction method
player skill
available tools
Recovery is not guaranteed.
Material Storage
Materials may exist:
on the ground
inside containers
inside infrastructure
carried by a Chronicle
Unlike Items, common materials may be represented as quantities rather than individually tracked entities during the MVP.
Future versions may optionally support unique material batches where provenance and quality become significant.
Relationship with Other Systems
Materials interact with:
Crafting
Construction
Infrastructure
Item System
Storage System
Economy
Knowledge Progression
They serve as the foundation for technological advancement.
Future Expansion
Future versions may introduce:
Moisture content
Grain direction in wood
Material fatigue
Aging
Weathering
Chemical reactions
Purity
Alloy composition
Biological decay
Radioactive materials
Magical materials (if discovered naturally within the world's lore)
Final Principle
Materials are not finished products.
They are the building blocks from which civilizations emerge.
Every tool, weapon, shelter, document, and machine ultimately begins as a collection of materials shaped through knowledge, labor, and time.