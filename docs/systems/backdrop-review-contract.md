# Backdrop generation and review contract

Task #241, under story #228 and epic #222. This is the contract every playthrough backdrop passes before a player can see it. The validator (`scripts/backdrops/build-manifest.mjs`) enforces it, and `npm run backdrops:test` proves the enforcement. Neither is a style guide.

## Why it exists

A backdrop is scenery. The simulation owns everything alive: whether a wolf is at the den is a fact of the world, decided by what the Chronicle can perceive. If a wolf is painted into the den picture, the screen shows an encounter the world never had, and it shows it before discovery. So a backdrop may show where a creature lives, but never the creature.

Before this contract, the manifest generator wrote `lifecycle: ACTIVE` and `creatureFree: true` for every image it found. Those were assertions no one had checked.

## Generation constraints

Use these in every prompt that produces a backdrop, and as the first pass of review.

**The frame shows a place, with nothing alive in it**
- No animals, birds, fish, insects, people, monsters, or native peoples, in the foreground or the distance.
- No silhouettes that read as a figure, and no eyes in the dark.
- Allowed: **nonliving habitat evidence**, such as tracks, a den mouth, an empty nest, droppings, bones, a worn game trail, a wallow, gnawed bark, a spent fire. The place may say something was here, but not that it is here now.
- Plants are not creatures, and neither is weather.

**Rendering**
- Smooth, modern, painterly or photographic rendering.
- Not pixel art, not low-poly, not a tileset.
- No grids, repeating tiles, visible seams, upscaled pixels, text, watermarks, UI, or frames.

**Dimensions**
- Widescreen at about 16:9: aspect ratio 1.75–1.80, at least 1600 × 900 pixels.

**Composition**
- The defining geography must survive the playthrough screen. The header takes the top band, the Body HUD the left edge, and narration and the action composer the lower third.
- Put the seam, spring, den mouth or ruin in the middle of the frame, not under a panel.
- Keep sky and ground calm enough that panel text stays readable over them.

**Naming**
- `playthrough-<slug>-v<N>.png`. The slug uses the world's own vocabulary (see `build-manifest.mjs`).
- Never reuse a slug for a different place.

## Review checklist

Each item is a reviewer's statement about the image, recorded in the record's `review.checklist`.

| item | the reviewer states |
|---|---|
| `noLivingCreatures` | Nothing alive is visible anywhere in the frame, at full size and zoomed. |
| `onlyNonlivingHabitatEvidence` | Any sign of wildlife is evidence (tracks, den, nest, bones), never the animal. |
| `smoothModernRendering` | The rendering is painterly or photographic, with no artefacts. |
| `noGridTilingOrPixelation` | No tiling, grid, seam, upscale pixelation, text or watermark. |
| `widescreenDimensions` | About 16:9 and at least 1600 × 900. |
| `uiSafeComposition` | Viewed on the playthrough screen, the defining feature is not hidden under a panel. |

A review is **APPROVED** only when every item is `true` and `reviewedBy` and `reviewedAt` are filled in.

## Lifecycle and what the validator refuses

| state | meaning |
|---|---|
| `PENDING_REVIEW` | Where every newly generated or replaced image starts. It is never shown. |
| `ACTIVE` | Shown. Requires an **APPROVED** review, or **LEGACY** (below), and widescreen dimensions. |
| `TOPOLOGY_GATED` | Kept but not shown until the world feature it depicts exists. |
| `DEPRECATED` | Retired. Kept for history. |
| `QUARANTINED` | An audit found a creature, or something that could be one (#240). Never shown, never a fallback target, never LEGACY. It leaves quarantine only by being replaced and re-reviewed. |

**Audit findings (#240).** Each review carries `findings`: `{issue, notes, disposition, source, recordedAt}`.
- `CREATURE` and `AMBIGUOUS` findings require `QUARANTINED`, with the review in the `FLAGGED` state.
- `RENDERING` blocks approval but not display.
- `NONE` means a pre-check found nothing.
- `source` is `AUTOMATED_VISION` or `HUMAN`. Automated vision may flag but never approve, so the validator refuses an APPROVED review whose reviewer is automated.

The 2026-09-15 audit results are in [`backdrop-audit-240.md`](backdrop-audit-240.md).

`npm run backdrops:check` fails if any of these happens:

- A record has no review, or states a checklist item as anything but a boolean.
- A record is `ACTIVE` with a `PENDING` review.
- A review is `APPROVED` with an unchecked item, no reviewer, or no date.
- A record claims `creatureFree` when no reviewer checked `noLivingCreatures`.
- An `ACTIVE` image is not widescreen.
- A record uses `LEGACY` on a key outside the frozen list, or on a replaced image.
- A replaced image keeps no record of what it replaced.

## Legacy images

The 151 images committed before this contract were `ACTIVE` on trust. They carry `review.state: LEGACY`, and their keys are frozen in `scripts/backdrops/legacy-unreviewed.json`.

- The list **only shrinks**. When #240 audits an image, its record becomes `APPROVED` (or it is regenerated or retired) and its key leaves the list.
- A new image never joins the list. A key outside it cannot use `LEGACY`, so the only route to `ACTIVE` for anything new is a completed review.

## Replacement and provenance

Regenerating the manifest **merges over the previous one** (`mergeRecord`); it never overwrites.

- **Unchanged image** (same content hash): it keeps its lifecycle, review, creature-free statement, and history.
- **Replaced image** (different content hash):
  1. The old `{version, contentHash, retiredAt}` is appended to `provenance.supersedes`.
  2. The record goes back to `PENDING_REVIEW` with a blank checklist, because a review of the old picture says nothing about the new one.
  3. Bump the `-v<N>` suffix. The validator refuses a history entry that is not earlier than the current version, and a version above 1 that does not name the one before it.
- **Brand-new image:** it starts `PENDING_REVIEW` with `creatureFree: false`.

## Adding or replacing an image

1. Generate it to the constraints above. Name it `playthrough-<slug>-v<N>.png`, bumping `N` if you are replacing an image.
2. `npm run backdrops:generate`. The image enters as `PENDING_REVIEW`.
3. Review it against the checklist, **on the playthrough screen**, not only in an image viewer.
4. In `backdrop-manifest.json`:
   - set every checklist item true;
   - set `review.state: APPROVED`, `reviewedBy` and `reviewedAt`;
   - set `creatureFree: true` and `lifecycle: ACTIVE`.
5. `npm run backdrops:check` and `npm run backdrops:test`. Commit the image, the manifest and nothing else.
