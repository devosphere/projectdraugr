# Project Draugr

# 01 - Game Design Document (GDD)

| Field | Value |
|-------|--------|
| Document ID | PD-001 |
| Version | 0.1.0 |
| Status | Draft |
| Author | Project Draugr Team |
| Last Updated | July 2026 |

---

# Table of Contents

1. Introduction
2. Design Goals
3. Player Experience
4. Core Gameplay Loop
5. Story Premise
6. The Observer
7. Initial Assessment System
8. World Generation
9. Player Progression
10. Survival Systems
11. Skills
12. Crafting
13. Building
14. NPC System
15. World Events
16. AI Narration
17. Saving
18. Death
19. Future Expansion

---

# 1. Introduction

## Purpose

This document defines the gameplay mechanics, design philosophy, and player experience for Project Draugr.

Unlike the Project Overview, this document specifies **how the game should behave**.

Every gameplay system implemented by developers should align with this document.

---

## Genre

Project Draugr is a:

- AI-assisted Text RPG
- Survival RPG
- Sandbox RPG
- Emergent Storytelling Game
- Single Player Browser Game

---

## Core Fantasy

The player is not a hero.

The player is not destined to save the world.

The player is simply another human transported into an unfamiliar world.

Every accomplishment is earned.

Every mistake has consequences.

Every story belongs to the player.

---

# 2. Design Goals

The game should consistently achieve the following goals.

## Goal 1

Encourage experimentation.

Players should feel comfortable trying unusual ideas.

The game should reward creativity whenever possible.

---

## Goal 2

Promote discovery.

Players should learn naturally through gameplay.

Tutorials should be minimal.

Knowledge should come from experience.

---

## Goal 3

Avoid artificial progression.

There are:

- no character classes
- no skill trees
- no predefined professions

Progression emerges through repeated actions.

---

## Goal 4

Create memorable stories.

The game should produce moments players naturally want to share.

Examples:

"I accidentally started a forest fire."

"I survived winter using mushrooms."

"I built an entire village beside a ruined cathedral."

These stories should emerge naturally.

---

## Goal 5

The world should feel alive.

NPCs have lives.

Creatures have routines.

Weather changes.

Resources disappear.

Nothing waits for the player.

---

# 3. Player Experience

The intended emotional progression is:

Arrival

↓

Confusion

↓

Curiosity

↓

Experimentation

↓

Discovery

↓

Confidence

↓

Mastery

↓

Legacy

The game should never rush the player.

---

# 4. Core Gameplay Loop

The gameplay loop consists of six stages.

## Observe

The player examines their surroundings.

Example:

> Look around

---

## Decide

The player determines an objective.

Examples:

- Find water
- Hunt food
- Build shelter
- Explore ruins

---

## Act

The player describes an action using natural language.

Examples:

> Climb the nearby tree.

> Gather dry branches.

> Dig beneath the rock.

---

## Simulate

The game engine validates:

- action feasibility
- required tools
- environmental conditions
- skill modifiers
- probability of success

The engine updates the world state.

---

## Narrate

The AI converts the structured outcome into immersive text.

Example:

Instead of:

"You collected 3 wood."

The AI may describe:

> You snap several dry branches from a weathered tree. Though brittle, they should burn well enough to keep a fire alive through the coming night.

---

## Progress

The player gains:

- experience
- discoveries
- memories
- opportunities

The cycle then repeats.

---

# 5. Story Premise

Project Draugr begins in the real world.

The player is an ordinary individual living in the year 2026.

An unknown event causes the player to disappear.

No explanation is provided.

The player awakens in a mysterious apocalyptic world corrupted by unknown forces.

The player's only possessions are the clothes they were wearing immediately before transportation.

No modern technology accompanies them.

No knowledge about the new world is provided.

---

# 6. The Observer

## Overview

The Observer is the central narrative system of Project Draugr.

From the player's perspective, the Observer is an unknown presence that occasionally manifests through dreams, visions, whispers, strange entities, or seemingly ordinary encounters.

From the game's perspective, the Observer is a hidden evaluation system that continuously learns who the player truly is through actions rather than declarations.

The Observer is intentionally mysterious.

Its identity must never be explicitly revealed during the early stages of the game.

---

## Design Philosophy

The Observer does not exist to guide the player.

The Observer does not exist to reward morality.

The Observer does not exist to punish mistakes.

Its sole purpose is observation.

The player should gradually realize that something has been watching them since the very beginning.

---

## Design Rules

The Observer must never:

- Give quests
- Reveal the future
- Explain the world
- Solve puzzles
- Tell the player they are "good" or "evil"
- Interfere with normal gameplay without narrative justification

Instead, it observes and records.

---

## Narrative Role

Internally, the Observer is the divine intelligence responsible for transporting humans into the world.

