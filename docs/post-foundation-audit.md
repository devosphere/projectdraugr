# Post-Foundation Audit — what's needed after M1–M5

> Written 2026-08-01, on completing the routing-hardening sequence (M1–M5, migrations V54–V60). Grounds the next phase so cycles don't re-derive it. See [[feedback-decide-and-document]].

## Where the project stands

The deterministic foundation is **complete**. Sixty migrations; 130 verified material processes across 10 activity categories; a two-axis router that resolves ordinary phrasing without an AI call; staged assembly with cures; an ordered quality grade that flows input→output with inspection and rework; and a coverage gate that fails validation on regression. The four procedure simulations resolve 46/46. This was always the *substrate* — the thing the three AIs stand on so they stay cheap and auditable.

**So the headline of this audit is: the foundation is done, and the three AIs are now the critical path.** Everything else is consolidation.

---

## 1. The three AIs — the actual next phase (highest value)

None of the three is wired to a real model yet (`grep` finds no LLM client in the backend). The foundation existed to make them affordable and safe; now they are what turns a simulator into the game.

- **Simulation Agent's voice — AI narration (Task #21).** The seam is fully built: `NarrationRouter` decides *whether* a moment earns a call, `NarrationEngine` supplies deterministic `backendNarration` as the floor, `PerceptionFrame` carries the context. What's missing is the client that calls the model with the refinement prompt. **This is the single biggest felt improvement and the most shovel-ready** — the cost-control layer that makes it viable is exactly what M1–M5 hardened. Start here. Needs: an Anthropic client, the prompt template in `narration-engine.md`, streaming to the frontend, and a spend cap.
- **Persistent State Architect — authoring-time schema growth.** Today only the *gate* exists (`ArchitectRouter`: COVERED/POLISH/INVENT). The AI that actually *writes a migration* when play hits INVENT is not built. The strategy doc settled that this is authoring-time, offline, one call per novel verb/process ever — and V56's `routing_miss_backlog` plus M5's per-category coverage now *feed* it with exactly what to build. This is the second AI and the one that keeps the world growing without per-action spend.
- **Persistent State Auditor — mostly done.** Read-only, implemented, runs at launch and on a schedule, and now guards routing, assembly, and quality invariants. The remaining piece is the loop that hands `NEEDS_REFINEMENT` findings to the Architect and re-reviews the result.

## 2. Make the gates actually enforce (medium — cheap, high leverage)

The gates exist but are half-manual:

- **The M5 coverage gate isn't in CI.** `verify.yml` runs `mvn test` + the frontend build, but never applies migrations or runs `routing-reachability-probe.sql`. Add a CI job: spin Postgres, apply V1–V60 with `ON_ERROR_STOP=1`, run the probe. Then "coverage regression fails the build" is true in CI, not just in the local workflow.
- **Migration validation isn't in CI either.** The from-scratch apply + Auditor-clean check is done by hand every migration (per CLAUDE.md). CI should do it, including asserting the launch audit is clean.

## 3. Automated coverage of the new mechanics (medium-high)

V58–V60 (assembly, cures, quality, rework, multi-output, preservation) are verified **only by manual end-to-end runs** in these sessions. The `AssemblyService`/quality/multi-output paths are DB-coupled and have no DB-free unit tests; the Testcontainers integration tests (which *do* run in CI) don't exercise them. One integration test that builds the bow through its cure, botches and reworks a stage, and asserts grades/spoilage would lock in the whole milestone against regression.

## 4. Frontend — the new depth is invisible (medium)

The frontend is data-driven and builds clean, but there is **no UI for anything M3–M4 added**: no assembly progress or cure timer, no quality grades, no `INSPECT` surface, no staged-build state. The game is fully playable through the text composer, but a player can't *see* a half-built bow curing or a defective plate. Plus the standing known-gaps: furniture panel, named locations in the header, reading documents off shelves, Archive visual verification.

## 5. Deliberately deferred — track, don't forget (low)

- **FINE-grade sources.** The grade dimension is complete but FINE is rarely produced, because raw materials default SOUND. A prime hide from a clean kill, select timber, etc. would let "fine in, fine out" actually happen.
- **Lean-to / fire-pit runtime cut-over.** They are expressed as staged data (V58) but still built by their old intents; the runtime move onto the engine was deferred to avoid regressing them.
- **Three-layer success model** is wired into only 2 intents (LIGHT_FIRE, CONFRONT_WILDLIFE); crafting/wounds are still binary — now that `QualityGrade.attempt` reads text care, extending it is natural.
- **Fire kit never wears out; friction fires are infinite.** Wear/degradation is unbuilt.
- **Coexisting build paths.** Bow and armour can be made both by a single-shot process and by a staged assembly. Intentional (fine control vs guided), but worth a deliberate decision if it ever confuses.

## Recommended order

1. **AI narration (Task #21)** — biggest felt gain, fully unblocked, the reason the foundation exists.
2. **CI: coverage gate + migration validation + launch-audit check** — cheap, makes the discipline self-enforcing before more content lands.
3. **One integration test across the V58–V60 mechanics** — lock the milestone in.
4. **Architect authoring loop** — turn the backlog into migrations; the second AI.
5. Frontend surfaces for assembly/quality; then the deferred enrichments.

Task #13 (verify writing flow E2E in a live playthrough) remains open and is a good warm-up playtest once narration lands.
