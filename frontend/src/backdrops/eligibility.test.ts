import { describe, expect, it } from 'vitest';
import manifest from './backdrop-manifest.json';
import { contextMatches, type RuntimeContext } from './resolveBackdrop';

/**
 * #229 — every one of the 151 assets is accounted for: selectable, or the root, or gated with a reason.
 *
 * `resolveBackdrop.test.ts` already proves every key RESOLVES to something showable. This asks the question
 * before that one: **can this record ever be the thing chosen at all?**
 *
 * Selection in `pickBackdrop` is by CONTEXT ONLY. For each candidate the backend sends it collects every record
 * whose declared `contexts` match, then walks that record's own fallback chain to the first servable one. The
 * consequence is easy to miss: **with a complete asset set, every selected record is itself servable, so the walk
 * returns it immediately and fallback never fires.** A record is therefore showable if and only if it is directly
 * selectable — it declares at least one context — or it is the registry root, the last resort.
 *
 * So a record with no contexts can never be shown however many images ship. 55 of the 151 are in that state, and
 * every single one of them carries a `gate` naming the reason and the issue that would lift it. That is the
 * manifest being honest about its own debt, and **nothing enforced it**: an ACTIVE record added with no contexts
 * and no gate would join them in silence, and a gate with no `activatedBy` would make the debt untrackable.
 *
 * These tests hold the manifest to the partition it already keeps perfectly.
 */

interface Record {
  backdropKey: string;
  lifecycle: string;
  fallbackKey: string | null;
  contexts?: RuntimeContext[];
  gate?: { reason?: string; activatedBy?: string } | null;
}

const RECORDS = (manifest as { backdrops: Record[] }).backdrops;
const selectable = (r: Record) => (r.contexts ?? []).length > 0;
const isRoot = (r: Record) => r.fallbackKey === null;

describe('every asset is accounted for', () => {
  it('has the whole registry, so a shrinking manifest cannot make these pass vacuously', () => {
    expect(RECORDS.length).toBeGreaterThanOrEqual(151);
  });

  it('partitions exactly into selectable, root, and gated — nothing silently unreachable', () => {
    const unexplained = RECORDS.filter(r => !selectable(r) && !isRoot(r) && !r.gate);
    expect(unexplained.map(r => r.backdropKey)).toEqual([]);
  });

  it('never both declares a context and claims to be gated', () => {
    // A record that is selectable AND gated is telling two stories. Whichever is true, the other misleads
    // whoever reads the manifest to decide what is still owed.
    const contradictory = RECORDS.filter(r => selectable(r) && r.gate);
    expect(contradictory.map(r => r.backdropKey)).toEqual([]);
  });

  it('gives every gate a reason and the issue that would lift it, so the debt stays tracked', () => {
    const vague = RECORDS.filter(r => r.gate && !(r.gate.reason ?? '').trim());
    expect(vague.map(r => r.backdropKey)).toEqual([]);
    const untracked = RECORDS.filter(r => r.gate && !(r.gate.activatedBy ?? '').trim());
    expect(untracked.map(r => r.backdropKey)).toEqual([]);
  });

  it('has exactly one root, and the root needs no context because it is the last resort', () => {
    const roots = RECORDS.filter(isRoot);
    expect(roots).toHaveLength(1);
    expect(roots[0].lifecycle).toBe('ACTIVE');
  });

  it('declares only context shapes the resolver can actually match', () => {
    // A context of a shape `contextMatches` cannot understand is dead weight that READS as live routing — the
    // record looks routed and can never be chosen, which is the same defect as declaring no context at all but
    // harder to see.
    //
    // Each declared context is fed to the real matcher with a candidate built to its own terms, so this cannot
    // drift from the matcher's rules. Both forms need care, and getting them wrong is how this test first failed:
    // `edge-or-beside` slugs its biomes (lowercase), and `whenBiome` is satisfied from the rest of the CHAIN
    // rather than from the candidate, so the chain has to carry the ground as the backend's would.
    const unmatchable: string[] = [];
    for (const r of RECORDS) {
      for (const c of r.contexts ?? []) {
        const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-');
        let candidate: string;
        let chain: string[];
        if ('candidate' in c) {
          candidate = c.candidate;
          chain = c.whenBiome ? [candidate, `biome.${slug(c.whenBiome)}`] : [candidate];
        } else {
          const [a, b] = (c.biomes ?? []).map(slug);
          candidate = `biome.${a}.edge-${b}`;
          chain = [candidate, `biome.${a}`];
        }
        if (!contextMatches(c, candidate, chain)) unmatchable.push(`${r.backdropKey}: ${JSON.stringify(c)}`);
      }
    }
    expect(unmatchable).toEqual([]);
  });

  it('keeps the gated set from growing: a new gate is debt, and debt must be a decision', () => {
    // A ratchet, not a target. These 55 are the state of the manifest as #229 found it, each with a reason and an
    // activating issue. The number must only ever go DOWN — when a gate's reason stops being true, its record
    // gets contexts and leaves this set. If adding an asset pushes it up, that asset is being shipped unshowable
    // and the decision to do so should be a deliberate edit to this number rather than a silent one.
    const gated = RECORDS.filter(r => r.gate);
    expect(gated.length).toBeLessThanOrEqual(55);
  });
});
