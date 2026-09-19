import { describe, expect, it } from 'vitest';
import manifest from './backdrop-manifest.json';
import { pickBackdrop, recordsFor, type RuntimeRecord } from './resolveBackdrop';

/**
 * #227 — every manifest key is reachable to something that can be shown.
 *
 * A build ships only the images present in `src/assets`, and CI has none but the registry root's neighbours; a
 * player's build has all of them. So the promise to test is not "every key has an image" but "every key resolves to
 * a scene whatever subset of images is present": served itself when it is there and shown, and otherwise walked down
 * its declared fallback chain to one that is — never to a blank screen, never round a loop.
 */

const RECORDS: RuntimeRecord[] = (manifest as { backdrops: RuntimeRecord[] }).backdrops.map(b => ({
  backdropKey: b.backdropKey, filename: b.filename, contentHash: b.contentHash, label: b.label,
  lifecycle: b.lifecycle, fallbackKey: b.fallbackKey, precedenceWeight: b.precedenceWeight, contexts: b.contexts ?? [],
}));
const byKey = new Map(RECORDS.map(r => [r.backdropKey, r]));
const root = RECORDS.find(r => r.fallbackKey === null)!;
/** A context a record declares, turned into the candidate chain the backend would send for it. */
function chainFor(record: RuntimeRecord): string[] | null {
  const context = record.contexts[0];
  if (!context) return null;
  if ('candidate' in context) return context.whenBiome ? [context.candidate, `biome.${context.whenBiome.toLowerCase().replace(/_/g, '-')}`] : [context.candidate];
  const [a, b] = context.biomes.map(x => x.toLowerCase().replace(/_/g, '-'));
  return [`biome.${a}.edge-${b}`];
}

describe('every manifest key resolves to a scene', () => {
  it('has exactly one root, and it is servable', () => {
    expect(RECORDS.filter(r => r.fallbackKey === null)).toHaveLength(1);
    expect(root.lifecycle).toBe('ACTIVE');
  });

  it('every fallback chain ends at the root without a loop', () => {
    for (const record of RECORDS) {
      const seen = new Set<string>();
      let current: RuntimeRecord | undefined = record;
      while (current && current.fallbackKey !== null) {
        expect(seen.has(current.backdropKey), `${record.backdropKey} loops at ${current.backdropKey}`).toBe(false);
        seen.add(current.backdropKey);
        current = byKey.get(current.fallbackKey);
        expect(current, `${record.backdropKey} falls back to a key that does not exist`).toBeDefined();
      }
      expect(current?.backdropKey).toBe(root.backdropKey);
    }
  });

  it('with every image present, a reachable ACTIVE record is chosen for its own context', () => {
    const all = () => true;
    for (const record of RECORDS.filter(r => r.lifecycle === 'ACTIVE' && r.contexts.length > 0)) {
      const chain = chainFor(record)!;
      const pick = pickBackdrop(chain, RECORDS, all);
      expect(pick, `${record.backdropKey} is reachable by ${chain} but resolved to nothing`).not.toBeNull();
      // Another record may outrank it for the same context by precedence; what must hold is that the pick answers it.
      expect(recordsFor(chain[0], RECORDS, chain).map(r => r.backdropKey), `${record.backdropKey} does not answer its own context`)
        .toContain(record.backdropKey);
    }
  });

  it('with only the root present, every declared context still resolves to a scene', () => {
    const onlyRoot = (filename: string) => filename === root.filename;
    for (const record of RECORDS.filter(r => r.contexts.length > 0)) {
      const chain = chainFor(record)!;
      const pick = pickBackdrop(chain, RECORDS, onlyRoot);
      expect(pick?.record.backdropKey, `${record.backdropKey} left a blank screen when its image was missing`).toBe(root.backdropKey);
    }
  });

  it('an image rejected at decode is never chosen again, and something else is', () => {
    const all = () => true;
    const activeWithContext = RECORDS.find(r => r.lifecycle === 'ACTIVE' && r.contexts.length > 0 && r.fallbackKey !== null)!;
    const chain = chainFor(activeWithContext)!;
    const first = pickBackdrop(chain, RECORDS, all)!;
    const second = pickBackdrop(chain, RECORDS, all, new Set([first.record.filename]));
    expect(second).not.toBeNull();
    expect(second!.record.filename).not.toBe(first.record.filename);
  });
});
