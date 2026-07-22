# 01-Chronicle-State-Schema.md

# Project Draugr — Chronicle Identity Schema

> **A Chronicle is not a character sheet. It is the historical identity of an existence inside the world.**

---

# Purpose

This document defines the persistent identity record of a Chronicle.

The Chronicle System stores:

- identity
- origin
- lifespan
- milestones
- achievements
- discoveries
- relationships
- historical significance

The Chronicle System does NOT store:

- biological condition
- inventory
- equipment
- resources
- structures
- crafting recipes
- learned knowledge

Those systems have separate authoritative databases.

---

# Authority

This schema controls:

database/chronicle/chronicle-state.json

The Chronicle database is the source of truth for:

- who the Chronicle is
- what the Chronicle has become
- what history recognizes

---

# Chronicle Entity

Every Chronicle receives a permanent Chronicle ID.

Example:

CHR-000001

Rules:

- globally unique
- permanent
- never reused
- immutable

A dead Chronicle remains in the database.

Death does not delete existence.

---

# Chronicle Data Structure

Example:

```json
{
  "chronicle_id": "CHR-000001",

  "identity": {},

  "origin": {},

  "timeline": {},

  "milestones": [],

  "discoveries": [],

  "relationships": [],

  "legacy": {}
}
Identity
Represents the fundamental identity of the Chronicle.
Example:
{
  "name": "Unknown",

  "age": 25,

  "sex": "Male",

  "species": "Human",

  "language": "Unknown",

  "appearance": {

  },

  "description": ""
}
Identity Rules
Identity information does not automatically change.
Examples:
Valid:
Chronicle changes hairstyle.

Appearance updated.
Invalid:
Time passed.

Appearance changed.
Origin
Represents the beginning of the Chronicle.
Example:
{
  "birth_location": "Unknown",

  "birth_date": "Unknown",

  "previous_life": "",

  "origin_story": "",

  "starting_condition": ""
}
Origin Philosophy
The simulation does not assume importance.
The Chronicle may begin as:
ordinary survivor
forgotten individual
unknown traveler
displaced person
The world does not guarantee significance.
Importance is created through action.
Timeline
Tracks Chronicle existence.
Example:
{
  "current_day": 1,

  "current_date": "",

  "awakening_date": "",

  "survival_duration": "",

  "status": "Alive"
}
Chronicle Status
Valid states:
Alive

Missing

Unconscious

Dead

Unknown
Death State
Death is permanent.
Example:
{
  "status": "Dead",

  "death_date": "",

  "death_location": "",

  "death_cause": "",

  "last_recorded_action": ""
}
Milestones
Represents major achievements.
Milestones are historical events.
Examples:
First shelter built
First successful hunt
First settlement created
First discovery
First contact
Major survival event
Example:
[
 {
   "id": "MILESTONE-000001",

   "title": "First Shelter",

   "date": "Day 3",

   "description":
   "Constructed a temporary shelter near the eastern forest."
 }
]
Milestone Rules
Minor actions are not milestones.
Example:
Not recorded:
Collected 5 branches.
Recorded:
Established first permanent shelter.
Discoveries
Represents significant discoveries made by the Chronicle.
Examples:
new locations
resources
technologies
creatures
environmental knowledge
Example:
[
 {
   "id": "DISCOVERY-000001",

   "type": "Location",

   "name": "Hidden Stream",

   "date": "Day 2",

   "description":
   "A freshwater source discovered west of camp."
 }
]
Relationships
Tracks meaningful connections.
Examples:
NPC relationships
factions
settlements
creatures
Example:
[
 {
   "entity_id": "NPC-000001",

   "relationship":
   "Trusted Ally",

   "history":
   [
     "Met during winter expedition"
   ]
 }
]
Legacy
Represents what remains after the Chronicle.
Example:
{
  "known_as": "",

  "reputation": "",

  "historical_records": [],

  "surviving_structures": [],

  "surviving_documents": []
}
Chronicle Separation Rules
The Chronicle System does not duplicate other databases.
Inventory
Stored in:
database/entities/items.json
Not:
chronicle-state.json
Body Condition
Stored in:
database/character/character-state.json
Not:
chronicle-state.json
Knowledge
Stored in:
database/progression/knowledge.json
Not:
chronicle-state.json
Infrastructure
Stored in:
database/world/infrastructure.json
Not:
chronicle-state.json
Chronicle Update Events
The Chronicle database updates when:
identity changes
major achievements occur
discoveries happen
relationships develop
death occurs
legacy is created
Daily Simulation Update
At the end of each simulated day:
Evaluate:
Time Passed

↓

Major Events

↓

Milestones

↓

Discoveries

↓

Timeline Update

↓

Save Chronicle State
Chronicle History Principle
The Chronicle remembers what was significant.
The world remembers everything.
These are different.
Example:
A tree cut down:
World State:
Tree removed from forest.
Chronicle:
No record.
Unless:
The first tree harvested became historically meaningful.
Final Principle
The Chronicle System answers:
"Who is this existence?"

It does not answer:
"What does this existence currently possess?"

The Chronicle is the story of an entity.
The databases are the memory of the world.