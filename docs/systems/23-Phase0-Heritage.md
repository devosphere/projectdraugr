# 23 – Phase 0 Heritage

> **Project Draugr**
>
> *What the Wolf Kingdom proved, kept so it is never proved twice.*

---

# Purpose

This document records what the Phase 0 prototype achieved and how that achievement was folded into the current architecture.

Phase 0 was a ten-day playthrough run with ChatGPT as the simulation engine and JSON files as state. It was unstable — it lost memory and contradicted itself — but it reached a working stone-age settlement and, in doing so, proved which domains are reachable and worth having.

Migration **V47** is the act of folding that evidence into the schema.

**Source:** `projectdraugr-day6-10.zip` → `database/current/*.json`

---

# The Governing Distinction

Everything in this document turns on one rule, from DR-0013 / F8 and Core Rule 7:

| | Scope | Survives a chronicle's death |
|---|---|---|
| **Schema and definitions** | World-global, monotonic | **Yes** — added once, forever |
| **Chronicle capability** | Per-chronicle | **No** — dies with them |

Phase 0's chronicle recorded `Woodworking [Operational]`. That familiarity **died with them.** What survives is the world's catalogue of what *can* be done — not anyone's ability to do it.

**V47 therefore grants no chronicle any skill.** A new chronicle still starts at zero familiarity and must earn every technique through the three-layer success model. The catalogue exists so the Persistent State Architect never re-derives a domain that has already been invented — nothing more.

---

# A Bug This Work Uncovered

Phase 0 crafted a **Primitive Stone Hatchet** on Day 5. Checking that against the current schema revealed that `stone_hatchet` and `stone_axe` were **referenced in Java but had no `item_definition` rows**:

- `PhysicalItemService.hasCuttingTool()` tested for `stone_hatchet` — a branch that could never be true.
- `fellTree()` (V39) tested for `stone_axe` / `stone_hatchet` / `iron_axe` / `hand_axe` — **none of which existed**, so `FELL_TREE` could never succeed.

V47 defines both tools. The felling intent works now because the prototype's inventory said it should.

---

# What V47 Adds

## Items (16 new definitions)

**Tools (bug fix)** — `stone_hatchet`, `stone_axe`

**Carrying and equipment** — `utility_belt` (reached Revision III in Phase 0 via the existing REFINE intent), `backpack_basket`, `large_basket`

**Workstations and storage** — `woodworking_table`, `stoneworking_table`, `weaving_table`, `sewing_table`, `tool_shed`

**Material grades** — `precision_tool_stone`, `foundation_stone`, `construction_stone`, `timber_log`, `vine`, `fiber_cordage`, `wooden_component`, `structural_timber`, `textile_material`

Material grades matter because Phase 0 recorded the principle that stone tools require balancing durability against availability — which only means something if stone comes in grades.

## `technique_definition` — 18 techniques

The Architect's ledger of what this world already knows is possible. Each row carries its domain, difficulty, what it produces, the tool *class* it needs, and the one-line principle the prototype learned.

**This is not a skill tree.** It says a technique is reachable, not that anyone can do it.

## `construction_kind` — 10 kinds

`construction_project.project_kind` is free text, and only two kinds ever existed in code (`LEAN_TO`, `STONE_FIRE_PIT`). Phase 0 completed seventeen projects across many more. The catalogue makes the kinds data rather than string literals.

## `district_purpose` — 9 purposes

`chronicle_named_location.purpose_tag` is free text. These are the purposes the Wolf Kingdom actually used, so settlement planning has the same vocabulary without re-deriving it.

## `container_capacity_default`

A container's capacity is a property of what it *is*, rather than a number repeated at every craft site.

## Domains registered (8 new)

`tools`, `woodworking`, `stoneworking`, `textiles`, `cartography`, `documentation`, `settlement_planning`, `logistics` — plus `phase0_heritage` marking the provenance.

---

# What V47 Deliberately Does Not Add

**The Wolf Kingdom's physical objects are not resurrected.** Phase 0 ran in a different, GPT-simulated world. The current world is generated from a pinned deterministic seed. Importing The Archives or the Wolf Fire Pit as live `world_object` rows would place a dead world's buildings into a world where nobody built them — violating the provenance rules that every object carries a history.

If a future chronicle wants an archive, they build one. The *technique* is catalogued; the *building* is not gifted.

**No capability, no skill, no starting knowledge.** Covered above.

**The principles below are documentation, not tables.** Nothing in the codebase reads them, and a table nothing reads is speculative schema. They are recorded here because they are genuine design findings.

---

# Engineering Principles Recorded in Phase 0

These are the prototype's own words, kept because they encode real reasoning about why the systems work as they do. Several are already reflected in the technique catalogue's `principle` column.

1. Stone wall fire pits improve fuel efficiency.
2. Air vents improve combustion and burn duration.
3. Stone slabs provide durable permanent documentation.
4. Dedicated storage districts improve logistics.
5. Balanced equipment distribution reduces fatigue.
6. Stone tools require balanced material selection between durability and availability.
7. Dedicated workstations improve specialized crafting efficiency.
8. Separated manufacturing zones improve settlement workflow.
9. Modular tool carrying systems improve field efficiency.
10. Consistent tool placement reduces preparation time and improves operational efficiency.
11. Organized resource storage improves long-term settlement scalability.

# Survival Principles Recorded in Phase 0

1. Reliable drinking water source established.
2. Reliable sanitation area established.
3. Dense forest provides renewable survival materials.
4. Resource baskets improve transport efficiency.
5. Reliable fire management is essential for settlement stability.
6. Daily preparation routines improve survival efficiency.
7. Specialized production areas support progression from survival activities into organized settlement development.
8. Organized storage and resource management improve long-term sustainability.

---

# The Settlement Phase 0 Built

Recorded for reference. None of this is seeded into the current world.

**Districts:** Heavy Manufacturing (operational), Textile Manufacturing (operational), Arsenal (operational), Wolf Library (planned), Central Park (planned), Warehouse (planned), Wolf District (planned)

**Landmarks:** Wolf Stone, Wolf Fire Pit, The Archives, Wolf Knowledge Workstation

**Explored ground:** Northern Woodland, Rocky Transition, Natural Stone Quarry, Dense Forest Resource Zone, Forest Stream, Stream Bank Trail

**Designated utility areas:** Drinking Area, Urination Area — sited deliberately apart, which is the sanitation principle above expressed as geography.

---

# What This Playthrough Demonstrates About the Game

The first thing this player built was **The Archives** — before shelter, before weapons.

They were a QA Lead and Technical Product Manager, and they built a documentation system on Day 1 because that is who they are. A carpenter would have built a structure. A hunter would have pursued a bow.

That is the thesis of the whole game, and Phase 0 is the evidence it holds.

---

# Related Documents

| Document | Relevance |
|----------|-----------|
| [06.3 – Decision Log](06.3-Decision-Log.md) | DR-0013 / F8 monotonic schema decision |
| [domain-creation-pattern](../architecture/domain-creation-pattern.md) | How the Architect adds a domain |
| `V47__phase0_wolf_kingdom_heritage.sql` | The migration itself |
