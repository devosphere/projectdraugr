# Project Draugr

# 00 - Project Overview

| Field | Value |
|-------|--------|
| Document ID | PD-000 |
| Version | 0.1.0 |
| Status | Draft |
| Author | Project Draugr Team |
| Last Updated | July 2026 |

---

# Table of Contents

1. Purpose
2. Vision
3. Mission
4. Core Philosophy
5. Design Pillars
6. Player Experience Goals
7. Story Premise
8. Project Scope
9. Target Audience
10. High-Level Gameplay Loop
11. Core Systems
12. Technology Overview
13. Development Principles
14. Milestones
15. Related Documents

---

# 1. Purpose

This document establishes the vision, philosophy, and high-level direction of Project Draugr.

It serves as the primary reference for every future design and technical decision.

If a proposed feature contradicts this document, the feature should be redesigned before implementation.

---

# 2. Vision

Project Draugr aims to become an AI-driven survival RPG where every player's story is unique.

Unlike traditional RPGs, the game does not contain:

- predefined classes
- predefined professions
- predetermined destiny
- scripted main quest

The player's identity is shaped entirely through actions.

The world is designed to react rather than direct.

---

# 3. Mission

To create a living world where players discover who they become instead of choosing who they are.

Project Draugr seeks to encourage:

- Curiosity
- Experimentation
- Freedom
- Consequences
- Discovery
- Emergent storytelling

Every session should produce a different story.

---

# 4. Core Philosophy

## 4.1 The World Comes First

The world exists independently of the player.

It has:

- ecosystems
- ruins
- corruption
- weather
- creatures
- history

The player enters an already existing world.

---

## 4.2 Actions Create Identity

Identity is earned.

Never selected.

Examples

Player repeatedly fishes

↓

Fishing improves

↓

Discovers better techniques

↓

Eventually becomes known as a Fisher

The game never asks:

"What class do you want?"

---

## 4.3 Freedom Before Content

The player should never feel limited by dialogue options.

Instead of selecting choices from a menu,

the player writes their intentions.

Example

Instead of

> Attack

the player types

> Throw sand into the creature's eyes before running.

The engine interprets intent.

---

## 4.4 Discovery is Progression

Players should learn through:

- observation
- experimentation
- failure
- exploration

Knowledge is progression.

---

## 4.5 Consequences Persist

Actions should leave permanent changes whenever practical.

Examples

Destroy bridge

↓

Travel route changes

Save survivor

↓

Future ally

Ignore settlement

↓

Settlement may disappear

---

## 4.6 The Observer Watches

A mysterious intelligence continuously evaluates the player.

It is never introduced as a god.

It never explains itself.

It appears through dreams, whispers, places, creatures, or ordinary people.

Its purpose is observation.

---

# 5. Design Pillars

Every feature should strengthen at least one of these pillars.

## Freedom

Players choose their own objectives.

---

## Immersion

Minimal game-like interfaces.

The world should feel believable.

---

## Consistency

Rules should remain predictable.

Players should understand outcomes over time.

---

## Replayability

Every new game should create a different story.

---

## Emergence

Interesting stories should arise naturally from systems.

Not scripted events.

---

# 6. Player Experience Goals

The player should feel:

Lost

↓

Curious

↓

Hopeful

↓

Confident

↓

Responsible

↓

Attached to their story

The emotional journey matters more than winning.

---

# 7. Story Premise

An ordinary person from Earth suddenly disappears.

No warning.

No explanation.

They awaken in an unfamiliar world corrupted by unknown forces.

The player has only the clothes they wore during the moment of transportation.

Everything else must be earned.

Unknown to the player,

their arrival was intentionally orchestrated by an unseen Observer.

---

# 8. Initial Assessment

Before awakening,

the player experiences darkness.

A mysterious voice begins asking questions.

The questions are adaptive.

Each answer influences the next question.

The assessment determines:

- personality tendencies
- starting clothing
- probable spawn conditions
- narrative flavor

The player never sees the calculated values.

---

# 9. Core Gameplay Loop

Observe surroundings

↓

Think

↓

Perform action

↓

World simulation updates

↓

AI narrates result

↓

Experience gained

↓

Skills improve

↓

New opportunities appear

↓

Repeat

---

# 10. Core Systems

Project Draugr is composed of several interconnected systems.

- World Simulation
- Survival
- Time
- Weather
- Crafting
- Construction
- Inventory
- Equipment
- Skills
- Knowledge
- NPC Behavior
- Observer Evaluation
- Dreams
- AI Narration

Each system remains independent while interacting with others.

---

# 11. Artificial Intelligence

Artificial Intelligence is responsible for:

- interpreting player intent
- narrating events
- generating immersive descriptions
- contextual dialogue

Artificial Intelligence is NOT responsible for:

- world simulation
- player statistics
- inventories
- crafting rules
- combat calculations

Game logic always belongs to the game engine.

---

# 12. Technology Overview

Frontend

React

TypeScript

TailwindCSS

Backend

Python

FastAPI

Database

PostgreSQL

AI

OpenAI API

Deployment

Frontend

Vercel

Backend

Railway

Database

Supabase

---

# 13. Development Principles

Every feature proposal should answer:

Does this increase freedom?

Does this create interesting stories?

Does this preserve immersion?

Does this introduce meaningful consequences?

If any answer is "No",

the feature should be reconsidered.

---

# 14. Project Milestones

Phase 1

Planning

Documentation

Architecture

---

Phase 2

World Simulation

Player

Inventory

Crafting

---

Phase 3

Observer

Dreams

Narration

---

Phase 4

Public Alpha

Player Testing

Feedback

Iteration

---

# 15. Related Documents

Next Document

01 - Game Design Document

Future Documents

02 - Technical Requirements

03 - System Architecture

04 - Gameplay Systems

05 - World Lore

06 - Observer System

07 - Database Design

08 - API Specification

09 - Development Roadmap

10 - Coding Standards

---

# Revision History

| Version | Date | Changes |
|----------|------|----------|
| 0.1.0 | July 2026 | Initial document created |
