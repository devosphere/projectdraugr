/**
 * #238/#235 — which manifest backdrop a place gets, as a pure function.
 *
 * The backend decides WHAT a place is and hands over a degrading chain of candidate keys (#225/#236):
 * `site.clay-beds`, `biome.temperate-forest.edge-grassland`, `biome.wetland.night`, `interior.cave.dark`,
 * `world.default`. Each manifest record declares, in its `contexts`, which of those it answers to — so routing is by
 * what a record says it is for, never by what its file is called (#235). A record with no context carries a `gate`
 * saying why nothing reaches it yet, and is simply never chosen.
 *
 * Kept free of Vite and the DOM on purpose, so it can be tested on its own. The loader supplies `available`.
 */

/** What the world must be for a record to be chosen. Mirrors BackdropContext in manifest.schema.ts. */
export type RuntimeContext =
  | { candidate: string; whenBiome?: string }
  | { setting: string; biomes: string[] };

/** The part of a manifest record the runtime needs. */
export interface RuntimeRecord {
  backdropKey: string;
  filename: string;
  contentHash: string;
  label: string;
  lifecycle: string;
  fallbackKey: string | null;
  precedenceWeight: number;
  contexts: RuntimeContext[];
}

export interface Pick {
  record: RuntimeRecord;
  /** The candidate that led here, or 'root' when nothing did. */
  via: string;
}

const MAX_FALLBACK_STEPS = 32;

const slug = (biome: string) => biome.toLowerCase().replace(/_/g, '-');

function rootOf(records: RuntimeRecord[]): RuntimeRecord | undefined {
  return records.find(r => r.fallbackKey === null);
}

/**
 * Whether one declared context is satisfied by this candidate, given the whole chain the backend sent.
 *
 * `whenBiome` reads the rest of the chain rather than the candidate itself: the ground's own biome is always in the
 * chain, so "a lake margin, but only in forest" is answerable without the browser working anything out for itself.
 */
export function contextMatches(context: RuntimeContext, candidate: string, chain: readonly string[]): boolean {
  if ('candidate' in context) {
    if (context.candidate !== candidate) return false;
    if (!context.whenBiome) return true;
    const wanted = `biome.${slug(context.whenBiome)}`;
    return chain.some(c => c === wanted || c.startsWith(`${wanted}.`));
  }
  if (context.setting === 'edge-or-beside' && context.biomes?.length === 2) {
    const [a, b] = context.biomes.map(slug);
    return candidate === `biome.${a}.edge-${b}` || candidate === `biome.${b}.edge-${a}`
        || candidate === `biome.${a}.beside-${b}` || candidate === `biome.${b}.beside-${a}`;
  }
  return false;
}

/** Every record that answers to this candidate, most specific first, ties broken by key so two never toss a coin. */
export function recordsFor(candidate: string, records: RuntimeRecord[], chain: readonly string[]): RuntimeRecord[] {
  return records
    .filter(r => (r.contexts ?? []).some(c => contextMatches(c, candidate, chain)))
    .sort((x, y) => (y.precedenceWeight - x.precedenceWeight) || x.backdropKey.localeCompare(y.backdropKey));
}

/**
 * The first backdrop a place can actually be shown with. Each candidate is tried in order; a record that cannot be
 * shown — not ACTIVE (quarantined, pending review, retired), not in this build, or already failed to load — walks its
 * own fallback chain before the next record or candidate is tried. The registry's root is the last resort, and null
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
    for (const declared of recordsFor(candidate, records, candidates)) {
      const found = walk(declared);
      if (found) return { record: found, via: candidate };
    }
  }
  const root = rootOf(records);
  return root && servable(root) ? { record: root, via: 'root' } : null;
}
