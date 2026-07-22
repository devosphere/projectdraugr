# 10-World-State-Schema.md

# Project Draugr — World State System Schema

> **The world does not reset when the player stops looking.**

---

# Purpose

This document defines the persistent world simulation state system of Project Draugr.

The World State System represents the current physical condition of the simulated world.

It records:

- environmental changes
- resource availability
- ecosystem changes
- terrain changes
- natural events
- world degradation
- regeneration
- permanent alterations

---

# Authority

This schema controls:

database/world/world-state.json

The World State Database is the authoritative source of truth for the current condition of the world.

---

# World State Philosophy

The world exists independently from the Chronicle.

The Chronicle is only one entity inside the world.

The world continues through:

- time progression
- weather
- seasons
- wildlife activity
- natural regeneration
- resource depletion
- environmental events

---

# Core Principle

The simulation must distinguish:

## Historical State

"What happened?"

Controlled by:

08-History-Schema.md

---

## Geographic Knowledge

"What places are known?"

Controlled by:

09-Geography-Schema.md

---

## World State

"What exists now?"

Controlled by:

10-World-State-Schema.md

---

# World State Entity Identity

Every major world state entity receives a permanent World State ID.

Example:

WORLD-000001

---

# World State ID Rules

World State IDs are:

- globally unique
- permanent
- immutable
- never reused

---

# World State Database Structure

Example:

```json
{
 "world_entities":

 [

  {

   "world_id":

   "WORLD-000001",

   "identity": {},

   "environment": {},

   "resources": {},

   "ecosystem": {},

   "changes": {},

   "history": []

  }

 ]
}
World Entity Identity
Defines the tracked world element.
Example:
{
 "world_id":

 "WORLD-000001",

 "name":

 "Northern Forest",

 "category":

 "Environment"
}
World State Categories
Every world entity belongs to a category.
Environment
Examples:
forests
rivers
mountains
wetlands
caves
plains
Resources
Examples:
forests
mineral deposits
clay deposits
wildlife populations
fishing areas
Ecosystems
Examples:
animal populations
plant populations
predator territories
migration zones
Weather Systems
Examples:
storms
droughts
seasonal conditions
temperature patterns
Terrain
Examples:
erosion
flooding
destroyed land
modified terrain
Environmental Data
Example:
{
 "environment":

 {

 "terrain":

 "Dense Forest",

 "climate":

 "Temperate",

 "season":

 "Summer",

 "condition":

 "Healthy"

 }

}
Resource State
Resources are tracked based on availability.
Example:
{
 "resources":

 [

 {

 "type":

 "Timber",

 "state":

 "Abundant",

 "regeneration":

 "Active"

 }

 ]

}
Resource States
Possible states:
Abundant

Available

Reduced

Scarce

Depleted

Regenerating

Destroyed
Resource Depletion
Resources change through world interaction.
Example:
Player action:
Cut 50 trees
World update:
Forest Timber

Available

↓

Reduced
Resource Regeneration
Natural resources may recover.
Affected by:
climate
season
ecosystem health
harvesting intensity
time
Example:
Forest

Reduced

↓

Regenerating

↓

Available
No Instant Regeneration
Invalid:
Cut tree

↓

Next day:

Tree restored
Valid:
Cut tree

↓

Years later

Forest slowly recovers
Ecosystem State
Tracks living systems.
Example:
{
 "ecosystem":

 {

 "wildlife":

 "Stable",

 "vegetation":

 "Healthy",

 "predator_activity":

 "Normal"

 }

}
Wildlife Population States
Examples:
Growing

Stable

Reduced

Scarce

Endangered

Extinct
Hunting Impact
Wildlife is affected by:
hunting
trapping
habitat destruction
seasons
disease
migration
Example:
Rabbit Population

Stable

↓

Reduced
Environmental Events
World events create state changes.
Examples:
storms
fires
floods
earthquakes
drought
volcanic activity
Event State Example
{
 "environmental_event":

 {

 "type":

 "Forest Fire",

 "severity":

 "Major",

 "affected_area":

 "Northern Forest"

 }

}
Terrain Modification
The world remembers physical changes.
Examples:
Permanent:
Bridge constructed

Road created

Forest cleared

Mine opened
Temporary:
Mud after rain

Snow coverage

Flood water
Weather State
Current weather belongs to world state.
Example:
{
 "weather":

 {

 "condition":

 "Rain",

 "intensity":

 "Moderate",

 "duration":

 "3 hours"

 }

}
Seasonal State
The world tracks seasonal progression.
Example:
{
 "season":

 {

 "current":

 "Autumn",

 "day":

 45

 }

}
World Time Progression
The world continuously changes.
Time affects:
weather
wildlife
resources
vegetation
structures
NPC activity
World Simulation Tick
Every simulation update follows:
Time Advancement

↓

Environmental Processing

↓

Resource Processing

↓

Wildlife Processing

↓

Infrastructure Decay

↓

World State Update

↓

Narration Generation
Player Interaction With World State
Player actions may modify world state.
Examples:
Gathering
Action:
Harvest wild plants
World effect:
Plant availability reduced
Logging
Action:
Cut trees
World effect:
Forest density reduced
Hunting
Action:
Hunt deer
World effect:
Deer population reduced
Construction
Action:
Build road
World effect:
Terrain modified
World Persistence Rules
The simulation must never:
restore destroyed resources automatically
ignore player impact
forget environmental changes
reset ecosystems
remove abandoned structures
create resources from nowhere
Relationship With Other Systems
Materials System
Uses:
World State
to determine resource availability.
Example:
Forest state affects timber availability.
Geography System
Uses:
World State
to represent current geographic conditions.
Infrastructure System
Modifies:
World State
through construction.
Examples:
roads
cleared land
settlements
History System
Records:
Major World State Changes
Chronicle System
Records:
Player-caused world impact
World State Update Pipeline
Whenever the world changes:
World Event

↓

Determine Affected Entities

↓

Update World State Database

↓

Create History Record if Significant

↓

Update Related Systems

↓

Generate Narration
Example
Chronicle clears forest area.
Before:
Forest Density:

High
Action:
Clear land for settlement
Updates:
World State:
Forest Density:

Reduced
Infrastructure:
Settlement Zone Created
History:
Major Land Clearing Event
Geography:
New Settlement Area Known
Future Expansion
Possible future systems:
global climate simulation
tectonic activity
ocean systems
civilization expansion
ecological food chains
planetary simulation
geological aging
long-term world evolution
Final Principle
The World State System answers:
"What is the current condition of reality?"

History remembers what happened.
Geography records what is known.
The Chronicle records who experienced it.
The world itself continues regardless of observation.
```