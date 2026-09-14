# Playthrough backdrop registry

The canonical record of every `src/assets/playthrough-*.png` environment image (EPIC #222, story #223).
One machine-readable manifest binds each asset to the world context that may show it, so no image is
orphaned, duplicated, misclassified, or shown in an impossible place — and so the asset directory and
the registry can never silently drift apart.

## Files

| File | Role |
| --- | --- |
| `manifest.schema.ts` | Typed, versioned schema + enums (biomes, proximity, family, lifecycle). Consumed by the future frontend loader and any backend contract test. |
| `backdrop-manifest.json` | The generated source of truth: one `BackdropRecord` per asset. **Do not hand-edit** — regenerate. |
| `../../../scripts/backdrops/build-manifest.mjs` | `generate` \| `check` \| `report`. No dependencies (Node built-ins). |
| `../../../scripts/backdrops/test-validation.mjs` | Proves the gate rejects each drift class. |
| `../../../docs/systems/backdrop-coverage.md` | Human-reviewable coverage report, grouped by family. |

## Commands (run from `frontend/`)

```bash
npm run backdrops:generate   # rebuild the manifest from the assets on disk (after adding/removing an image)
npm run backdrops:check      # CI gate: directory⇄manifest bijection, unique keys/hashes, fallback integrity, on-disk hash match
npm run backdrops:report     # regenerate docs/systems/backdrop-coverage.md
npm run backdrops:test       # validator negative tests
```

`backdrops:check` and `backdrops:test` run in the `frontend` CI job (`.github/workflows/verify.yml`).

## What a record declares

Stable `backdropKey` (from the filename slug), display label, lifecycle; base `biomes[]` (ecotones list
two), `siteFamily`, canonical `contextKeys[]`, `proximity` (EXACT_SITE › ADJACENT_VISIBLE › ECOTONE ›
REGIONAL) and its `precedenceWeight`; temporal/`seasons`/`weather` eligibility where genuinely required;
`showBeforeDiscovery`; the `creatureFree` neutral-environment guarantee; a `fallbackKey` chain that
terminates at the single root (`forest`); and integrity metadata (`contentHash`, `width`, `height`,
`aspectRatio`, `bytes`).

## Invariants the schema deliberately keeps

- **No encounter presence.** A backdrop is neutral scenery. Living things are owned by the simulation,
  never by the image — the validator rejects any `creature`/`encounter`/`animalPresent` field.
- **Classification uses canonical world keys**, not free text. Base biomes are the backend `BiomeClimate`
  set: `TEMPERATE_FOREST, WETLAND, GRASSLAND, HIGHLAND, MOUNTAIN, OCEAN`.
- **Every asset resolves to something.** Discovery-gated sites, ruins, and monster territory fall back
  through their base-biome anchor to the root, so the screen is never blank.

- **Nothing goes live unreviewed (#241).** Every image passes the neutral-backdrop review contract in
  [`docs/systems/backdrop-review-contract.md`](../../../docs/systems/backdrop-review-contract.md):
  - A generated or replaced image enters `PENDING_REVIEW`.
  - `ACTIVE` needs an `APPROVED` review (every checklist item true, with a reviewer and a date) and widescreen
    dimensions.
  - The 151 pre-contract images are `LEGACY`, frozen in `scripts/backdrops/legacy-unreviewed.json`. That list only
    shrinks.
- **Replacement never overwrites history.** Regenerating merges over the previous manifest. A changed image appends
  its old `{version, contentHash}` to `provenance.supersedes` and returns to `PENDING_REVIEW`.

## Adding or changing an image

1. Generate it to the constraints in the review contract, and drop the `playthrough-<slug>-v<N>.png` into
   `src/assets/`. Bump `N` when replacing an image.
2. If the slug tokens don't classify it correctly, add a one-line entry to `OVERRIDES` in
   `build-manifest.mjs`.
3. `npm run backdrops:generate`. The image enters `PENDING_REVIEW`.
4. Review it against the checklist on the playthrough screen, then record the review in the manifest.
5. `npm run backdrops:check`, then `npm run backdrops:test`. Commit the image **and** the manifest.
