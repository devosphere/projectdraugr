# Core Agent Boundaries

Project Draugr has three collaborating agents. They are roles with enforced data boundaries, not three voices speaking to the player.

## Simulation Agent

Owns proposed changes to physics, ecology, weather, wildlife, and narration. It reads authoritative current state and produces a proposed outcome. It never writes database state directly.

The MVP uses deterministic rules behind this contract. A future AI model may propose narration and bounded simulation outcomes, but the same contract remains in place.

## Persistent State Architect

Owns PostgreSQL schema evolution, Flyway migrations, and persistence mappings. It creates a new optional domain only when the world has actually invented or requires it. It never writes player-facing narration.

Runtime code may not alter schemas. A new domain must arrive as a reviewed Flyway migration.

## Persistent State Auditor

Performs read-only checks on authoritative state and immutable archives. It may report corruption or violated invariants, but it never repairs, changes, deletes, or narrates data.

## Collaboration order

`Simulation Agent proposes → Persistent State Architect commits current state and immutable history → Persistent State Auditor verifies the committed reality.`

The Chronicle UI renders only the Chronicle's permitted perception; it never renders Overseer knowledge.
