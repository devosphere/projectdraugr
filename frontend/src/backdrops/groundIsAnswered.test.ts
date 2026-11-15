import { describe, expect, it } from 'vitest';
import manifest from './backdrop-manifest.json';
import { namesTheGround, pickBackdrop, recordsFor, type RuntimeRecord } from './resolveBackdrop';

/**
 * #229 — every ground the world actually generates is answered, and no place is shown as a forest unless it is one.
 *
 * `resolveBackdrop.test.ts` proves the other direction: that every key the MANIFEST declares resolves to something
 * showable. That says nothing about whether the manifest answers what the BACKEND sends. The backend's fallback
 * design rests on one sentence in `BackdropResolver`:
 *
 *   "still ends on a bare biome key, which every generated biome has a backdrop for"
 *
 * Nothing checked it. It is a cross-stack promise — the resolver is Java, the manifest is frontend data — so it
 * could only ever have been checked from one side or the other, and it was checked from neither.
 *
 * WHY IT MATTERS, in the words of the defect it already cost: a `built.*` candidate that nothing answered let the
 * chain run out, and `pickBackdrop` fell to the registry ROOT — a forest. "So finishing a shelter changed the view
 * from the marsh you are standing in to a wood you are not." That was fixed for the built tier by appending the
 * ground. The whole fix leans on the ground itself being answerable, which is what this file holds to account.
 *
 * MEASURED, and it holds: nine of the ten generated biomes are answered by an ACTIVE record, and the tenth —
 * CAVE_INTERIOR — is never emitted as a biome key at all. `groundKeyOf` returns null inside the rock and the
 * interior tier answers instead, with `interior.cave.lit` / `interior.cave.dark`, both answered by
 * `karst-cave-interior`. The design is sound; this is the guard that keeps it so.
 */

const RECORDS: RuntimeRecord[] = (manifest as { backdrops: RuntimeRecord[] }).backdrops.map(b => ({
  backdropKey: b.backdropKey, filename: b.filename, contentHash: b.contentHash, label: b.label,
  lifecycle: b.lifecycle, fallbackKey: b.fallbackKey, precedenceWeight: b.precedenceWeight, contexts: b.contexts ?? [],
}));
const root = RECORDS.find(r => r.fallbackKey === null)!;

/**
 * Every biome this world puts on the ground, from `SELECT DISTINCT biome FROM world_chunk` against a database built
 * from nothing — not a list of the biomes someone meant to support. A biome added to genesis without a backdrop
 * must fail here rather than quietly show the player a forest.
 */
const GENERATED_BIOMES = [
  'CAVE_INTERIOR', 'CAVE_MOUTH', 'COAST', 'GRASSLAND', 'HIGHLAND',
  'MOUNTAIN', 'OCEAN', 'RIVER_BANK', 'TEMPERATE_FOREST', 'WETLAND',
] as const;

const slug = (s: string) => s.toLowerCase().replace(/_/g, '-');

/**
 * The candidate the backend ends its chain on for this ground — `BackdropResolver.groundKeyOf`, which returns null
 * for CAVE_INTERIOR because inside the rock the interior tier answers instead.
 */
function endingCandidatesFor(biome: string): string[] {
  return biome === 'CAVE_INTERIOR' ? ['interior.cave.lit', 'interior.cave.dark'] : [`biome.${slug(biome)}`];
}

/** A player's build holds every image; the question here is whether anything ANSWERS the ground, not what shipped. */
const everyImagePresent = () => true;

