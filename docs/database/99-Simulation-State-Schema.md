# 99-Simulation-State-Schema.md

# Project Draugr — Simulation State System Schema

> **The database is the memory of the world. The simulation state is the heartbeat.**

---

# Purpose

This document defines the global runtime state of the Project Draugr simulation.

The Simulation State System coordinates all persistent systems and maintains the current execution state of the world.

It does not replace individual databases.

It acts as the synchronization layer between:

- Chronicle State
- World State
- Geography
- Infrastructure
- Construction
- Crafting
- Materials
- Knowledge
- History
- Items

---

# Authority

This schema controls:

database/99-simulation-state.json

The Simulation State Database is the authoritative source for:

- current simulation time
- active Chronicle
- current world tick
- database synchronization status
- last processed actions
- pending updates

---

# Simulation State Philosophy

Individual databases answer:

"What exists?"

The Simulation State answers:

"What is happening right now?"

---

# Core Principle

The simulation database must always know:

- where the simulation currently is
- what systems have been updated
- what actions have been processed
- what changes are waiting to be committed

---

# Simulation State Structure

Example:

```json
{
 "simulation":

 {

 "simulation_id":

 "SIM-000001",

 "status":

 "Active",

 "current_time": {},

 "world_tick": {},

 "active_chronicle": {},

 "processing": {},

 "synchronization": {},

 "last_actions": [],

 "version":

 "MVP"

 }

}
Simulation Identity
Every simulation instance receives a permanent Simulation ID.
Example:
SIM-000001
Simulation ID Rules
Simulation IDs are:
globally unique
permanent
immutable
A simulation instance represents one continuous world.
Simulation Status
Possible states:
Initializing

Active

Paused

Suspended

Completed

Terminated
Example
{
 "status":

 "Active"
}
Current Time
Tracks the current world time.
Example:
{
 "current_time":

 {

 "date":

 "July 20, 2026",

 "time":

 "16:34",

 "day_number":

 1,

 "season":

 "Summer"

 }

}
Time Authority
The Simulation State controls:
calendar progression
time advancement
day cycles
All systems reference simulation time.
World Tick System
The simulation uses world ticks.
A tick represents one processed world update cycle.
Example:
{
 "world_tick":

 {

 "current":

 152,

 "last_update":

 "July 20, 2026 16:34"

 }

}
Tick Processing
Every tick processes:
Time Progression

↓

Environment

↓

Weather

↓

Wildlife

↓

Resources

↓

Infrastructure

↓

Chronicle Physiology

↓

Database Synchronization

↓

Narration
Active Chronicle
Defines the currently controlled Chronicle.
Example:
{
 "active_chronicle":

 {

 "chronicle_id":

 "CHR-000001",

 "status":

 "Alive",

 "location":

 "GEO-000001"

 }

}
Processing State
Tracks current simulation execution.
Example:
{
 "processing":

 {

 "current_phase":

 "World Processing",

 "action_id":

 "ACT-000015"

 }

}
Processing Phases
Valid phases:
Waiting

Receiving Action

Validating Action

World Processing

Biological Processing

Resource Processing

Database Update

Narration Generation

Completed
Action Processing
Every player action receives an Action ID.
Example:
ACT-000001
Action Record
Example:
{
 "action_id":

 "ACT-000001",

 "input":

 "I collect dry branches",

 "status":

 "Completed",

 "result":

 "Collected 5 dry branches"

}
Action Lifecycle
Every action follows:
Player Input

↓

Create Action ID

↓

Validate Intent

↓

Process World Reality

↓

Apply Consequences

↓

Update Databases

↓

Generate Response

↓

Archive Action
Synchronization System
The Simulation State tracks database consistency.
Example:
{
 "synchronization":

 {

 "character_state":

 "Synced",

 "items":

 "Synced",

 "world_state":

 "Synced",

 "history":

 "Synced"

 }

}
Synchronization States
Possible values:
Synced

Pending Update

Processing

Conflict

Error
Database Update Priority
Updates follow this order:
1. 00-Character-State

2. 02-Items

3. 03-Infrastructure

4. 04-Construction

5. 05-Crafting

6. 06-Materials

7. 07-Knowledge

8. 08-History

9. 09-Geography

10. 10-World-State

11. 99-Simulation-State
Why Simulation State Updates Last
The Simulation State represents the final confirmed condition.
Example:
Player action:
Craft Stone Axe
Processing:
Crafting Check

↓

Material Check

↓

Knowledge Check

↓

Create Item Entity

↓

Update Inventory

↓

Update Crafting History

↓

Update Simulation State
Last Actions
Stores recent processed actions.
Example:
{
 "last_actions":

 [

 {

 "action_id":

 "ACT-000015",

 "description":

 "Built wooden shelter",

 "time":

 "Day 4"

 }

 ]

}
Action History Limit
The Simulation State only stores recent actions.
Long-term records belong to:
08-History.md
Pending Events
The world may have scheduled changes.
Examples:
weather changes
crafting completion
construction completion
healing progression
resource regeneration
Example:
{
 "pending_events":

 [

 {

 "event":

 "Drying Timber",

 "completion":

 "Day 3"

 }

 ]

}
Scheduled World Events
Examples:
Rain expected

Crop growth

Animal migration

Resource regeneration

Construction completion

Disease progression
Save Integrity
Before generating narration:
The simulation must confirm:
Character State Updated

Items Updated

World State Updated

History Updated

Simulation State Updated
Failure Prevention
The simulation must never:
generate narration before database updates
create objects without IDs
lose completed actions
forget world changes
overwrite previous history
duplicate entities
reset simulation time
Crash Recovery
If a simulation interruption occurs:
The engine resumes from:
99-Simulation-State
Using:
last confirmed tick
last completed action
synchronization status
database records
Example Recovery
Before crash:
World Tick:

500

Processing:

Database Update
After restart:
Check:
00-Character-State

02-Items

10-World-State

Resume:
Tick 500 Completion
End of Day Processing
At the end of each simulated day:
The system performs:
Daily Tick

↓

Update Physiology

↓

Update Resources

↓

Update World Changes

↓

Update Construction

↓

Update Crafting

↓

Update History

↓

Save Simulation State
Simulation Continuity Rule
The simulation must survive:
long play sessions
multiple chat sessions
engine restarts
future continuation
The database is the memory.
The prompt is only the instruction.
Relationship With All Systems
Character State
Current body condition.
Items
Physical objects.
Infrastructure
Permanent structures.
Construction
Building progress.
Crafting
Production methods.
Materials
Resource availability.
Knowledge
Learned understanding.
History
Past events.
Geography
Known locations.
World State
Current reality.
Future Expansion
Possible future simulation controls:
multiplayer synchronization
NPC simulation queues
civilization simulation
world generation seeds
procedural events
alternate timelines
server persistence
Final Principle
The Simulation State System exists to guarantee:
The world continues exactly where it stopped.

The Chronicle may sleep.
The player may leave.
The chat may close.
But the world remembers.
```