This information is classified as **Developer Knowledge**.

The player should never be explicitly informed of this fact during the MVP.

NPCs should also be unaware.

Different cultures may have different interpretations of the Observer.

Examples:

- Ancient God
- Spirit of the World
- Forgotten King
- Living Planet
- Collective Consciousness

No interpretation should be confirmed.

---

## Observer Encounters

The Observer has no fixed physical form.

It may communicate through almost anything.

Examples include:

- Dreams
- Voices
- Birds
- Trees
- Monsters
- Ruins
- Reflections
- Children
- Elderly strangers
- Wind
- Rain
- Silence

These manifestations should never appear predictable.

The player should always question whether the encounter truly happened.

---

## Frequency

Observer encounters should remain rare.

Recommended frequency:

Early Game

One encounter every 10–20 in-game days.

Mid Game

One encounter every 20–40 in-game days.

Late Game

Triggered primarily by major life events.

Examples:

- Near death
- Establishing a settlement
- Losing a companion
- Discovering an ancient ruin
- Surviving a catastrophe

---

## Communication Style

The Observer never speaks directly.

Instead, it asks questions.

Example

Instead of

"You did well."

It asks

"Would you make the same decision again?"

---

Instead of

"You failed."

It asks

"What did failure teach you?"

---

Instead of

"You are brave."

It asks

"What is courage without fear?"

The purpose is reflection.

Not judgment.

---

## Hidden Evaluation

The Observer maintains an internal profile for every player.

These values are hidden.

Players never see numerical scores.

Example internal profile:

Curiosity

Compassion

Adaptability

Patience

Caution

Persistence

Consistency

Selflessness

Pragmatism

Creativity

Leadership

Risk Tolerance

Discipline

These are not "stats."

They exist only to help the game understand the player.

---

## Consistency Principle

The Observer values consistency more than morality.

Example

A player consistently sacrifices personal safety to help others.

The Observer recognizes consistency.

Another player consistently prioritizes survival over strangers.

The Observer also recognizes consistency.

Neither approach is inherently rewarded.

The Observer seeks understanding, not judgment.

---

## Memory

The Observer remembers every significant event.

Examples:

- First shelter
- First kill
- First death
- First harvest
- First betrayal
- First companion
- First settlement

These memories may influence future encounters.

---

## Observer Log

Internally, the game stores observations as chronological records.

Example

Day 3

Player refused to abandon an injured traveler despite starvation.

Day 11

Player intentionally burned infected crops to protect a nearby settlement.

Day 26

Player ignored cries for help during a storm.

These records are never shown directly to the player.

---

## Dreams

Dreams are the primary communication method.

Dreams occur only while sleeping.

Dreams should feel symbolic rather than literal.

Examples:

The player walks through a forest where every tree whispers a different memory.

A child repeatedly asks the same question without waiting for an answer.

A bird flies backward across a crimson sky.

The purpose is emotional reflection rather than exposition.

---

## Observer Questions

Questions asked during dreams should have no objectively correct answer.

Examples

"What would you protect if nothing belonged to you?"

"When does survival become selfishness?"

"If everyone forgot your name, would your actions still matter?"

These answers may subtly influence future Observer evaluations but should never alter the core rules of the world.

---

## Observer Rewards

The Observer does not grant items, levels, or skills.

Its influence is subtle.

Possible outcomes include:

- Additional dream encounters
- Increased narrative depth
- Hidden world discoveries
- Alternative interpretations of events
- Rare opportunities for self-reflection

The Observer should never feel like a traditional progression mechanic.

---

## Developer Notes

The Observer is the narrative spine of Project Draugr.

It exists to encourage introspection rather than competition.

Players should never attempt to "optimize" Observer interactions.

If players begin asking,

"How do I maximize Observer points?"

then the design has failed.

The Observer should remain unknowable, philosophical, and quietly present throughout the player's journey.


The player must survive.

---

# 7. Adaptive Assessment System

## Overview

Before awakening in the new world, every player undergoes an invisible evaluation.

This evaluation is known internally as the **Adaptive Assessment System (AAS)**.

From the player's perspective, they experience only darkness and hear a mysterious voice asking seemingly unrelated questions.

The player is never informed that these questions are part of a character assessment.

The purpose of the assessment is not to determine strength or abilities.

Its purpose is to establish an initial understanding of the player's personality, decision-making tendencies, and circumstances immediately before transportation.

---

# Design Philosophy

The assessment should feel like a conversation.

It should never resemble a traditional character creation screen.

The player should never see:

- Character Creation
- Race Selection
- Class Selection
- Attribute Points
- Difficulty Selection
- Starting Equipment Selection

Instead, the player simply answers questions.

Every answer helps reconstruct who they were moments before they disappeared.

---

# Design Goals

The Adaptive Assessment System has five objectives.

