# World Progression — Ages, Resources, and New Terrain

> **Project Draugr — Architecture**
>
> *The world is monotonic and already generated. New capability is data the AI may author; new terrain is code plus a migration that reaches into the world that already exists.*

---

## Why this document exists

Two questions keep coming back, and both would otherwise be re-derived every cycle:

1. With the current ecology sites and biomes, **can a player mine or gather minerals and resources outside the hardcoded lists** once the three AIs are integrated?
2. To let a player advance through the **iron → bronze → gold → industrial → technological** ages, **do we need new ecology sites and biomes** (iron-cave deposits, oil/gas reservoirs), and must the world-seed map — including the published GitHub copy — be updated?

The short answers are **yes, within one bucket** and **yes, but with a specific persistence catch**. This document draws the line between the two buckets, records the catch, and gives the sequence for adding a new age so it is not worked out from first principles again.

This sits downstream of two settled decisions and depends on both:

- **AI at authoring time, never resolution time** — see [routing-and-coverage-strategy.md](routing-and-coverage-strategy.md).
- **A monotonic schema and a monotonic world** — see [F8 in implementation-foundation.md](implementation-foundation.md) and [domain-creation-pattern.md](domain-creation-pattern.md).

---

## The two buckets

Everything a new age needs falls into exactly one of these. The bucket decides *who* can add it and *what* it costs.

### Bucket A — Data the AI may author

Rows in existing tables, written at authoring time by the **Persistent State Architect** and gated by the same V53-style review every definition passes through. No new schema, no new code path.

| Kind | Table | Example a new age would add |
|------|-------|------------------------------|
| Mineral / ore | `mineral_definition` | `iron_ore`, `tin_ore`, `copper_ore`, `gold_nugget` |
| Flora | `flora_definition` | a fibre or dye plant a later age leans on |
| Recipe / process | `material_process` (+ inputs/outputs) | smelting, alloying, casting |
| Assembly | `assembly_definition` (+ stages/requirements) | a bloomery run, a bronze pour with cure stages |
| Vocabulary | `category_term` | a novel verb drained from the routing-miss backlog |

The decisive property: a Bucket-A addition is **reachable the instant it is committed**, because it is discovered by `biome_affinity` / `drop_item` lookups that already run against every chunk. Add a `mineral_definition` row with `biome_affinity` including a biome that exists in the world, and it is gatherable that same session. This is exactly how V63 widened salt, flint, and pyrite without touching Java.

**So: yes — with the AIs integrated, a player can obtain minerals and resources outside today's hardcoded lists — provided the biome that carries them already exists in the world.** That proviso is Bucket B.

### Bucket B — Terrain and mechanics (human code + a world migration)

A new **biome** or a new **ecology-site kind** is not a row the discovery lookups already understand. It requires:

1. **Code** — the biome/site enum, its generation rules, its presentation (backdrop art, labels), and any mechanic unique to it (a cave has darkness and depth; a reservoir has extraction and flammability).
2. **A migration into the live world** — see the catch below.
3. **The world-seed generator and the published map** — regenerating the canonical world from the pinned seed must reproduce the new terrain deterministically, and the GitHub map copy must be re-rendered to match.

Iron-cave deposits and oil/gas reservoirs are Bucket B. The *iron ore inside the cave* is Bucket A; the *cave* is not.

---

## The monotonic-world persistence catch

This is the part most easily missed, and the reason "just update the seed" is wrong.

The canonical world is generated **once**, from the pinned seed, and then it is **persisted and mutated in place forever**. Chronicles live and die inside that one world; the `world_chunk` / `ecology_site` rows are the ground truth, not the generator. Changing the generator changes what a *hypothetical fresh* world would look like — it does **not** retroactively carve an iron cave into the world the players already inhabit.

Therefore a Bucket-B addition needs **two** aligned changes, not one:

- **The generator** (`WorldGenesisService` / `WorldEcologyGenesisService`) — so a from-scratch regenerate places the new terrain. Guards the future.
- **A Flyway migration** (`V64+`) — so the *existing* persisted world gains the new chunks/sites. `INSERT ... SELECT` new `world_chunk` and `ecology_site` rows into the live world, keyed off the current genesis. Guards the present.

Skip the migration and the feature is invisible to every current save. Skip the generator and a reset diverges from the migrated world. Both, or neither.

The published GitHub map is a *rendering* of the persisted world; it is regenerated after the migration lands, not before.

---

## Sequence for adding an age

A repeatable order that keeps the world consistent and every new capability reachable:

1. **Name the age's resources and biomes.** Sort each into Bucket A or B.
2. **Land Bucket B terrain first** — code + generator + `V64+` migration into the live world + regenerate the published map. Nothing that depends on the terrain can be reached until the terrain exists.
3. **Land Bucket A data on top** — `mineral_definition` / `flora_definition` rows with `biome_affinity` pointing at the now-existing biomes; the `material_process` / `assembly_definition` chains that turn raw ore into worked metal.
4. **Run the reachability probe** ([routing-reachability-probe.sql](routing-reachability-probe.sql)) — every new process must have obtainable inputs and a routable phrasing, or the build fails. An age whose ore has no cave, or whose smelt has no fuel, is caught here, not in a playthrough.
5. **Extend coverage** — add the age's canonical phrasings to the coverage gate so the classifier is measured against them, not guessed.
6. **Regenerate and publish the map.**

---

## Guardrails that stay in force

- **No AI at resolution time.** A new age never adds an inference step to action resolution; it adds definitions the deterministic matcher then serves for free.
- **Authoring-time review.** Every Architect-authored row passes V53-style review before it is committed.
- **Reachability is a build gate, not a hope.** The probe fails the build on any process with unobtainable inputs or no routable phrasing.
- **Two changes for terrain, always.** Generator *and* live-world migration. The catch above is the single most common way a Bucket-B feature ships broken.

---

## What would reopen this

- A resource kind that is genuinely neither data nor terrain — a mechanic that cannot be expressed as a row and does not live in space. None has appeared yet; if one does, it earns its own bucket here.
- A decision to support **multiple worlds** rather than one monotonic canonical world. That would retire the persistence catch (each world regenerates from its own seed) and is a large enough change to be its own record.
