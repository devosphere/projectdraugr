/**
 * #238 — which manifest backdrop a place gets, as a pure function.
 *
 * The backend decides WHAT a place is and hands over a degrading chain of candidate keys (#225/#236):
 * `biome.temperate-forest.edge-grassland`, `biome.wetland.night`, `site.clay-deposit`, `interior.cave.dark`,
 * `world.default`. The manifest names images by slug: `forest`, `forest-grassland-ecotone`, `karst-cave-interior`.
 * This bridges the two without the browser learning anything about the world it was not told — it only ever reads
 * the candidates it was sent and the registry it was built with.
 *
 * It is kept free of Vite and the DOM on purpose, so it can be tested on its own. The loader supplies `available`.
 */

/** The part of a manifest record the runtime needs. */
export interface RuntimeRecord {
  backdropKey: string;
  filename: string;
  contentHash: string;
  label: string;
  lifecycle: string;
  biomes: string[];
  siteFamily: string;
  fallbackKey: string | null;
}

export interface Pick {
  record: RuntimeRecord;
  /** The candidate that led here, or 'root' when nothing did. */
  via: string;
}

/** The ground's own backdrop for each base biome, and for the presentation keys the location endpoint still sends. */
export const BIOME_ANCHOR: Record<string, string> = {
  TEMPERATE_FOREST: 'forest',
  WETLAND: 'wetland',
  GRASSLAND: 'plains',
  HIGHLAND: 'highland',
  MOUNTAIN: 'mountain-crag',
  OCEAN: 'open-ocean',
  RIVER_BANK: 'stream',
  COAST: 'coast',
  CLAY_DEPOSIT: 'clay-deposit',
  SALT_DEPOSIT: 'rock-salt-exposure',
  CAVE_INTERIOR: 'karst-cave-interior',
};

const MAX_FALLBACK_STEPS = 32;

const toBiome = (slug: string) => slug.toUpperCase().replace(/-/g, '_');

function rootOf(records: RuntimeRecord[]): RuntimeRecord | undefined {
  return records.find(r => r.fallbackKey === null);
}

/**
 * The manifest key a backend candidate names, or undefined when the registry has no image for exactly that. An
 * undefined answer is not a failure: the chain the backend sent degrades, and a later, plainer candidate answers.
 */
export function candidateToKey(candidate: string, records: RuntimeRecord[]): string | undefined {
  if (!candidate) return undefined;
  const byKey = (k: string) => records.some(r => r.backdropKey === k) ? k : undefined;

  if (candidate === 'world.default') return rootOf(records)?.backdropKey;
  if (candidate.startsWith('interior.cave')) return byKey(BIOME_ANCHOR.CAVE_INTERIOR);
  if (candidate.startsWith('site.')) return byKey(candidate.slice('site.'.length));
  if (candidate.startsWith('built.')) return undefined; // nothing built has scenery of its own yet

  if (candidate.startsWith('biome.')) {
    const [, biomeSlug, ...qualifiers] = candidate.split('.');
    const biome = toBiome(biomeSlug ?? '');
    // Snow and night have no images of their own in the registry: those links are skipped so the chain falls to a
    // plainer one, rather than showing a sunlit field at midnight under a daylight label.
    if (qualifiers.includes('snow') || qualifiers.includes('night')) return undefined;
    const setting = qualifiers[0];
    if (setting && (setting.startsWith('edge-') || setting.startsWith('beside-'))) {
      const other = toBiome(setting.replace(/^(edge|beside)-/, ''));
      const pair = records.find(r => r.siteFamily === 'ECOTONE' && r.biomes.length === 2
        && r.biomes.includes(biome) && r.biomes.includes(other));
      return pair?.backdropKey;
    }
    if (setting && setting !== 'deep') return undefined;
    const anchor = BIOME_ANCHOR[biome];
    return anchor ? byKey(anchor) : undefined;
  }

  // A presentation key from the location endpoint (TEMPERATE_FOREST, CLAY_DEPOSIT), or a manifest key already.
  const anchor = BIOME_ANCHOR[candidate];
  return anchor ? byKey(anchor) : byKey(candidate);
}

/**
 * The first backdrop a place can actually be shown with. Each candidate is tried in order; a candidate whose image
 * cannot be shown — not ACTIVE (quarantined, pending review, retired), not in this build, or already failed to load —
 * walks its own fallback chain before the next candidate is tried. The registry's root is the last resort, and null
 * means not even that can be shown.
 */
export function pickBackdrop(candidates: string[], records: RuntimeRecord[], available: (filename: string) => boolean,
                             rejected: ReadonlySet<string> = new Set()): Pick | null {
  const byKey = new Map(records.map(r => [r.backdropKey, r]));
  // A plain boolean, not a type guard: a guard would narrow a record that is merely unservable to `never`.
  const servable = (r: RuntimeRecord): boolean =>
    r.lifecycle === 'ACTIVE' && available(r.filename) && !rejected.has(r.filename);

  const walk = (start: RuntimeRecord | undefined): RuntimeRecord | undefined => {
    let current: RuntimeRecord | undefined = start;
    const seen = new Set<string>();
    for (let step = 0; current !== undefined && step < MAX_FALLBACK_STEPS; step++) {
      if (servable(current)) return current;
      if (seen.has(current.backdropKey) || current.fallbackKey === null) return undefined;
      seen.add(current.backdropKey);
      current = byKey.get(current.fallbackKey);
    }
    return undefined;
  };

  for (const candidate of candidates) {
    const key = candidateToKey(candidate, records);
    const found = key ? walk(byKey.get(key)) : undefined;
    if (found) return { record: found, via: candidate };
  }
  const root = rootOf(records);
  return root && servable(root) ? { record: root, via: 'root' } : null;
}
