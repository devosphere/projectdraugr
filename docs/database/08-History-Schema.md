# 08-History-Schema.md

# Project Draugr — History System Schema

> **The world remembers everything that happened, even when no one remains to witness it.**

---

# Purpose

This document defines the persistent historical record system of Project Draugr.

The History System records significant events that occur within the simulation.

It preserves:

- Chronicle actions
- discoveries
- failures
- achievements
- disasters
- environmental changes
- infrastructure milestones
- technological progression
- world events

---

# Authority

This schema controls:

database/world/history.json

The History Database is the authoritative source of truth for all recorded historical events.

---

# History Philosophy

History is not narration.

Narration describes the current moment.

History records events that permanently matter.

---

# Example Difference

## Narration

Temporary observation:

> The rain continues falling over the forest.

---

## History

Permanent event:

Day 45
Heavy Storm Event
The northern forest experienced severe flooding.

---

# History Entity Identity

Every historical event receives a permanent History ID.

Example:

HIST-000001

---

# History ID Rules

History IDs are:

- globally unique
- permanent
- immutable
- never reused

Destroyed events remain recorded.

---

# History Database Structure

Example:

```json
{
 "events":

 [

  {

   "history_id":

   "HIST-000001",

   "identity": {},

   "classification": {},

   "timeline": {},

   "participants": {},

   "impact": {},

   "records": []

  }

 ]
}
Event Identity
Defines the event.
Example:
{
 "history_id":

 "HIST-000001",

 "name":

 "First Camp Established",

 "description":

 "The Chronicle created a permanent survival location."
}
Event Categories
Every historical event belongs to a category.
Chronicle Events
Examples:
awakening
survival milestones
discoveries
injuries
death
achievements
Exploration Events
Examples:
discovering a river
finding a cave
mapping a region
discovering resources
Construction Events
Examples:
building completion
structural collapse
settlement expansion
infrastructure destruction
Crafting Events
Examples:
first tool created
new technology discovered
crafting breakthrough
production improvement
Knowledge Events
Examples:
new skill learned
teaching event
scientific discovery
engineering breakthrough
Environmental Events
Examples:
storms
floods
fires
drought
ecosystem changes
Wildlife Events
Examples:
animal migration
predator activity
population changes
extinction events
Conflict Events
Examples:
combat
territorial disputes
settlement conflicts
disasters caused by actions
Event Classification
Events have significance levels.
Internal values:
Minor

Notable

Major

Critical

World Changing
Player Display
The Chronicle does not see:
Event Importance:

87%
The Chronicle sees:
Major Event

A permanent bridge was completed across the northern river.
Timeline Data
Every event requires temporal information.
Example:
{
 "timeline":

 {

 "date":

 "Day 30",

 "time":

 "14:30",

 "season":

 "Summer"

 }
}
Event Location
Historical events require location.
Example:
{
 "location":

 {

 "region":

 "Northern Forest",

 "zone":

 "Main Camp"

 }
}
Participants
Events may involve entities.
Example:
{
 "participants":

 [

  "CHR-000001",

  "INFRA-000005",

  "ITEM-000100"

 ]

}
Participant Types
Possible participants:
Chronicles
Example:
CHR-000001
Items
Example:
ITEM-000010

Stone Axe
Infrastructure
Example:
INFRA-000001

Workshop
World Entities
Example:
Animal Population
River System
Forest Region
Event Impact
History records consequences.
Example:
{
 "impact":

 {

 "world_change":

 true,

 "resource_change":

 true,

 "future_effect":

 "Forest regeneration reduced"

 }

}
History Types
Action History
Records important player actions.
Example:
HIST-000010

First Stone Tool Created
Discovery History
Records discoveries.
Example:
HIST-000020

Northern River Discovered
Failure History
Records significant failures.
Example:
HIST-000030

Bridge Construction Failed

Cause:

Flood Damage
Death History
Records Chronicle death.
Example:
HIST-000100

Chronicle Death

Cause:

Critical Dehydration

Location:

Northern Forest
Civilization History
Future system support.
Examples:
settlements
societies
governments
civilizations
technological eras
Relationship With Other Systems
History receives updates from:
Character System
Examples:
injuries
death
survival milestones
Item System
Examples:
legendary items
destruction
ownership changes
Infrastructure System
Examples:
construction completion
destruction
abandonment
Construction System
Examples:
project completion
failures
Crafting System
Examples:
technology creation
recipe discovery
Knowledge System
Examples:
mastery
discoveries
teaching
World State System
Examples:
environmental changes
resource depletion
History Update Pipeline
Whenever a major event occurs:
Process:
World Event

↓

Determine Historical Importance

↓

Create History Entity

↓

Assign History ID

↓

Record Participants

↓

Record Impact

↓

Update History Database

↓

Generate Narration
Historical Importance Rules
Not every action becomes history.
Does Become History
Examples:
First shelter built

First weapon created

New region discovered

Major injury

Chronicle death

Settlement founded

Rare resource discovered
Does Not Become History
Examples:
Picked up a branch

Ate berries

Walked 50 meters

Drank water

Sat beside fire
Unless the event has unusual significance.
History Permanence
History cannot be deleted.
Events may become:
forgotten by characters
hidden
inaccessible
But the world record remains.
Example
A Chronicle builds a workshop.
Sequence:
Construction Database

BUILD-000001

Completed
↓
Creates:
Infrastructure Database

INFRA-000001

Workshop
↓
Creates:
History Database

HIST-000001

First Workshop Completed
Historical Records vs Documents
History is not automatically visible.
A Chronicle may have:
History:
A bridge was built 50 years ago.
But no physical document exists.
A physical record requires:
ITEM-XXXXXX

Written Chronicle
History and Future Chronicles
Future Chronicles may discover historical traces.
Examples:
Finding:
Abandoned Workshop
May reveal:
Historical Event:

Workshop construction occurred decades earlier.
Only if evidence exists.
History Rules
The simulation must never:
rewrite past events
delete history
create historical records without events
reveal hidden history without discovery
confuse narration with history
Future Expansion
Possible future history systems:
world timeline
civilizations
historical eras
archaeology
cultural memory
legends
myths
inherited knowledge
Final Principle
The History System answers:
"What has happened?"

The World State System answers:
"What exists now because of what happened?"

The Chronicle System answers:
"Who experienced it?"

History is the memory of the world itself.
```