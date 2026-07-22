# 09-Geography-Schema.md

# Project Draugr — Geography System Schema

> **The world is discovered, not revealed.**

---

# Purpose

This document defines the persistent geography system of Project Draugr.

The Geography System manages the Chronicle's understanding of the physical world.

It records:

- discovered locations
- explored regions
- terrain
- landmarks
- resource areas
- routes
- environmental features
- mapped locations

---

# Authority

This schema controls:

database/world/geography.json

The Geography Database is the authoritative source of truth for discovered world locations.

---

# Geography Philosophy

The world exists independently.

The Chronicle only knows what has been:

- observed
- explored
- mapped
- learned
- documented

Unknown areas remain unknown.

---

# Discovery Principle

The map is not the world.

The map represents the Chronicle's understanding of the world.

A location can exist without being discovered.

---

# Example

World Reality:

Large Mountain Range

Chronicle Knowledge:

Unknown

After exploration:

Northern Mountain Range Discovered

After mapping:

Northern Mountain Range
Recorded on Map

---

# Geography Entity Identity

Every discovered geographic entity receives a permanent Geography ID.

Example:

GEO-000001

---

# Geography ID Rules

Geography IDs are:

- globally unique
- permanent
- immutable
- never reused

Destroyed or forgotten locations retain historical identity.

---

# Geography Database Structure

Example:

```json
{
 "locations":

 [

  {

   "geography_id":

   "GEO-000001",

   "identity": {},

   "classification": {},

   "environment": {},

   "discovery": {},

   "resources": {},

   "connections": {},

   "history": []

  }

 ]
}
Location Identity
Defines the geographic entity.
Example:
{
 "geography_id":

 "GEO-000001",

 "name":

 "Forest Stream",

 "description":

 "A freshwater stream running through dense woodland."
}
Location Categories
Every geographic entity belongs to a category.
Natural Locations
Examples:
forests
rivers
lakes
mountains
caves
valleys
plains
wetlands
coastlines
Resource Locations
Examples:
iron deposits
clay deposits
hunting grounds
fishing areas
medicinal plant regions
Constructed Locations
Examples:
camps
settlements
villages
ruins
roads
bridges
Unknown Locations
Unexplored regions may exist without detailed records.
Example:
Unexplored Northern Territory
Environment Data
Defines physical characteristics.
Example:
{
 "environment":

 {

 "terrain":

 "Dense Forest",

 "climate":

 "Temperate",

 "vegetation":

 "Mixed Woodland",

 "water":

 "Freshwater Stream"

 }

}
Terrain Types
Examples:
Forest

Mountain

Hill

Valley

Plain

Desert

Swamp

Cave

Coast

River
Climate Types
Examples:
Temperate

Tropical

Arid

Cold

Polar

Mountain
Discovery Data
Records how the Chronicle learned about the location.
Example:
{
 "discovery":

 {

 "discovered":

 true,

 "date":

 "Day 5",

 "method":

 "Exploration"

 }

}
Discovery Methods
Possible methods:
Direct Exploration
Example:
Walking into unknown territory
Observation
Example:
Seeing mountains from a distance
Information Transfer
Example:
NPC describes location
Documentation
Example:
Finding an old map
Location Knowledge States
A location has different awareness levels.
Internal states:
Unknown

↓

Observed

↓

Discovered

↓

Explored

↓

Mapped

↓

Surveyed
Player Representation
The Chronicle does not see:
Exploration Progress:

73%
Instead:
Northern Forest

Explored
Resource Data
Geography may contain known resource information.
Example:
{
 "resources":

 [

  {

   "type":

   "Stone",

   "availability":

   "Common"

  }

 ]

}
Resource Knowledge Rules
Resources are not automatically known.
Example:
Incorrect:
Entering forest:

Iron Ore Available Nearby
Correct:
You notice exposed rocks along the hillside.
Further investigation may reveal:
Possible iron-bearing stone.
Geographic Connections
Locations may connect.
Example:
{
 "connections":

 [

 {

 "destination":

 "GEO-000002",

 "route":

 "Forest Path"

 }

 ]

}
Connection Types
Examples:
Path

River Route

Mountain Pass

Road

Cave Passage

Coastal Route
Distance Information
Distance knowledge depends on exploration.
Unknown:
A mountain exists north.
Known:
Mountain is approximately two days travel away.
Fully mapped:
Exact route recorded.
Map Relationship
Maps are physical objects.
The Geography System stores world understanding.
The Item System stores physical maps.
Example
Geography Database:
GEO-000001

Main Camp Region

Known
Item Database:
ITEM-000050

Main Camp Map

Stored on wooden table
Map Updates
Maps do not automatically update.
Example:
World:
New bridge constructed
Geography:
Bridge location exists
Physical map:
Still outdated
Only the Chronicle can update the map.
Location History
Every geographic entity stores history.
Example:
{
 "history":

 [

 {

 "event":

 "Discovered",

 "date":

 "Day 20"

 },

 {

 "event":

 "Forest Fire",

 "date":

 "Day 90"

 }

 ]

}
Geographic Change
The world can change.
Examples:
rivers change course
forests regrow
resources deplete
roads decay
structures collapse
terrain changes
Relationship With Other Systems
World State System
Controls:
current environmental condition
resource depletion
terrain changes
History System
Records:
discoveries
disasters
major changes
Infrastructure System
Controls:
settlements
buildings
roads
permanent structures
Item System
Controls:
physical maps
survey tools
documents
Knowledge System
Controls:
geographic understanding
navigation ability
environmental interpretation
Geography Update Pipeline
When exploring:
Chronicle Action

↓

World Processing

↓

Determine Geographic Discovery

↓

Create / Update Geography Entity

↓

Update Geography Database

↓

Update History if Significant

↓

Generate Narration
Exploration Rules
The simulation must never:
reveal unexplored areas
create perfect maps automatically
provide resource locations without discovery
update physical maps automatically
confuse player knowledge with world reality
Future Expansion
Possible future geography systems:
procedural world generation
climate simulation
civilization territories
trade routes
geological history
navigation systems
satellite-equivalent technologies
planetary geography
Final Principle
The Geography System answers:
"What places does the Chronicle know about?"

The World State System answers:
"What exists in the world?"

The Map System answers:
"What has the Chronicle physically recorded?"

The world is larger than the map.
Discovery is earned.
```