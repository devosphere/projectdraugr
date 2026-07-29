# Core Agent Boundaries

**Version:** 0.1.0
**Status:** MVP architecture contract
**Last updated:** 2026-07-29

Project Draugr has three collaborating agents. They are roles with enforced data boundaries, not three voices speaking to the player.

## Simulation Agent

Owns proposed changes to physics, ecology, weather, wildlife, and narration. It reads authoritative current state and produces a proposed outcome. It never writes database state directly.

The MVP includes this agent boundary now. Deterministic rules are the default implementation; an AI model may be introduced behind the same proposal contract for interpretation, bounded environmental reasoning, and narration. AI access is optional infrastructure, never permission to bypass facts, validation, or persistence boundaries.

## Persistent State Architect

Owns PostgreSQL schema evolution, Flyway migrations, and persistence mappings. It creates a new optional domain only when the world has actually invented or requires it. It never writes player-facing narration.

Runtime code may not alter schemas. A new domain must arrive as a reviewed Flyway migration.

## Persistent State Auditor

Performs read-only checks on authoritative state and immutable archives. It may report corruption or violated invariants, but it never repairs, changes, deletes, or narrates data.

## Collaboration order

`Simulation Agent proposes → Persistent State Architect commits current state and immutable history → Persistent State Auditor verifies the committed reality.`

The Chronicle UI renders only the Chronicle's permitted perception; it never renders Overseer knowledge.
