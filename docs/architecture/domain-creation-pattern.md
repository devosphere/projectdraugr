# Domain-Creation Pattern

> **Project Draugr — the Persistent State Architect's recipe**
>
> *A domain is invented once, for all time.*

---

## What a domain is

A **domain** is a coherent capability the world knows how to be: fire, literature,
navigation — and, in the world's future, pottery, smithing, textiles, agriculture,
medicine. Each domain is a small cluster of schema (tables, item definitions) plus the
service and intent wiring that lets a chronicle act within it.

The authoritative list of domains that exist is the [`domain_registry`](../../backend/src/main/resources/db/migration/V38__create_domain_registry.sql)
table. The Persistent State Architect consults it before adding anything, so a domain is
never given schema twice.

## The governing decisions

- **Monotonic schema (DR-0013).** Schema is global and grows in one direction. When a
  domain is invented for the first time *anywhere* in the world's history, the Architect
  adds its tables **once**. Every later chronicle inherits a world where that schema
  exists — but starts with zero `chronicle_capability_adaptation` in it. You can find a
  dead chronicle's kiln; you do not inherit their skill at it.
- **Realism is the gate (Core Rule #5).** A domain is added only when a living
  civilization actually reaches it. Pottery arrives when a chronicle fires clay in a
  kiln, not before. The registry's `origin` column marks the foundation domains the
  developer pre-migrated (`PREBUILT`) apart from those invented during play (`INVENTED`).
- **Nothing is deleted.** A domain, once known, is known forever. A migration only ever
  adds.

## The recipe

Adding a domain is one Flyway migration plus the code that acts on it. Do it in this
order — the same order every existing domain followed.

### 1. Confirm the domain does not yet exist

Query `domain_registry` (or call `DomainRegistryService.exists(key)`). If the domain is
already registered, its schema exists; do not add it again — extend it instead.

### 2. Write the Flyway migration `Vnn__add_<domain>.sql`

The next number follows the highest existing migration. In one migration:

- **Item definitions** the domain introduces, via
  `INSERT INTO item_definition (...) ON CONFLICT (item_key) DO NOTHING`.
- **New tables** for the domain's runtime state. Follow the existing conventions:
  `object_id UUID` keyed to `world_object`/`item_instance` for anything physical; a
  `world_id` scope where the state is world-wide; append-only history in its own table,
  never mixed with mutable runtime rows.
- **The registry row**, in the same migration:
  ```sql
  INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
  VALUES ('pottery','Pottery','Kilns, clay vessels, and firing state.','Vnn','INVENTED');
  ```

### 3. Add a service

A `@Service` holding the domain's mutation logic, using raw `JdbcTemplate` (no ORM),
each mutation `@Transactional`. Physical objects are created as a `world_object` row +
their type-specific row + an `object_transition` for provenance. Objects are never
deleted — they transition to `DESTROYED` with `destroyed_location_id` and
`destroyed_cause` set.

### 4. Wire the intent

In `ChronicleActionService`, in this exact order (the house pattern):

1. **Intent enum** — add the new intent.
2. **Duration switch** — how long the act takes.
3. **Classifier keyword check** — the natural-language cues that route to it. All player
   intent arrives through the action composer; there is no UI button.
4. **Dispatch block** — call the service, set `outcome` and the witness-only
   `perception` prose. The narrator describes the attempt and its result; it never hints
   at what was missing.
5. **Service method** — the call itself.

New physical object types placed at a location surface automatically in the perception
frame's `nearbyObjects` (attention-permitting), so no frame change is usually needed.

### 5. Add invariant checks

Extend `PersistentStateAuditor` with the logical invariants the new tables must always
satisfy — the ones the foreign keys cannot express (a live process with no fuel, an
exhausted resource left active, a current pointer that is stale or foreign). The Auditor
only reads; never add mutation there.

### 6. Respect the three-layer success model

If the domain's actions are procedurally complex (like fire or the hunt), gate them
through the three layers, not a coin flip: physical prerequisites first (hard gate),
then `SuccessModel.specificity` on the action text, then
`CapabilityAdaptationService.familiarity` for the practiced hand.

## Where each piece lives

| Piece | Location |
|-------|----------|
| Migration | `backend/src/main/resources/db/migration/Vnn__add_<domain>.sql` |
| Registry row | inside that migration, `INSERT INTO domain_registry` |
| Service | `backend/src/main/java/com/devosphere/draugr/<domain>/` |
| Intent wiring | `action/ChronicleActionService.java` |
| Success layers | `action/SuccessModel.java`, `capability/CapabilityAdaptationService.java` |
| Invariants | `audit/PersistentStateAuditor.java` |
| Registry read | `domain/DomainRegistryService.java`, `GET /api/domains` |

## The boundary that must hold

Only the Architect writes migrations. Only the Simulation Agent narrates. The Auditor
only reads. A new domain touches all three seams — schema (Architect), action and
narration (Simulation), invariants (Auditor) — but never blurs them.