1. Determine the player's probable clothing.

2. Estimate behavioral tendencies.

3. Influence initial spawn conditions.

4. Personalize the introduction.

5. Begin the Observer's long-term evaluation.

---

# Hidden Nature

The player should never know:

- why questions are asked
- what values are recorded
- how answers affect gameplay

There is no summary screen.

No personality report.

No attribute allocation.

The game immediately transitions into awakening.

---

# Question Categories

Questions are grouped into independent categories.

The system dynamically selects questions based on previous responses.

Categories include:

- Daily Life
- Occupation
- Personality
- Problem Solving
- Habits
- Relationships
- Risk Tolerance
- Observation
- Routine
- Clothing Context

Future categories may be added without modifying the overall system.

---

# Adaptive Question Flow

Every answer influences the next question.

Example

Question

"Where were you most likely going today?"

Player

"Work."

Possible follow-up questions

- What type of work?
- Do you normally work indoors?
- Do you wear a uniform?
- How far is your commute?

If instead the player answers

"Meeting friends."

Possible follow-up

- Where do you usually meet?
- What would you normally wear?
- How do you travel?

The assessment should feel like a natural conversation rather than a branching questionnaire.

---

# Clothing Reconstruction

Players never choose starting equipment.

Instead, the game reconstructs what they were likely wearing.

Example

Player

"I work in an office."

↓

"What is today's weather?"

↓

"Warm."

↓

"What would you normally wear?"

↓

"Polo shirt."

Generated Equipment

Head

None

Body

Polo Shirt

Legs

Slacks

Feet

Leather Shoes

Accessories

Watch (non-functional after transport)

The equipment reflects the player's final moments on Earth.

---

# Earth Equipment Rules

Only clothing worn on the player's body may be transported.

Examples of valid items:

- Shirt
- Jacket
- Hat
- Pants
- Shoes
- Gloves
- Belt
- Glasses
- Scarf
- Jewelry

Examples of invalid items:

- Smartphone
- Wallet
- Backpack
- Keys
- Laptop
- Weapons
- Food
- Water Bottle
- Tools

The transportation event affects only the player and the clothing physically worn.

---

# Hidden Personality Model

The assessment estimates behavioral tendencies.

These values remain hidden.

Example internal metrics

Curiosity

Empathy

Patience

Creativity

Risk Tolerance

Discipline

Adaptability

Observation

Leadership

Persistence

Consistency

These are not player attributes.

They exist solely for simulation purposes.

---

# Spawn Generation

The assessment does not directly choose a spawn location.

Instead, it influences probabilities.

Example

High Curiosity

↓

Greater chance of spawning near:

- Ancient ruins
- Libraries
- Abandoned laboratories

High Compassion

↓

Greater chance of spawning near:

- Survivor camps
- Injured NPCs
- Small settlements

High Risk Tolerance

↓

Greater chance of spawning near:

- Corrupted zones
- Dangerous wildlife
- Valuable resources

Randomness remains an essential factor.

No two playthroughs should be identical.

---

# Dynamic Introduction

The narration should adapt to the assessment.

Example

Office Worker

"You remember fluorescent lights and unfinished work."

Runner

"You remember your heartbeat slowing after another stride."

Student

"You remember unfinished conversations and tomorrow's responsibilities."

The introduction should feel personal without revealing the underlying system.

---

# Assessment Engine

The system should support an expanding question pool.

Version 0.1

Approximately 50 questions.

Each playthrough

5–8 questions.

Future versions

100+

200+

500+

Questions.

This ensures high replayability.

---

# Developer Guidelines

Questions should never have objectively correct answers.

Avoid questions such as

"Are you brave?"

Instead ask

"You hear someone calling for help from inside a collapsing building.

What is your first instinct?"

Behavioral scenarios produce more meaningful information than direct personality questions.

---

# Future Expansion

Possible future additions include

- Regional cultural questions
- Seasonal context
- Dynamic weather
- Current day of week
- Time of day
- Family relationships
- Long-term ambitions

These additions should increase immersion without changing the core philosophy.

---

# Developer Notes

The Adaptive Assessment System replaces traditional character creation.

The player should feel as though they are recalling ordinary moments from their previous life rather than constructing a fictional character.

By the time the player awakens, the game already has a believable understanding of who they were—without ever asking them to select a class, assign attribute points, or choose starting gear.

The assessment is the Observer's first conversation with the player, but neither party fully understands the significance of that moment.

---

# 8. World Generation

## Overview

The world of Project Draugr is not created for the player.

Instead, the player is introduced into an already existing world that continues to evolve regardless of the player's presence.

The world has:

- history
- ecosystems
- weather
- corruption
- abandoned civilizations
- creatures
- survivors

The player is simply another living being attempting to survive.

---

