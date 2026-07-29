# Action, Narration, and Object Invariants

**Version:** 0.1.0
**Status:** Active implementation contract
**Last updated:** 2026-07-29

## Object-first reality

`world_object` is the highest persistence abstraction for all physical reality. A Chronicle is a world object with a biological lifecycle; it is never the owner of the world.

Objects retain UUID identity across carrying, use, abandonment, Chronicle death, damage, and destruction. A destroyed object remains archived with `lifecycle_state = DESTROYED`; it is never deleted.

When a Chronicle dies, carried objects must be physically relocated to the Chronicle's death location or a physically present container. The Chronicle's personal state ends; world objects remain.

## Action resolution

The player declares intent. The resolver classifies it, checks authoritative state and Chronicle knowledge, calculates time and effects, persists allowed state changes, writes immutable history, and only then requests narration.

Deterministic rules resolve actions when the answer is fully knowable. Bounded AI reasoning is used only for interpretation, improvised methods, complex environmental interaction, or narration. AI never writes PostgreSQL and cannot create facts absent from its allowed-fact packet.

## Narration policy

Narration is not a hint system and must never compensate for an ignored Body HUD. It must not warn, remind, recommend, diagnose, or state the Chronicle's hunger, thirst, energy, temperature, health, bladder, bowel, hygiene, or other internal HUD condition.

It may describe an immediate, fact-backed sensation or directly observed consequence when it belongs to the resolved action or permitted external perception. For example, rain soaking a sleeve is valid; “you are very thirsty” and “you should find water” are not. Sensory wording must never become a hidden-status reminder, recommendation, or substitute for reading the Body HUD.

Narration is constrained to Chronicle-permitted external perception and state-validated action outcomes. It cannot invent entities, resources, places, outcomes, coordinates, hidden statistics, unseen threats, or Overseer knowledge.
