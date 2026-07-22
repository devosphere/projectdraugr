# 07-Knowledge-Schema.md

# Project Draugr — Knowledge System Schema

> **Knowledge is not what exists in the world. Knowledge is what the Chronicle understands about the world.**

---

# Purpose

This document defines the persistent knowledge system of Project Draugr.

The Knowledge System manages:

- learned skills
- discovered techniques
- observations
- principles
- procedures
- doctrines
- theories
- engineering understanding
- survival experience

Knowledge represents the internal understanding of the Chronicle.

---

# Authority

This schema controls:

database/progression/knowledge.json

The Knowledge Database is the authoritative source of truth for everything the Chronicle knows.

---

# Knowledge Philosophy

Knowledge is separate from physical existence.

A Chronicle may:

- know something without writing it down
- write something down without fully understanding it
- lose physical documents while retaining knowledge
- discover information but misunderstand it

---

# Example Difference

## Knowledge

Primitive Bow Construction
Understanding:
How to create a functional bow.

Exists inside:

knowledge.json

---

## Documentation

ITEM-000100
Stone Tablet:
Bow Construction Instructions

Exists inside:

items.json

---

The knowledge exists in the mind.

The document exists in the world.

---

# Knowledge Entity Identity

Every knowledge entry receives a permanent Knowledge ID.

Example:

KNOW-000001

---

# Knowledge ID Rules

Knowledge IDs are:

- globally unique
- permanent
- immutable
- never reused

Forgotten or outdated knowledge remains in historical records.

---

# Knowledge Database Structure

Example:

```json
{
 "knowledge_entries":

 [

  {

   "knowledge_id":

   "KNOW-000001",

   "identity": {},

   "category": {},

   "mastery": {},

   "source": {},

   "history": []

  }

 ]
}
Identity Data
Defines the knowledge entry.
Example:
{
 "knowledge_id":

 "KNOW-000001",

 "name":

 "Primitive Stone Axe Construction",

 "description":

 "Knowledge of creating basic stone cutting tools."
}
Knowledge Categories
Knowledge is divided into domains.
Survival Knowledge
Examples:
finding water
identifying edible plants
shelter building
fire creation
animal tracking
Crafting Knowledge
Examples:
stone tools
woodworking
weaving
leather processing
pottery
Construction Knowledge
Examples:
foundations
structural supports
roofing
construction planning
Material Knowledge
Examples:
identifying stone types
wood properties
mineral recognition
resource locations
Biological Knowledge
Examples:
medicine
anatomy
disease recognition
plant remedies
Environmental Knowledge
Examples:
weather patterns
seasons
terrain understanding
ecosystem behavior
Combat Knowledge
Examples:
weapon handling
hunting techniques
defensive tactics
tracking threats
Engineering Knowledge
Examples:
mechanisms
machines
advanced construction
manufacturing principles
Knowledge Source
Every knowledge entry records how it was obtained.
Examples:
{
 "source":

 {

 "type":

 "Experience",

 "event":

 "Repeated hunting practice"

 }

}
Knowledge Sources
Possible sources:
Experience
Learned through direct action.
Example:
Repeated fishing improves fishing knowledge.
Observation
Learned through watching.
Example:
Observing animal behavior.
Teaching
Obtained from another entity.
Example:
NPC teaches metalworking.
Documentation
Learned from physical records.
Example:
Reading an engineering blueprint.
Experimentation
Discovered through trial and error.
Example:
Testing different clay mixtures.
Knowledge Mastery
Knowledge has progression.
The system does not display numerical values.
Internal states:
Unknown

↓

Familiar

↓

Learned

↓

Practiced

↓

Skilled

↓

Advanced

↓

Mastered
Player View
The Chronicle sees:
Correct:
Knowledge

Basic Shelter Construction

Understanding:
Familiar
Incorrect:
Shelter Skill:

42%
Skill Development
Knowledge improves through:
repetition
successful application
experimentation
teaching
refinement
Example
Initial:
Knowledge:

Stone Tool Creation

Unknown
After observation:
Familiar
After creating tools:
Practiced
After many improvements:
Skilled
Knowledge Does Not Create Items
Learning:
Advanced Woodworking
does not create:
ITEM-000500

Wooden Chair
The Chronicle must physically craft the chair.
Knowledge Does Not Modify Reality
Knowing:
Bridge Construction
does not create:
INFRA-000001

Bridge
Construction must occur.
Knowledge and Crafting Relationship
Crafting requires knowledge.
Example:
Attempt:
Create Metal Sword
System checks:
Knowledge:

Metalworking
Blacksmithing
Weapon Construction
If missing:
Possible result:
failure
incomplete attempt
discovery through experimentation
Knowledge and Construction Relationship
Construction requires understanding.
Example:
Simple:
Lean-to Shelter

Requires:

Basic Shelter Knowledge
Advanced:
Stone Fortress

Requires:

Advanced Construction Knowledge
Engineering Knowledge
Knowledge and Documentation
Knowledge may create documentation.
Example:
The Chronicle knows:
Camp Layout Planning
Creates:
ITEM-000900

Camp Blueprint
The blueprint is physical.
The knowledge remains internal.
Documentation Does Not Automatically Update
Example:
Knowledge:
Main Camp Expansion
Changes.
Existing document:
ITEM-000900

Old Camp Blueprint
does not update automatically.
The Chronicle must revise it.
Knowledge Transfer
Knowledge can move between entities.
Methods:
teaching
books
tablets
demonstrations
observation
Teaching Process
Example:
Teacher:
CHR-000001

Master Carpenter
Student:
CHR-000002
Process:
Teaching Attempt

↓

Understanding Check

↓

Knowledge Transfer

↓

Knowledge Update
Knowledge Loss
Knowledge may be affected by:
memory limitations
trauma
isolation
lack of practice
However:
Knowledge loss does not destroy historical records.
Knowledge History
Every knowledge entry keeps history.
Example:
{
 "history":

 [

  {

   "event":

   "Discovered",

   "date":

   "Day 10"

  },

  {

   "event":

   "Improved",

   "date":

   "Day 50"

  }

 ]

}
Discovery System
New knowledge may emerge from:
exploration
survival events
experimentation
observation
failure
Discovery Example
The Chronicle sees:
Unknown plant
After experimentation:
Knowledge:

Edible Plant Identification

Learned
Knowledge Restrictions
The simulation must never:
grant knowledge without a source
confuse documents with understanding
create items from learning alone
skip learning requirements
reveal unknown information without discovery
Knowledge Update Pipeline
Whenever knowledge changes:
Examples:
discovery
learning
teaching
mastery improvement
forgetting
Process:
Event

↓

Determine Knowledge Impact

↓

Update Knowledge Database

↓

Update Related Systems

↓

Record History

↓

Generate Narration
Relationship With Other Systems
Knowledge affects:
Crafting System
Determines available recipes.
Construction System
Determines building capability.
Materials System
Determines resource understanding.
Chronicle System
Represents personal development.
Future Expansion
Possible future knowledge systems:
skill specialization
professions
education
research
scientific discovery
cultural knowledge
language development
civilization advancement
Final Principle
The Knowledge System answers:
"What does the Chronicle understand?"

The Item System answers:
"What physical things exist?"

The Documentation System answers:
"What information has been physically recorded?"

The world changes only when knowledge becomes action.
```