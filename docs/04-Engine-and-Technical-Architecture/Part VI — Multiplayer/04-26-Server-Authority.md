# Project Draugr

# 04-26 - Server Authority

| Field | Value |
|--------|-------|
| Document ID | PD-004.26 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 03-02 - World Simulation Layer, 04-05 - Simulation Pipeline, 04-24 - Networking Architecture, 04-25 - Replication System |
| Related Documents | 03-03 - Causality Framework, 03-04 - Reality Integrity Layer, 03-06 - Player Integration Layer, 04-27 - Synchronization |

---

# Purpose

The Server Authority System defines the single source of objective truth for every Chronicle.

In Project Draugr, there is only one reality.

Only one simulation advances time.

Only one simulation resolves causality.

Only one simulation decides what truly happened.

That simulation exists on the authoritative server.

Clients never determine reality.

They merely request to participate in it.

---

# Design Philosophy

The player does not control reality.

The player influences reality.

Every player action is a proposal.

Every simulation outcome is a verdict.

The server exists not to oppose the player, but to preserve the integrity of the living world.

---

# Core Principles

## One Objective Reality

Every Chronicle possesses one authoritative world state.

There are never multiple conflicting truths.

---

## Intent Before Action

Clients transmit intent.

Servers determine outcomes.

---

## Simulation Determines Truth

Every authoritative decision originates from the simulation.

Not from clients.

Not from prediction.

---

## Equal Reality

Every connected player experiences the same underlying universe.

No player receives privileged simulation rules.

---

## Integrity Over Responsiveness

When responsiveness conflicts with simulation correctness,

simulation correctness always wins.

---

# Responsibilities

The Server Authority System is responsible for:

- validating player intent;
- executing authoritative simulation;
- resolving conflicting actions;
- maintaining causality;
- protecting world integrity;
- preventing unauthorized state modification.

It is not responsible for:

- rendering;
- client prediction;
- UI responsiveness;
- presentation.

---

# Authority Pipeline

Every player interaction follows the same sequence.

```text
Player Input

↓

Client Intent

↓

Server Validation

↓

Simulation Execution

↓

World Update

↓

Replication

↓

Client Presentation
```

Reality changes only after simulation execution.

---

# Objective Truth

Every simulation tick produces one authoritative world state.

Examples:

Tree exists.

Tree burns.

Tree collapses.

Tree becomes ash.

These events occur exactly once.

Every connected client eventually observes the same history.

---

# Player Intent

Players never directly execute actions.

Instead they request actions.

Examples:

Move here.

Attack target.

Craft item.

Open door.

Speak to NPC.

The request enters the simulation.

The simulation determines the result.

---

# Action Validation

Every request undergoes validation.

Examples:

Movement

Can the player physically reach the location?

Combat

Is the target actually within range?

Crafting

Do the required resources exist?

Dialogue

Can this conversation occur?

Invalid requests never modify reality.

---

# Conflict Resolution

Multiple players may attempt incompatible actions simultaneously.

Examples:

Two players grab the same object.

Two factions attack the same city.

Multiple builders modify one structure.

The authoritative simulation resolves every conflict deterministically.

---

# Simulation Authority

The server exclusively controls:

- AI;
- creatures;
- weather;
- economy;
- civilization;
- ecosystems;
- corruption;
- chronology;
- causality.

Clients never execute authoritative simulation logic.

---

# Ownership

Players temporarily control certain entities.

Examples:

Player character

Vehicle

Possessed creature

Ownership improves responsiveness.

Authority never transfers.

---

# Prediction Boundaries

Clients may temporarily predict:

- movement;
- camera;
- animation;
- interface feedback.

Predictions never become authoritative.

When prediction differs from reality,

reality always replaces prediction.

---

# Anti-Cheat Philosophy

Project Draugr does not primarily defend against cheating.

It defends objective reality.

Cheating becomes impossible when clients possess no authority over the simulation.

Security is a consequence of architecture.

---

# Simulation Consistency

Authoritative decisions must remain deterministic.

Given identical inputs,

the server always produces identical simulation outcomes.

Consistency enables:

- persistence;
- replay;
- recovery;
- debugging.

---

# Temporal Authority

Only the server advances simulation time.

Clients never:

- pause time;
- accelerate time;
- rewind time;
- skip simulation ticks.

Time belongs to the Chronicle.

---

# Environmental Authority

Examples:

Storm begins.

River floods.

Forest burns.

Volcano erupts.

Only the simulation determines when these events occur.

Clients simply observe them.

---

# AI Authority

NPCs never respond directly to client instructions.

Instead:

Player speaks.

↓

Server validates interaction.

↓

AI reasons.

↓

NPC responds.

Every decision originates from the AI simulation.

---

# Persistence Authority

Saving the Chronicle occurs only on the authoritative server.

Clients never write persistent world state.

Only verified simulation becomes history.

---

# Failure Handling

If communication fails:

Client

↓

Connection Interrupted

↓

Simulation Continues

↓

Reconnect

↓

World Synchronization

The world never pauses for disconnected observers.

---

# Scalability

Authority remains centralized even when simulation is distributed internally.

Future implementations may divide workload across servers,

while preserving one logical authoritative simulation.

Players should never perceive multiple competing realities.

---

# Integration with Replication

Server Authority determines:

What is true.

Replication determines:

Who needs to know.

---

# Integration with Synchronization

Authority generates authoritative updates.

Synchronization determines how clients converge toward them.

---

# Integration with Persistence

Only authoritative simulation data enters:

- saves;
- serialization;
- recovery;
- historical archives.

History records truth,

never prediction.

---

# Debug Support

Developer tools should visualize:

- validated requests;
- rejected requests;
- authority ownership;
- simulation decisions;
- causality chains;
- rollback events;
- prediction corrections.

Developers should always identify why the server accepted or rejected a particular action.

---

# Performance Considerations

The Server Authority System should optimize:

- request batching;
- deterministic scheduling;
- validation caching;
- asynchronous networking;
- scalable authority partitions;
- efficient conflict resolution.

Performance improvements must never compromise objective truth.

---

# Developer Notes

The authoritative server is not merely infrastructure.

It is the physical laws of Project Draugr.

Gravity exists because the server says it exists.

A kingdom falls because the simulation determined it fell.

A creature dies because reality reached that conclusion.

Players may shape history,

but history itself is written only once.

There is one Chronicle.

One timeline.

One objective reality.

The server exists to protect it.

---

# Future Expansion

Future versions may introduce:

- distributed authoritative simulation;
- planetary-scale server clusters;
- regional authority delegation;
- deterministic rollback networking;
- authoritative replay generation;
- cross-Chronicle synchronization;
- inter-server migration;
- omniversal simulation authority.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Server Authority architecture defining the server as the single objective source of reality for every Chronicle. |