describe('every ground the world generates is answered', () => {
  it('each generated biome has at least one ACTIVE record that answers its ending candidate', () => {
    const unanswered: string[] = [];
    for (const biome of GENERATED_BIOMES) {
      for (const candidate of endingCandidatesFor(biome)) {
        const answering = recordsFor(candidate, RECORDS, [candidate]).filter(r => r.lifecycle === 'ACTIVE');
        if (answering.length === 0) unanswered.push(`${biome} (${candidate})`);
      }
    }
    expect(unanswered, `no ACTIVE backdrop answers these grounds, so standing on them falls to the registry root `
      + `— the forest: ${unanswered.join(', ')}`).toEqual([]);
  });

  /**
   * THE DEFECT SHAPE ITSELF. Falling back to the root is the design's last resort and it is a LIE about the place:
   * a marsh shown as a wood is a confidently wrong answer in the same even voice as a true one.
   */
  it('no generated ground resolves to the forest root unless it is the forest', () => {
    const lies: string[] = [];
    for (const biome of GENERATED_BIOMES) {
      const chain = endingCandidatesFor(biome);
      const shown = pickBackdrop(chain, RECORDS, everyImagePresent);
      if (!shown) { lies.push(`${biome} -> nothing at all`); continue; }
      if (shown.record.backdropKey === root.backdropKey && biome !== 'TEMPERATE_FOREST') {
        lies.push(`${biome} -> ${root.backdropKey}`);
      }
    }
    expect(lies, `these grounds would be shown as something they are not: ${lies.join(', ')}`).toEqual([]);
  });

  /**
   * AND THE OTHER BUILD — the one without the art (#932).
   *
   * <p>The tests above are written with `everyImagePresent`, which is the player's build. A CLEAN CHECKOUT holds
   * four of the hundred and fifty backdrops: the rest are generated locally and deliberately not committed. So
   * every candidate misses, every chain walks down to the registry root, and the forest plate is what the whole
   * world is shown with. There is nothing wrong with that — it is the last resort doing its job, and there is no
   * other image to show.
   *
   * <p>What is wrong is NAMING it. The root's label is "Forest", and the header printed it as the locality
   * beneath the ground, so a clean build read "Cave Interior / FOREST", "Grassland / FOREST", "Wetland / FOREST"
   * — the exact symptom #878 was filed about, invisible to anyone whose assets are present. Measured live before
   * the fix: `CAVE INTERIOR | Morning | FOREST`. After it: `CAVE INTERIOR | Morning`, same stand-in picture.
   */
  it('in a build with only the committed art, no ground is NAMED after the stand-in it is shown with', () => {
    const COMMITTED = new Set([
      'playthrough-clay-deposit-v1.png', 'playthrough-forest-v1.png',
      'playthrough-quarry-v1.png', 'playthrough-stream-v1.png',
    ]);
    const named: string[] = [];
    let standingIn = 0;
    for (const biome of GENERATED_BIOMES) {
      const shown = pickBackdrop(endingCandidatesFor(biome), RECORDS, f => COMMITTED.has(f));
      expect(shown, `${biome} could not even be shown the stand-in`).toBeTruthy();
      // A ground whose own art happens to be committed is named, and rightly — the river bank keeps `stream`.
      // The claim here is only about the grounds shown the ROOT.
      if (shown!.record.backdropKey !== root.backdropKey) continue;
      standingIn++;
      if (namesTheGround(shown!)) named.push(`${biome} -> ${shown!.record.label}`);
    }
    expect(standingIn, 'this build must actually fall to the stand-in, or the test proves nothing')
      .toBeGreaterThan(0);
    expect(named, `these grounds would be labelled after a picture that is only standing in for them: `
      + `${named.join(', ')}`).toEqual([]);
  });

  /** The converse, so the fix cannot be "never name anything": a real scene still names the place it is. */
  it('a ground answered by its own scene still names that place', () => {
    const shown = pickBackdrop(['biome.wetland'], RECORDS, everyImagePresent);
    expect(shown, 'the wetland has a scene of its own').toBeTruthy();
    expect(shown!.record.backdropKey, 'and it is not the root').not.toBe(root.backdropKey);
    expect(namesTheGround(shown!), 'so its label is a place and may be shown as one').toBe(true);
  });

  /** And the ground is answered with a scene, not merely with a record — the chain must be walkable to an image. */
  it('each generated ground resolves to a showable scene', () => {
    for (const biome of GENERATED_BIOMES) {
      const shown = pickBackdrop(endingCandidatesFor(biome), RECORDS, everyImagePresent);
      expect(shown, `${biome} resolved to no scene`).toBeTruthy();
      expect(shown!.record.lifecycle, `${biome} resolved to a record that cannot be shown`).toBe('ACTIVE');
    }
  });
});
