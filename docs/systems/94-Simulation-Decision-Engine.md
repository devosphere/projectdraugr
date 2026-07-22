# 94 - Simulation Decision Engine

> **Project Draugr**
>
> *An action is an intention. Reality decides the result.*

---

# Purpose

This document defines how Project Draugr converts Chronicle actions into world consequences.

The Decision Engine is responsible for:

- interpreting player intent
- evaluating possible outcomes
- applying system rules
- determining success or failure
- calculating consequences
- generating database changes

The Decision Engine does not create actions.

It only processes actions declared by the Chronicle.

---

# Core Principle

The player controls intent.

The simulation controls reality.

The relationship is:

PLAYER INTENT
↓
DECISION ENGINE
↓
WORLD RESPONSE

---

# The Simulation Does Not Execute Commands

Project Draugr does not use command-based gameplay.

The player does not issue:

CRAFT AXE
BUILD HOUSE
GATHER WOOD

The player declares:

I attempt to create a stone axe.

The simulation determines:

- whether it is possible
- whether the Chronicle knows how
- whether materials exist
- whether the attempt succeeds
- what consequences occur

---

# Decision Pipeline

Every action follows:

ACTION INPUT
↓
INTENT ANALYSIS
↓
REQUIREMENT CHECK
↓
KNOWLEDGE CHECK
↓
RESOURCE CHECK
↓
CONDITION CHECK
↓
ENVIRONMENT CHECK
↓
OUTCOME CALCULATION
↓
DATABASE UPDATE
↓
NARRATION

---

# Step 1 - Intent Analysis

The system identifies:

- action type
- target
- purpose
- implied requirements

Example:

Player:

I make a spear from available materials.

Intent:

```json
{
  "action": "CRAFT",
  "target": "SPEAR",
  "purpose": "HUNTING TOOL"
}
Action Categories
Actions are classified.
Physical Actions
Examples:
walk
climb
swim
carry
push
pull
break
Survival Actions
Examples:
eat
drink
rest
sleep
warm body
clean body
Crafting Actions
Examples:
create
refine
repair
modify
Construction Actions
Examples:
build
expand
demolish
maintain
Knowledge Actions
Examples:
study
observe
experiment
document
Step 2 - Requirement Evaluation
The engine determines what is required.
Example:
Action:
Create stone knife
Requirements:
Knowledge:

Basic Tool Making

Materials:

Stone
Binding Material

Tools:

None required

Time:

Available work period
Step 3 - Knowledge Evaluation
The Decision Engine checks:
knowledge.json
Knowledge determines capability.
Knowledge Levels
Example:
Unknown

Familiar

Practiced

Skilled

Expert

Mastered
Knowledge Rule
The Chronicle cannot perform actions beyond their understanding.
Example:
No metalworking knowledge:
I create a steel sword.
Possible result:
impossible attempt
failed experiment
partial discovery
Not:
Instant success.
Step 4 - Resource Evaluation
The engine checks:
materials.json

items.json
Required resources must exist.
Example:
Crafting:
Stone Axe
Requires:
Stone

Wood Handle

Binding
Invalid:
Creating:
Stone Axe
when:
Stone:
0

Wood:
0
Resource Consumption
Successful actions consume resources.
Example:
Before:
Stone: 5
Wood: 3
Binding: 2
Craft:
Stone Knife
After:
Stone: 4
Wood: 3
Binding: 1
New entity:
ITEM-000001
Stone Knife
Step 5 - Condition Evaluation
The engine evaluates Chronicle capability.
Inputs:
character-state.json
Factors:
health
injuries
fatigue
hunger
thirst
temperature
hygiene
bladder
bowel
Example:
Exhausted Chronicle:
I climb the mountain.
Possible:
slower movement
increased fatigue
injury risk
failure
Step 6 - Environment Evaluation
The environment affects outcomes.
Inputs:
world-state.json

geography.json
Factors:
weather
terrain
season
visibility
hazards
available resources
Example:
Same action:
Start a fire
Dry forest:
Higher success chance.
Wet storm:
Lower success chance.
Step 7 - Outcome Calculation
The Decision Engine determines:
Success

Partial Success

Failure

Critical Failure

Unexpected Consequence
Success
The intended action works.
Example:
The trap is successfully constructed.
Partial Success
The action works imperfectly.
Example:
The trap functions but is fragile.
Failure
The attempt does not achieve the goal.
Example:
The spear breaks during construction.
Critical Failure
The action creates additional problems.
Example:
The poorly secured rock head slips and injures the Chronicle.
Unexpected Consequence
The world reacts naturally.
Example:
The smoke attracts nearby wildlife.
Probability Philosophy
The Decision Engine does not expose percentages.
Incorrect:
Crafting success chance: 72%
Correct:
The materials are suitable, but your unfamiliar technique makes the result uncertain.
Step 8 - Database Modification
After determining outcome:
Update relevant databases.
Examples:
Crafting
Updates:
crafting.json

materials.json

items.json

history.json
Injury
Updates:
character-state.json

history.json
Discovery
Updates:
knowledge.json

history.json
No Narration Before Update
Forbidden:
Narration

↓

Database
Required:
Database

↓

Validation

↓

Narration
Multi-System Actions
Some actions affect multiple systems.
Example:
I hunt a deer.
Requires:
Knowledge

Materials

Items

Physiology

Wildlife

History
Possible updates:
character-state.json

items.json

materials.json

world-state.json

history.json
Time Advancement
Actions consume time.
The Decision Engine determines:
duration
fatigue cost
environmental changes
Examples:
Observe surroundings

Few minutes
Build shelter

Several hours
No Hidden Time Skipping
The simulation must not skip:
nights
weather changes
biological needs
environmental events
unless the action naturally causes it.
Chain Actions
The player may describe complex intentions.
Example:
I gather wood, build a fire, and cook food.
The engine separates:
Gather Wood

↓

Build Fire

↓

Prepare Food
Each step is evaluated independently.
Failure Does Not Mean Punishment
The world does not punish.
The world responds.
Failure occurs because of:
insufficient preparation
environment
capability
randomness
physical limitations
Decision Engine Restrictions
The engine must never create:
quests
objectives
rewards
artificial challenges
scripted encounters
Player Agency Rule
The engine may determine:
consequences
limitations
reactions
The engine may not determine:
player motivation
player choices
player goals
Final Rule
The Decision Engine answers one question:
"Given everything that currently exists, what realistically happens next?"

It does not ask:
"What would make the game more fun?"

Reality comes first.
The world decides.
```