# Project Draugr — Test Strategy

The single map of how this project is tested. It names the tiers, says what each is for, where
its tests live, how to run them, and what CI enforces — so a cycle adds a test to the *right*
place instead of re-deriving the structure.

> **The one principle.** The deterministic world is the source of truth and must be provably
> correct on its own; the AI layer is a pure upgrade that may only *append*, never break, and
> never cost a token on a failure path. The tiers below exist to keep both halves of that true.

---

## The pyramid

| Tier | Lives in | Needs | Runs locally? | Gate |
|---|---|---|---|---|
| **Unit** | `backend/src/test/**` (plain JUnit) + `frontend` `tsc -b` | nothing | ✅ always | CI `backend`, `frontend` |
| **Integration** | `backend/src/test/**/*IntegrationTest.java` (`@SpringBootTest` + Testcontainers) | Docker | ⚠️ only where Testcontainers can reach Docker (skips gracefully otherwise) | CI `backend` |
| **System / E2E** | `scripts/e2e/*.sh` | booted backend + Postgres | ✅ with a running stack | manual / pre-release |
| **Regression** | `*RegressionTest.java` (unit) · `tests/regression/*.sql` (SQL) · replay cases embedded in integration | varies | SQL replays: ✅ via `docker` | CI `backend` + `routing` + `regression` |
| **Migration / reachability** | `backend/src/main/resources/db/migration/V*.sql` + `docs/architecture/routing-reachability-probe.sql` | Postgres | ✅ via `docker` | CI `routing` |

Naming is the tier: a class ending `IntegrationTest` is the integration tier (boots a container);
one ending `RegressionTest` pins a specific past bug; everything else in `src/test` is unit.

---

## What each tier is for

### Unit — logic in isolation, no I/O
Pure functions and single services with no database or network. The narration brain lives almost
entirely here: `NarrationRouterTest` (the cost gate — which moments earn an AI call), `NarrationEngineTest`,
`NarrationPolicyTest` (the witness-stance guard), `ActionInputClassifierTest`, the intent classifier,
`QualityGradeTest`, the routing matchers, and the three AI agents against **stub** models
(`SimulationNarratorTest`, `AuditorSummarizerTest`, `PersistentStateArchitectTest`) — no key, no network.
`GlobalExceptionHandlerTest` covers the clean-response mapping **and** the hard-error tracker wiring.

Run: `mvn test` (from `backend/`). Frontend contract check: `npm run build` (from `frontend/`) — `tsc -b`
fails on any drift between a backend response shape and the client type that reads it.

### Integration — the real Spring context against a real Postgres
`@SpringBootTest` boots the whole application over a `PostgreSQLContainer`, so every JDBC write path,
Flyway migration, foreign-key ordering, immutability trigger, and transaction boundary executes exactly
as in production. `FullTickPlaythroughIntegrationTest` drives the authoritative survival loop end to end;
`SaveResumeDurabilityIntegrationTest` and `WorldEventArchiveIntegrationTest` cover persistence and the
append-only archive. Each `@BeforeAll` asserts Docker is available and **skips the class** otherwise, so a
Docker-less machine reports `Tests run: 0` instead of failing — the tier still runs in CI.

Run: `mvn test` where Testcontainers can reach the Docker engine (CI always; locally only if the engine is
reachable to Testcontainers — see the note in CLAUDE.md).

### System / E2E — the running product over HTTP
`scripts/e2e/api-regression.sh` drives a **booted** backend through real endpoints (awaken → act → read
history → archive → PDF), asserting status codes and payloads a player's client would see.
`scripts/e2e/locking-queries.sql` checks the row-locking that protects the single living chronicle.
Use before a release, or when a change touches the HTTP contract or a cross-request invariant.

### Regression — a past bug, pinned so it cannot return
Every fixed bug earns a test that fails on the old behaviour. Three homes, by what the bug lives in:
- **Logic bug →** a `*RegressionTest` unit class (e.g. `IntentClassificationRegressionTest`: word-boundary
  collisions that once stole a process key).
- **SQL / schema contract →** a runnable `tests/regression/*.sql` replay (see below).
- **Full-loop behaviour →** an ordered case inside an integration test (e.g. the #16 fresh-awakening death
  loop, pinned in `FullTickPlaythroughIntegrationTest`).

### Migration / reachability — the schema can always build and every process is reachable
CI applies **every** `V*.sql` in numeric order under `ON_ERROR_STOP` on a clean Postgres, then runs the
reachability probe (every VERIFIED process must route and none may collide). A DDL, ordering, or coverage
regression fails the build, not a player's first boot.

---

## Runnable SQL regression replays (`tests/regression/`)

Some contracts are purely at the database layer — the append-only immutability of `chronicle_action`, and
the narration overlay that lets AI/coda prose survive a reload without mutating that history. These are
verified by `tests/regression/run-sql-regressions.sh`, which stands up a throwaway Postgres via `docker`
(the same engine that's reachable on the dev machine even when Testcontainers is not), applies every
migration, and runs each `*.sql` replay under `ON_ERROR_STOP`. Each replay `RAISE`s on the old-bug
behaviour, so a regression exits non-zero.

```bash
tests/regression/run-sql-regressions.sh
```

| Replay | Pins |
|---|---|
| `immutability-and-overlay.sql` | `chronicle_action` rejects UPDATE/DELETE (the immutability trigger); the `chronicle_action_narration` overlay INSERTs cleanly and `COALESCE(overlay, base)` returns the enriched prose on read — the exact bug where AI narration issued an illegal `UPDATE chronicle_action` |

---

## Coverage map — the AI layer & recent fixes

| Area | Unit | Integration | Regression | Notes |
|---|---|---|---|---|
| Narration router (cost gate) | `NarrationRouterTest` | via full-tick | — | keeps ~90% of actions off the network |
| Simulation Agent refine() | `SimulationNarratorTest` (stub) | — | — | total on failure; never throws |
| Auditor / Architect | `AuditorSummarizerTest`, `PersistentStateArchitectTest` (stub) | — | — | read-only / authoring-time |
| **Immutability + narration overlay** | — | `NarrationOverlayIntegrationTest` (CI) | `tests/regression/immutability-and-overlay.sql` (local + CI) | the shipped fix; base row never mutated, overlay COALESCEd into history/journey/PDF |
| **Hard-error tracker** | `GlobalExceptionHandlerTest` | — | — | records persistence/unexpected; not client 4xx |
| Token-safety ordering | — | `NarrationOverlayIntegrationTest` (CI) | — | model call is the last fallible step |

---

## The rule for adding a test

1. **Fixing a bug?** Add its regression first (the tier table above tells you where), watch it fail, then fix.
2. **New deterministic behaviour?** Unit test the logic; if it touches the DB or a full request, add/extend an integration case.
3. **New AI seam?** Unit-test the agent against a **stub** model (never a live key in tests). If it persists, add the DB assertion to integration + a SQL replay for the storage contract.
4. **New endpoint or cross-request invariant?** Extend `scripts/e2e/api-regression.sh`.

Never put a live API key in a test. The AI tiers prove the *wiring and the fallback*, not the model.
