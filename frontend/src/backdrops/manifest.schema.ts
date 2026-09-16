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
  'TEMPERATE_FOREST', 'WETLAND', 'GRASSLAND', 'HIGHLAND', 'MOUNTAIN', 'OCEAN', 'RIVER_BANK', 'COAST',
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

/**
 * Registry lifecycle. TOPOLOGY_GATED entries are kept but only surface once their gate exists. PENDING_REVIEW is
 * where every newly generated or replaced image starts (#241): it cannot become ACTIVE until its review is done.
 */
export const LIFECYCLE_STATES = ['ACTIVE', 'TOPOLOGY_GATED', 'DEPRECATED', 'PENDING_REVIEW', 'QUARANTINED'] as const;
export type LifecycleState = (typeof LIFECYCLE_STATES)[number];

/**
 * The neutral-backdrop review checklist (#241, docs/systems/backdrop-review-contract.md). Every item is a
 * reviewer's statement about the image, and an APPROVED review has all of them true.
 */
export const REVIEW_CHECKLIST = [
  'noLivingCreatures',            // no animal, person, monster or insect visible anywhere in the frame
  'onlyNonlivingHabitatEvidence', // tracks, dens, nests, bones, droppings are allowed; the creature itself is not
  'smoothModernRendering',        // painterly/photographic, not pixel art, not low-poly, no visible artefacts
  'noGridTilingOrPixelation',     // no repeating tiles, grids, seams or upscaled pixels
  'widescreenDimensions',         // ~16:9, at least WIDESCREEN.minWidth × WIDESCREEN.minHeight
  'uiSafeComposition',            // the defining geography survives the header, Body HUD, narration and composer
] as const;
export type ReviewChecklistItem = (typeof REVIEW_CHECKLIST)[number];

/**
 * APPROVED: reviewed against the full checklist. PENDING: not yet reviewed. LEGACY: ACTIVE before this contract
 * existed, on trust — allowed only for keys in scripts/backdrops/legacy-unreviewed.json, which may only shrink.
 */
export const REVIEW_STATES = ['PENDING', 'APPROVED', 'LEGACY', 'FLAGGED'] as const;
export type ReviewState = (typeof REVIEW_STATES)[number];

/**
 * What an audit found (#240). CREATURE and AMBIGUOUS block showing the image at all: the record must be QUARANTINED
 * until it is replaced and re-reviewed. RENDERING records an artefact that blocks approval but not display. NONE is a
 * pre-check that found nothing — it still needs a human to approve. Automated vision may flag; it never approves.
 */
export const FINDING_ISSUES = ['CREATURE', 'AMBIGUOUS', 'RENDERING', 'NONE'] as const;
export type FindingIssue = (typeof FINDING_ISSUES)[number];
export const FINDING_SOURCES = ['AUTOMATED_VISION', 'HUMAN'] as const;
export type FindingSource = (typeof FINDING_SOURCES)[number];

export interface ReviewFinding {
  issue: FindingIssue;
  notes: string;
  /** QUARANTINE, CREATOR_REVIEW, REPLACE or HUMAN_APPROVAL — what has to happen next. */
  disposition: 'QUARANTINE' | 'CREATOR_REVIEW' | 'REPLACE' | 'HUMAN_APPROVAL';
  source: FindingSource;
  recordedAt: string;
}

export interface BackdropReview {
  state: ReviewState;
  /** Audit findings against this image, oldest first. Replacement starts a fresh list. */
  findings: ReviewFinding[];
  checklist: Record<ReviewChecklistItem, boolean>;
  /** Who completed the review. Required for APPROVED. */
  reviewedBy: string | null;
  /** ISO date the review was completed. Required for APPROVED. */
  reviewedAt: string | null;
}

/** What an ACTIVE backdrop must measure to count as widescreen. */
export const WIDESCREEN = { minAspect: 1.75, maxAspect: 1.8, minWidth: 1600, minHeight: 900 } as const;

/** Time-of-day bands an image is genuinely tied to. Empty = eligible in every band. */
export const TIME_BANDS = ['DAWN', 'DAY', 'DUSK', 'NIGHT'] as const;
export type TimeBand = (typeof TIME_BANDS)[number];

/** An earlier image this record replaced. Replacement appends; it never overwrites (#241). */
export interface SupersededVersion {
  version: number;
  contentHash: string;
  retiredAt: string;
}

export interface BackdropProvenance {
  /** Generator that produced the asset (kept for attribution / regeneration). */
  generator: string;
  /** The classification family the inventory pass placed it in (audit trail). */
  family: SiteFamily;
  /** Every earlier image under this key, oldest first. A version above 1 must name the one before it. */
  supersedes: SupersededVersion[];
}

/**
 * What the world has to be for this image to be shown (#235). The backend hands the client a chain of candidate keys
 * (`site.clay-beds`, `biome.wetland.deep`, `interior.cave.dark`, `world.default`, and the location endpoint's
 * presentation keys), and a record declares which of them it answers to. Routing is by this declaration, never by
 * what the file happens to be called.
 *
 *  - `{ candidate }` — this exact key.
 *  - `{ candidate, whenBiome }` — this key, but only on that ground: a lake margin in forest is not a lake margin on
 *    open grassland, and they are different pictures.
 *  - `{ setting: 'edge-or-beside', biomes: [a, b] }` — the seam between two kinds of country, in either order.
 */
export type BackdropContext =
  | { candidate: string; whenBiome?: BaseBiome }
  | { setting: 'edge-or-beside'; biomes: [BaseBiome, BaseBiome] };

/** Why an image cannot be reached yet, and what would change that. A record has contexts or a gate, never neither. */
export interface BackdropGate {
  reason: string;
  /** The issue(s) whose delivery would give this image a context, e.g. "#232, #224". */
  activatedBy: string;
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
  /** The neutral-backdrop review (#241). ACTIVE requires APPROVED, or LEGACY for a frozen pre-contract key. */
  review: BackdropReview;
  /** The world contexts that select this image (#235). Empty only when `gate` says why. */
  contexts: BackdropContext[];
  /** Why this image is unreachable, or null when contexts reach it. */
  gate: BackdropGate | null;
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