# Design Philosophy

The world should feel indifferent.

It should never appear to revolve around the player.

Examples

A village may disappear before the player ever discovers it.

A forest fire may destroy valuable resources.

A river may flood.

A predator may eliminate another species.

These events should occur naturally.

---

# World Structure

The world consists of Regions.

Each Region contains multiple Zones.

Each Zone contains numerous Locations.

Structure

World

↓

Regions

↓

Zones

↓

Locations

↓

Points of Interest

This hierarchy allows procedural expansion while maintaining organization.

---

# Regions

Regions represent major geographical areas.

Examples

- Ashen Plains
- Black Mire
- Crimson Forest
- Silent Highlands
- Broken Coast
- Frozen Expanse
- Hollow Desert

Regions define:

- climate
- available resources
- creature population
- weather patterns
- corruption level

---

# Zones

Zones are subdivisions within Regions.

Each Zone has its own environmental identity.

Example

Crimson Forest

↓

Northern Woods

↓

Old Hunter Trail

↓

Collapsed Cabin

Each Zone contains:

- flora
- fauna
- terrain
- danger level
- resource availability

---

# Locations

Locations are places the player can directly explore.

Examples

- abandoned house
- ruined church
- cave entrance
- stream
- hilltop
- watchtower
- mine
- swamp

Locations should encourage exploration.

---

# Points of Interest (POI)

Points of Interest provide opportunities for discovery.

Examples

Natural

- waterfalls
- giant trees
- caves
- cliffs
- rivers

Artificial

- ruins
- camps
- laboratories
- statues
- temples
- abandoned settlements

POIs should contain stories rather than quests.

---

# Spawn System

The player never chooses where to begin.

The game selects a spawn location based on:

- adaptive assessment
- world conditions
- probability
- randomness

Spawn conditions should always be believable.

Examples

Near fresh water.

Near abandoned shelter.

Inside ruined buildings.

Near dangerous wildlife.

On isolated coastlines.

No spawn should guarantee survival.

---

# World Persistence

The world remembers changes.

Examples

If trees are cut,

they remain gone until regrowth.

If a bridge collapses,

travel routes change.

If a building burns,

its remains persist.

Persistence is one of the defining characteristics of Project Draugr.

---

# Time System

Time flows continuously.

Recommended MVP

One in-game day

=

30 real-world minutes

Each day consists of:

Morning

Afternoon

Evening

Night

Time influences:

- visibility
- creature behavior
- NPC schedules
- temperature
- weather

---

# Weather System

Weather affects gameplay rather than visuals alone.

Possible weather

- clear
- rain
- storm
- fog
- ashfall
- corrupted rain
- strong wind

Weather influences

- travel
- fire
- farming
- tracking
- hunting
- visibility
- survival

---

# Seasons

Version 0.1

Static season.

Future versions

Spring

Summer

Autumn

Winter

Seasonal changes should affect:

- crops
- migration
- food availability
- temperatures
- diseases

---

# Corruption

Corruption is a natural force affecting the world.

It is not inherently evil.

Corruption changes environments over time.

Examples

Healthy forest

↓

Corrupted forest

↓

Dead forest

↓

Crystal wasteland

Corruption may create:

- new resources
- dangerous creatures
- environmental hazards

Players may choose to study, avoid, or exploit corruption.

---

# Resource Distribution

Resources should never appear evenly distributed.

Instead,

each Region develops naturally.

Examples

Mountains

- stone
- iron
- caves

Forest

- wood
- herbs
- wildlife

Swamps

- mushrooms
- medicinal plants
- poisonous creatures

Oceans

- fish
- salt
- shells

Scarcity creates meaningful exploration.

---

# Exploration

Exploration should reward curiosity.

Possible discoveries

- forgotten journals
- abandoned camps
- unusual plants
- hidden caves
- ancient machinery
- mysterious symbols

Discoveries should raise questions rather than immediately provide answers.

---

# World Events

Independent events occur regardless of player actions.

Examples

Forest fire.

Disease outbreak.

Migration.

Heavy storm.

Earthquake.

Settlement abandonment.

These events create a living world.

---

# Living Ecosystem

Creatures interact with each other.

Predators hunt prey.

Herbivores migrate.

Plants spread naturally.

Water sources dry up.

Nothing exists solely for the player's benefit.

---

# World Knowledge

At the start of the game,

the player knows nothing.

Maps must be created.

Landmarks remembered.

Safe routes discovered.

Knowledge is earned.

---

# Developer Notes

The world should feel ancient.

The player should constantly feel as though they have arrived late to a story that has been unfolding for centuries.

Every ruined building,

every abandoned camp,

every strange monument,

should imply that someone else lived here long before the player arrived.

The purpose of world generation is not to provide content.

Its purpose is to create opportunities for meaningful stories.
