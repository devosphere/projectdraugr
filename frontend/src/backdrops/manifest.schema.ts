// Typed, versioned schema for the playthrough backdrop registry (EPIC #222, story #223).
//
// One canonical record describes every `src/assets/playthrough-*.png` file: which world
// context may show it, how specific that context is, what it falls back to, and the
// provenance/integrity metadata that lets CI prove the registry and the asset directory
// never drift apart. The schema deliberately does NOT encode encounter presence — a backdrop
// is a neutral, creature-free environment; living things are owned by the simulation, never by
// the scenery (issue #231: "Schema does not encode encounter presence").
//
// The manifest JSON (`backdrop-manifest.json`) is generated and checked by
// `scripts/backdrops/build-manifest.mjs`. Bump SCHEMA_VERSION only for a breaking shape change;
// the validator uses it to refuse a manifest it cannot understand and to drive cache invalidation.

export const SCHEMA_VERSION = 1 as const;

/** The canonical base biomes of the world seed (backend `BiomeClimate`). */
export const BASE_BIOMES = [
  'TEMPERATE_FOREST', 'WETLAND', 'GRASSLAND', 'HIGHLAND', 'MOUNTAIN', 'OCEAN', 'RIVER_BANK',
] as const;
export type BaseBiome = (typeof BASE_BIOMES)[number];

/**
 * How tightly the image is bound to a specific world feature. The resolver (#225) prefers the
 * most specific eligible backdrop, so proximity drives precedence.
 */
export const PROXIMITY_CLASSES = [
  'EXACT_SITE',       // the image IS this site (a den, a seam, a ruin interior) — show only when standing at it
  'ADJACENT_VISIBLE', // a feature visible from nearby (a cliff, a shoreline, a riverbank)
  'ECOTONE',          // a transition between two base biomes
  'REGIONAL',         // the surrounding base-biome landscape — the safe wide default
] as const;
export type ProximityClass = (typeof PROXIMITY_CLASSES)[number];

/** Classification family — the axis the coverage report groups by. */
export const SITE_FAMILIES = [
  'BASE_BIOME',         // the plain surrounding landscape for a biome
  'ECOTONE',            // biome-to-biome transition
  'FRESHWATER',         // river, stream, lake, pool, spring, marshy water feature
  'COAST',              // ocean, shore, beach, tidal, archipelago, dune
  'KARST',              // cave / limestone / underground water systems
  'GEOLOGICAL',         // rock, stone, mineral outcrops and stone-tool stock
  'RESOURCE_SITE',      // ore/metal/salt/pigment/clay extraction sites
  'RUIN',               // human-made ruins and abandoned structures
  'FLORA_SITE',         // edible/useful plant stands, groves, meadows, patches
  'FAUNA_RANGE',        // real-animal ranges, dens, colonies, nesting/spawning grounds
  'MONSTER_TERRITORY',  // fabular-creature territory (discovery-gated)
  'NATIVE_TERRITORY',   // native settlement / territory evidence
  'DOMESTICATION',      // paddocks, husbandry, tamed-animal grounds
] as const;
export type SiteFamily = (typeof SITE_FAMILIES)[number];

/** Registry lifecycle. TOPOLOGY_GATED entries are kept but only surface once their gate exists. */
export const LIFECYCLE_STATES = ['ACTIVE', 'TOPOLOGY_GATED', 'DEPRECATED'] as const;
export type LifecycleState = (typeof LIFECYCLE_STATES)[number];

/** Time-of-day bands an image is genuinely tied to. Empty = eligible in every band. */
export const TIME_BANDS = ['DAWN', 'DAY', 'DUSK', 'NIGHT'] as const;
export type TimeBand = (typeof TIME_BANDS)[number];

export interface BackdropProvenance {
  /** Generator that produced the asset (kept for attribution / regeneration). */
  generator: string;
  /** The classification family the inventory pass placed it in (audit trail). */
  family: SiteFamily;
}

export interface BackdropRecord {
  /** Stable identity, derived from the filename slug. Never reused for a different image. */
  backdropKey: string;
  filename: string;
  /** Asset revision parsed from the `-vN` filename suffix. */
  version: number;
  /** Human-reviewable display label surfaced in the world header. */
  label: string;
  lifecycle: LifecycleState;

  /** Base biome(s) this context can appear in. Ecotones list both; everything else lists one. */
  biomes: BaseBiome[];
  siteFamily: SiteFamily;
  /** Canonical world/ecology/geology/ruin context tokens, drawn from the slug vocabulary. */
  contextKeys: string[];
  proximity: ProximityClass;

  /** Time bands the image requires. Empty array = any time. */
  timeBands: TimeBand[];
  /** Seasons the image requires (canonical names). Empty = any season. */
  seasons: string[];
  /** Weather kinds the image requires (backend `weatherKind`). Empty = any weather. */
  weather: string[];

  /** May this context be shown before the player has explicitly discovered the feature? */
  showBeforeDiscovery: boolean;
  /** Neutral-environment guarantee: no visible living creature. Must be true for ACTIVE entries. */
  creatureFree: boolean;

  /** backdropKey to fall back to when this one is ineligible. null only on the single root. */
  fallbackKey: string | null;
  /** Higher wins when several backdrops are eligible. Tracks proximity specificity. */
  precedenceWeight: number;

  /** Integrity + presentation metadata proven against the file on disk by the validator. */
  contentHash: string; // sha256 hex of the PNG bytes
  width: number;
  height: number;
  aspectRatio: number; // width / height, rounded to 3 dp
  bytes: number;

  provenance: BackdropProvenance;
}

export interface BackdropManifest {
  schemaVersion: typeof SCHEMA_VERSION;
  /** Directory the records enumerate, relative to the repo root. */
  assetRoot: string;
  backdrops: BackdropRecord[];
}

/** Precedence weight for each proximity class — the resolver's specificity ladder. */
export const PROXIMITY_WEIGHT: Record<ProximityClass, number> = {
  EXACT_SITE: 40,
  ADJACENT_VISIBLE: 30,
  ECOTONE: 20,
  REGIONAL: 10,
};
