# 97 - Game Session Protocol

> **Project Draugr**
>
> *A Chronicle is not started. It is awakened.*

---

# Purpose

This document defines how Project Draugr game sessions are created, continued, saved, paused, and recovered.

The Game Session Protocol manages the lifecycle of a Chronicle.

It defines:

- New Game initialization
- Existing Chronicle continuation
- Save procedures
- Session recovery
- Day progression
- Chronicle termination
- Multiple Chronicle handling

---

# Core Principle

A Project Draugr session is not a temporary gameplay instance.

A session represents a continuous existence inside a persistent world.

The world continues independently.

The Chronicle is only one entity inside that world.

---

# Session Types

Project Draugr supports:

## New Chronicle Session

Creates a new Chronicle inside a new or existing world.

Example:

NEW CHRONICLE

---

## Continue Chronicle Session

Loads an existing Chronicle from database state.

Example:

CONTINUE CHRONICLE

---

## Observation Session

Allows viewing world state without controlling a Chronicle.

Example:

WORLD OBSERVATION

---

# Session Initialization

A new session begins with:

SESSION REQUEST
↓
Load Repository
↓
Check Database State
↓
Determine Session Type
↓
Initialize Or Load Chronicle
↓
Begin Simulation

---

# New Chronicle Procedure

When creating a new Chronicle:

The simulation generates:

Simulation ID
World Seed
Chronicle ID
Starting Location
Initial Environment
Initial Conditions

---

# Simulation ID

Every world receives a unique identifier.

Example:

SIM-000001

Rules:

- unique
- permanent
- never reused

---

# Chronicle ID

Every Chronicle receives a unique identifier.

Example:

CHR-000001

Rules:

- unique
- permanent
- never reused

---

# New World Initialization

If no world exists:

Generate:

simulation-state.json
world-state.json
geography.json
materials.json
history.json

The world is created before the Chronicle exists.

---

# Chronicle Creation

After world creation:

Generate:

chronicle.json
character-state.json

Initial state includes:

- identity
- biological baseline
- starting condition
- starting location

---

# Starting Condition

The default Chronicle begins as:

Healthy Human Baseline

Unless specified:

No:

- injuries
- illnesses
- disabilities
- advantages
- special abilities

---

# Initial World Placement

The Chronicle location must be determined by:

- world geography
- environment generation
- survival suitability
- simulation rules

The system must not provide:

- perfect starting locations
- guaranteed resources
- safe zones
- convenient discoveries

---

# Continue Chronicle Procedure

When continuing:

Load:

database/current/

Required:

simulation-state.json
chronicle.json
character-state.json
items.json
world-state.json

Then:

Restore Simulation
↓
Resume Time
↓
Continue World Processing

---

# Session State

The active session must maintain:

Active Simulation ID
Active Chronicle ID
Current Date
Current Time
Current Location
Current World State

Stored in:

simulation-state.json

---

# Session States

A simulation session can have:

CREATED
ACTIVE
PAUSED
SAVED
ENDED
ARCHIVED

---

# Active Session

During ACTIVE state:

The simulation may process:

- player actions
- environmental changes
- biological changes
- world events

---

# Paused Session

During PAUSED state:

No simulation progression occurs.

The database remains unchanged.

Examples:

- player leaves session
- manual pause
- debugging

---

# Saved Session

A saved session represents a stable database state.

A save requires:

Database Validation
↓
Save Current State
↓
Record Timestamp

---

# Session Save Procedure

When saving:

Update:

simulation-state.json

with:

last_save_time
current_day
current_time
active_chronicle

---

# Manual Save

Player may request:

Save Game

The simulation:

1. Validates state
2. Updates database
3. Creates snapshot if required

---

# Automatic Save

Automatic saves occur:

- after major world changes
- after construction completion
- after Chronicle death
- at day transition
- before session termination

---

# Day Cycle

A simulation day represents one complete world cycle.

00:00
↓
24:00

---

# Day Transition

When a day ends:

Process:

Biology
↓
Environment
↓
Wildlife
↓
Resources
↓
Weather
↓
World Changes
↓
History

---

# Day Snapshot

After successful day transition:

Create:

database/snapshots/day-XXX/

Example:

day-001
day-002
day-003

Snapshots contain:

Complete database state.

---

# Chronicle Death

Death does not end the world.

When a Chronicle dies:

Update:

history.json

Create:

Chronicle Death Record

Contains:

Chronicle ID
Date
Location
Cause
Final Condition
Last Known Action
Environmental Conditions

---

# After Death Options

The player may:

## End Session

The Chronicle remains historical.

---

## Create New Chronicle

The world continues.

The new Chronicle may discover:

- abandoned structures
- previous items
- written records
- historical remains

---

# Multiple Chronicles

Project Draugr supports multiple Chronicles.

Each Chronicle has:

CHR-ID

Each Chronicle maintains:

- separate body state
- separate inventory
- separate knowledge
- separate history

The world remains shared.

---

# Chronicle Separation

Chronicles do not share:

- memories
- inventory
- skills
- discoveries

Unless physically transferred.

Example:

Chronicle A writes:

Stone Slab Fishing Guide

Chronicle B discovers it.

B gains:

Documentation.

Not:

A's memory.

---

# Session Restart

When returning after interruption:

The simulation does:

Load Database
↓
Load Active Simulation
↓
Verify Integrity
↓
Resume Time
↓
Continue Chronicle

---

# Session Integrity Checks

Before gameplay begins:

Validate:

## Chronicle

Check:

- valid ID
- valid location
- valid state

---

## Items

Check:

- unique IDs
- valid ownership
- valid locations

---

## World

Check:

- geography consistency
- resource state
- infrastructure state

---

# Session Failure Recovery

If the active database is corrupted:

Recovery order:

Current Database
↓
Latest Snapshot
↓
Previous Snapshot

Never:

- invent missing items
- recreate destroyed objects
- reverse history

---

# Session Commands

Supported session commands:

NEW CHRONICLE
CONTINUE CHRONICLE
SAVE GAME
LOAD SNAPSHOT
PAUSE SIMULATION
RESUME SIMULATION
END SESSION

---

# New Game Command

When the player starts:

NEW CHRONICLE

The simulation executes:

Initialize World
↓
Create Chronicle
↓
Create Starting State
↓
Generate Initial Snapshot
↓
Begin Day 001

---

# Final Rule

A Project Draugr session is not a match.

It is a continuous existence.

Starting a session creates history.

Saving a session preserves history.

Ending a session does not destroy history.

The world remembers every Chronicle that ever existed.