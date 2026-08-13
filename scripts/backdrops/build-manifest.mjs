#!/usr/bin/env node
// Backdrop registry tool (EPIC #222, story #223 — tasks #230 inventory/classify, #231 validate).
//
//   node scripts/backdrops/build-manifest.mjs generate   # (re)write backdrop-manifest.json from the assets
//   node scripts/backdrops/build-manifest.mjs check       # validation gate — exits non-zero on any drift
//   node scripts/backdrops/build-manifest.mjs report      # human-reviewable coverage report, grouped by family
//
// No dependencies — Node built-ins only (fs / crypto / path), so it runs the same locally and in CI.
// The classification is deterministic: slug tokens drive a rules pass, and a small OVERRIDES table
// pins the handful of cases the rules cannot infer from tokens alone. The generated JSON is the
// source of truth the `check` gate enforces against the asset directory.

import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, relative } from 'node:path';

const SCHEMA_VERSION = 1;
const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const ASSET_DIR = join(REPO, 'frontend', 'src', 'assets');
const ASSET_ROOT_REL = relative(REPO, ASSET_DIR).replace(/\\/g, '/');
const MANIFEST_PATH = join(REPO, 'frontend', 'src', 'backdrops', 'backdrop-manifest.json');
const FILE_RE = /^playthrough-(.+)-v(\d+)\.png$/;

const BASE_BIOMES = ['TEMPERATE_FOREST', 'WETLAND', 'GRASSLAND', 'HIGHLAND', 'MOUNTAIN', 'OCEAN'];
const PROXIMITY_CLASSES = ['EXACT_SITE', 'ADJACENT_VISIBLE', 'ECOTONE', 'REGIONAL'];
const SITE_FAMILIES = ['BASE_BIOME', 'ECOTONE', 'FRESHWATER', 'COAST', 'KARST', 'GEOLOGICAL',
  'RESOURCE_SITE', 'RUIN', 'FLORA_SITE', 'FAUNA_RANGE', 'MONSTER_TERRITORY', 'NATIVE_TERRITORY', 'DOMESTICATION'];
const LIFECYCLE_STATES = ['ACTIVE', 'TOPOLOGY_GATED', 'DEPRECATED'];
const PROXIMITY_WEIGHT = { EXACT_SITE: 40, ADJACENT_VISIBLE: 30, ECOTONE: 20, REGIONAL: 10 };

// The base-biome anchor backdrop each biome falls back to. `forest` is the global root (fallback null).
const ANCHOR = {
  TEMPERATE_FOREST: 'forest',
  WETLAND: 'wetland',
  GRASSLAND: 'plains',
  HIGHLAND: 'highland',
  MOUNTAIN: 'mountain-crag',
  OCEAN: 'open-ocean',
};
const ROOT_KEY = 'forest';

// --- classification vocabulary ------------------------------------------------------------------

// Fabular creatures — their territories are discovery-gated MONSTER_TERRITORY, never shown blind.
const MONSTERS = ['warden', 'troll', 'centaur', 'hydra', 'siren', 'harpy', 'roc', 'wyvern',
  'deepwater-maw', 'maw', 'ash-hound', 'gloom-moth', 'glasswing', 'ridge-stalker', 'dusk-prowler',
  'thornback', 'reedkin'];

// Token -> base biome vote. Longer/again-specific tokens are checked before broad ones.
const BIOME_TOKENS = [
  // wetland
  [['bog', 'fen', 'mire', 'marsh', 'peat', 'reed', 'wetland', 'floodplain', 'swamp', 'carr',
    'amphibian', 'waterfowl', 'water-lily', 'bulrush', 'lily', 'siren', 'hydra', 'firefly-marsh',
    'crocodilian', 'catfish', 'eel', 'salt-marsh'], 'WETLAND'],
  // ocean / coast
  [['ocean', 'coast', 'coastal', 'shore', 'shoreline', 'archipelago', 'beach', 'seabird',
    'tidal', 'estuary', 'shell-beach', 'island-colony'], 'OCEAN'],
  // mountain
  [['mountain', 'crag', 'talus', 'scree', 'snowline', 'volcanic', 'basalt', 'pumice', 'obsidian',
    'wolverine', 'wyvern', 'roc'], 'MOUNTAIN'],
  // highland
  [['highland', 'tarn', 'heath', 'bilberry', 'juniper', 'alpine', 'reindeer', 'harpy', 'raptor-cliff',
    'ridge', 'eyrie', 'foothill'], 'HIGHLAND'],
  // grassland
  [['grassland', 'plains', 'plain', 'meadow', 'scrub', 'steppe', 'savanna', 'herd', 'aurochs',
    'locust', 'grain', 'seed-grass', 'fibre-grassland', 'jackal', 'gopher', 'warren', 'buffalo',
    'migration-corridor', 'cricket'], 'GRASSLAND'],
  // forest (default-ish, but token-driven so it competes fairly)
  [['forest', 'woodland', 'grove', 'canopy', 'timber', 'old-growth', 'mast', 'nut', 'mushroom',
    'blackberry', 'thorn-brush', 'earthworm', 'badger', 'fox-earth', 'bear-den', 'boar', 'spider-den',
    'ant-mound', 'squirrel', 'owl-woodland', 'silk-moth', 'raven', 'shelter-grove', 'wooded'], 'TEMPERATE_FOREST'],
];

// Freshwater tokens can appear in any biome; they set the FAMILY, not the biome.
const FRESHWATER_TOKENS = ['river', 'stream', 'lake', 'lakebank', 'lakeshore', 'pool', 'pond',
  'spring', 'waterway', 'ford', 'floodplain', 'shallows', 'fishery', 'spawning', 'trout', 'pike',
  'crayfish', 'turtle', 'otter', 'beaver', 'undercut-riverbank', 'riverbank', 'headwater', 'stillwater',
  'deep-slow-river', 'tarn'];
const RESOURCE_TOKENS = ['seam', 'vein', 'exposure', 'deposit', 'ore', 'pyrite', 'salt', 'gold',
  'tin', 'copper', 'silver', 'lead', 'sulphur', 'ochre', 'pigment', 'iron', 'placer', 'bog-iron'];
const GEOLOGY_TOKENS = ['obsidian', 'flint', 'chert', 'quartz', 'clay', 'soapstone', 'limestone',
  'lime', 'sandstone', 'pumice', 'basalt', 'cobble', 'hammerstone', 'refractory-clay', 'precision-tool-stone',
  'abrasive', 'crystal'];
const RUIN_TOKENS = ['aqueduct', 'causeway', 'granary', 'observatory', 'watchtower', 'archive',
  'shrine', 'city', 'ruin', 'fallen-city', 'sunken', 'collapsed', 'broken', 'buried', 'overgrown', 'flooded'];
const KARST_TOKENS = ['karst', 'cave', 'underground-karst', 'bat-cave'];
const FLORA_TOKENS = ['grove', 'brush', 'patch', 'stand', 'heath', 'herb', 'fibre', 'grain', 'mast',
  'nut', 'mushroom', 'lily', 'reed-root', 'root-patch', 'edible-root', 'bilberry', 'juniper', 'blackberry',
  'wild', 'flora', 'carr', 'timber'];
const FAUNA_TOKENS = ['den', 'lair', 'sett', 'burrow', 'colony', 'nest', 'roost', 'range', 'territory',
  'warren', 'wallow', 'mound', 'hive', 'earth', 'ground', 'corridor', 'basking', 'nursery',
  'spawning', 'nesting', 'eyrie', 'pack', 'herd'];
const NATIVE_TOKENS = ['native', 'settlement', 'camp', 'isle-settlement'];
const DOMESTICATION_TOKENS = ['domestication', 'paddock'];
const ECOTONE_TOKENS = ['ecotone', 'foothill', 'edge', 'snowline'];

// Broad landscapes that are the safe wide default for their biome (REGIONAL, shown before discovery).
const REGIONAL_SLUGS = new Set(['forest', 'dense-forest', 'wetland', 'plains', 'highland', 'coast',
  'open-ocean', 'mountain-crag', 'dry-scrub', 'stream', 'estuary', 'tidal-flat', 'salt-marsh',
  'deep-peat-bog', 'river-floodplain', 'fibre-grassland', 'wild-fibre-meadow']);

// Overrides for slugs whose correct classification the token rules cannot infer. Each is a partial
// record merged over the rules output. Kept small and explicit so every choice is reviewable.
const OVERRIDES = {
  // freshwater features that read as forest/grassland by token but are water-first sites
  'woodland-spring': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FRESHWATER', proximity: 'EXACT_SITE' },
  'forest-lakebank': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FRESHWATER', proximity: 'ADJACENT_VISIBLE' },
  'open-lakeshore': { biomes: ['GRASSLAND'], siteFamily: 'FRESHWATER', proximity: 'ADJACENT_VISIBLE' },
  'wooded-riverbank': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FRESHWATER', proximity: 'ADJACENT_VISIBLE' },
  'marsh-island': { biomes: ['WETLAND'], siteFamily: 'FRESHWATER', proximity: 'EXACT_SITE' },
  // karst / caves
  'karst-cave': { biomes: ['MOUNTAIN'], siteFamily: 'KARST', proximity: 'EXACT_SITE' },
  'karst-cave-interior': { biomes: ['MOUNTAIN'], siteFamily: 'KARST', proximity: 'EXACT_SITE', showBeforeDiscovery: false },
  'underground-karst-stream': { biomes: ['MOUNTAIN'], siteFamily: 'KARST', proximity: 'EXACT_SITE', showBeforeDiscovery: false },
  'bat-cave-insect-roost': { biomes: ['MOUNTAIN'], siteFamily: 'KARST', proximity: 'EXACT_SITE', showBeforeDiscovery: false },
  'mushroom-hollow': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FLORA_SITE', proximity: 'EXACT_SITE' },
  // coast / ocean specifics
  'coastal-cliff-dune': { biomes: ['OCEAN'], siteFamily: 'COAST', proximity: 'ADJACENT_VISIBLE' },
  'grassland-coastal-dune': { biomes: ['GRASSLAND', 'OCEAN'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'seabird-island-colony': { biomes: ['OCEAN'], siteFamily: 'FAUNA_RANGE', proximity: 'EXACT_SITE' },
  'shell-beach': { biomes: ['OCEAN'], siteFamily: 'COAST', proximity: 'ADJACENT_VISIBLE' },
  'estuary': { biomes: ['OCEAN', 'WETLAND'], siteFamily: 'COAST', proximity: 'ECOTONE' },
  'tidal-flat': { biomes: ['OCEAN'], siteFamily: 'COAST', proximity: 'ADJACENT_VISIBLE' },
  'archipelago-shoreline': { biomes: ['OCEAN'], siteFamily: 'COAST', proximity: 'ADJACENT_VISIBLE' },
  // geology/resource sites that also carry a biome token
  'bog-iron-iron-sand-bar': { biomes: ['WETLAND'], siteFamily: 'RESOURCE_SITE', proximity: 'EXACT_SITE' },
  'obsidian-field': { biomes: ['MOUNTAIN'], siteFamily: 'GEOLOGICAL', proximity: 'EXACT_SITE' },
  'pumice-basalt-volcanic-scree': { biomes: ['MOUNTAIN'], siteFamily: 'GEOLOGICAL', proximity: 'EXACT_SITE' },
  'clay-deposit': { biomes: ['WETLAND'], siteFamily: 'RESOURCE_SITE', proximity: 'EXACT_SITE' },
  'refractory-clay-bed': { biomes: ['WETLAND'], siteFamily: 'RESOURCE_SITE', proximity: 'EXACT_SITE' },
  'ochre-pigment-earth-bank': { biomes: ['GRASSLAND'], siteFamily: 'RESOURCE_SITE', proximity: 'EXACT_SITE' },
  'rounded-hammerstone-cobble-bar': { biomes: ['GRASSLAND'], siteFamily: 'GEOLOGICAL', proximity: 'EXACT_SITE' },
  'quarry': { biomes: ['MOUNTAIN'], siteFamily: 'GEOLOGICAL', proximity: 'EXACT_SITE' },
  // ecotones
  'forest-grassland-ecotone': { biomes: ['TEMPERATE_FOREST', 'GRASSLAND'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'forest-wetland-ecotone': { biomes: ['TEMPERATE_FOREST', 'WETLAND'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'grassland-wetland-ecotone': { biomes: ['GRASSLAND', 'WETLAND'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'forest-highland-foothill': { biomes: ['TEMPERATE_FOREST', 'HIGHLAND'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'plains-highland-foothill': { biomes: ['GRASSLAND', 'HIGHLAND'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'highland-mountain-snowline': { biomes: ['HIGHLAND', 'MOUNTAIN'], siteFamily: 'ECOTONE', proximity: 'ECOTONE' },
  'herd-migration-corridor': { biomes: ['GRASSLAND'], siteFamily: 'FAUNA_RANGE', proximity: 'REGIONAL' },
  // ruins
  'native-settlement-edge': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'NATIVE_TERRITORY', proximity: 'ADJACENT_VISIBLE' },
  'reedkin-river-isle-settlement': { biomes: ['WETLAND'], siteFamily: 'NATIVE_TERRITORY', proximity: 'EXACT_SITE', showBeforeDiscovery: false },
  'domestication-paddock': { biomes: ['GRASSLAND'], siteFamily: 'DOMESTICATION', proximity: 'EXACT_SITE' },
  // grassland fauna that reads as forest by a shared token
  'deer-range': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FAUNA_RANGE', proximity: 'REGIONAL' },
  'elk-range': { biomes: ['HIGHLAND'], siteFamily: 'FAUNA_RANGE', proximity: 'REGIONAL' },
  'reptile-basking-heath': { biomes: ['HIGHLAND'], siteFamily: 'FAUNA_RANGE', proximity: 'ADJACENT_VISIBLE' },
  'nocturnal-firefly-marsh': { biomes: ['WETLAND'], siteFamily: 'FAUNA_RANGE', proximity: 'EXACT_SITE', timeBands: ['NIGHT'] },
  'nocturnal-owl-woodland': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FAUNA_RANGE', proximity: 'REGIONAL', timeBands: ['NIGHT'] },
  'cricket-night-meadow': { biomes: ['GRASSLAND'], siteFamily: 'FAUNA_RANGE', proximity: 'REGIONAL', timeBands: ['NIGHT'] },
  'gloom-moth-colony': { timeBands: ['NIGHT'] },
  'wild-honeybee-tree-hive': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FAUNA_RANGE', proximity: 'EXACT_SITE' },
  // single-biome fauna edges that the 'edge' token would mis-read as ecotones
  'forest-rat-groundbird-edge': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FAUNA_RANGE', proximity: 'ADJACENT_VISIBLE' },
  // caves are mountain karst; the bear/troll ones are shelters/territories in that rock
  'cave-bear-shelter': { biomes: ['MOUNTAIN'], siteFamily: 'KARST', proximity: 'EXACT_SITE', showBeforeDiscovery: false },
  'cave-mouth-lair': { biomes: ['MOUNTAIN'], siteFamily: 'KARST', proximity: 'EXACT_SITE', showBeforeDiscovery: false },
  // stone-tool stock reads as a resource by 'exposure'; it is a geological outcrop
  'precision-tool-stone-exposure': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'GEOLOGICAL', proximity: 'EXACT_SITE' },
  'rock-salt-exposure': { biomes: ['MOUNTAIN'], siteFamily: 'RESOURCE_SITE', proximity: 'EXACT_SITE' },
  // salt-marsh is a wetland landscape, not a salt-extraction site
  'salt-marsh': { biomes: ['WETLAND'], siteFamily: 'BASE_BIOME', proximity: 'REGIONAL' },
  // vegetation communities read as flora sites
  'bog-medicinal-toxic-flora': { biomes: ['WETLAND'], siteFamily: 'FLORA_SITE', proximity: 'EXACT_SITE' },
  'wetland-willow-carr': { biomes: ['WETLAND'], siteFamily: 'FLORA_SITE', proximity: 'REGIONAL' },
  'highland-tarn': { biomes: ['HIGHLAND'], siteFamily: 'FRESHWATER', proximity: 'ADJACENT_VISIBLE' },
  'alpine-headwater': { biomes: ['HIGHLAND'], siteFamily: 'FRESHWATER', proximity: 'ADJACENT_VISIBLE' },
  'old-growth-timber': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FLORA_SITE', proximity: 'REGIONAL' },
  // biome corrections the tokens can't infer
  'deepwater-maw-territory': { biomes: ['OCEAN'] },
  'arctic-fox-snowfield-den': { biomes: ['HIGHLAND'] },
  'centaur-territory-camp': { biomes: ['GRASSLAND'] },
  'flooded-archive': { biomes: ['WETLAND'] },
  'sunken-shrine': { biomes: ['WETLAND'] },
  'earthworm-rich-forest-soil': { biomes: ['TEMPERATE_FOREST'], siteFamily: 'FAUNA_RANGE', proximity: 'EXACT_SITE' },
};

// --- helpers ------------------------------------------------------------------------------------

function titleCase(slug) {
  return slug.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

function hasAny(slug, tokens) {
  // Boundary-safe: match a token only as a whole hyphen-delimited word (or run of words),
  // never as a substring — otherwise "forest" matches "ore", "scavenging" matches "cave",
  // "nesting" matches "tin", "golden" matches "gold" (the intent-classifier substring trap).
  const s = `-${slug}-`;
  return tokens.some(t => s.includes(`-${t}-`));
}

function pngDimensions(buf) {
  // PNG: 8-byte signature, then IHDR chunk (4 len + 'IHDR' + width BE + height BE).
  const sigOk = buf.length > 24 && buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47;
  if (!sigOk || buf.toString('ascii', 12, 16) !== 'IHDR') return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

function classify(slug) {
  // biome: token votes, tie-break by BIOME_TOKENS order (forest last = weakest default)
  let biome = null;
  for (const [tokens, b] of BIOME_TOKENS) { if (hasAny(slug, tokens)) { biome = b; break; } }
  if (!biome) biome = 'TEMPERATE_FOREST';

  // family: most-specific structural read first
  let family;
  if (hasAny(slug, ECOTONE_TOKENS)) family = 'ECOTONE';
  else if (hasAny(slug, RUIN_TOKENS)) family = 'RUIN';
  else if (hasAny(slug, KARST_TOKENS)) family = 'KARST';
  else if (hasAny(slug, NATIVE_TOKENS)) family = 'NATIVE_TERRITORY';
  else if (hasAny(slug, DOMESTICATION_TOKENS)) family = 'DOMESTICATION';
  else if (hasAny(slug, MONSTERS)) family = 'MONSTER_TERRITORY';
  else if (hasAny(slug, RESOURCE_TOKENS)) family = 'RESOURCE_SITE';
  else if (hasAny(slug, GEOLOGY_TOKENS)) family = 'GEOLOGICAL';
  else if (hasAny(slug, FRESHWATER_TOKENS)) family = 'FRESHWATER';
  else if (hasAny(slug, FAUNA_TOKENS)) family = 'FAUNA_RANGE';
  else if (hasAny(slug, FLORA_TOKENS)) family = 'FLORA_SITE';
  else family = 'BASE_BIOME';

  const isMonster = hasAny(slug, MONSTERS);
  if (isMonster) family = 'MONSTER_TERRITORY';

  // proximity
  let proximity;
  if (family === 'ECOTONE') proximity = 'ECOTONE';
  else if (REGIONAL_SLUGS.has(slug) || family === 'BASE_BIOME') proximity = 'REGIONAL';
  else if (['RESOURCE_SITE', 'GEOLOGICAL', 'RUIN', 'KARST', 'MONSTER_TERRITORY', 'NATIVE_TERRITORY',
    'DOMESTICATION'].includes(family)) proximity = 'EXACT_SITE';
  else if (hasAny(slug, ['den', 'lair', 'sett', 'burrow', 'nest', 'roost', 'mound', 'hive', 'nursery',
    'eyrie', 'colony', 'wallow', 'hollow'])) proximity = 'EXACT_SITE';
  else if (hasAny(slug, ['cliff', 'shoreline', 'shore', 'bank', 'dune', 'edge', 'ford', 'lakebank',
    'lakeshore', 'riverbank', 'shallows'])) proximity = 'ADJACENT_VISIBLE';
  else proximity = 'REGIONAL';

  // discovery-gating: exact sites, ruins, monster/native territory, and interiors are not shown blind
  const showBeforeDiscovery = !(family === 'MONSTER_TERRITORY' || family === 'RUIN'
    || family === 'NATIVE_TERRITORY' || slug.includes('interior') || slug.includes('lair'));

  return { biomes: [biome], siteFamily: family, proximity, showBeforeDiscovery };
}

function contextKeysFor(slug, family) {
  // canonical-ish context tokens: the slug tokens minus pure filler, plus the family.
  const drop = new Set(['the', 'of', 'and', 'v1']);
  const toks = slug.split('-').filter(t => t && !drop.has(t));
  return Array.from(new Set([family.toLowerCase(), ...toks]));
}

function buildRecords() {
  const files = readdirSync(ASSET_DIR).filter(f => FILE_RE.test(f)).sort();
  return files.map(filename => {
    const [, slug, ver] = filename.match(FILE_RE);
    const buf = readFileSync(join(ASSET_DIR, filename));
    const dim = pngDimensions(buf);
    if (!dim) throw new Error(`Not a readable PNG: ${filename}`);
    const base = classify(slug);
    const ov = OVERRIDES[slug] || {};
    const merged = { ...base, ...ov };
    const biomes = merged.biomes;
    const family = merged.siteFamily;
    const proximity = merged.proximity;
    const primaryBiome = biomes[0];
    let fallbackKey = ANCHOR[primaryBiome];
    if (slug === fallbackKey) fallbackKey = slug === ROOT_KEY ? null : ROOT_KEY; // anchors chain to root
    return {
      backdropKey: slug,
      filename,
      version: Number(ver),
      label: titleCase(slug),
      lifecycle: 'ACTIVE',
      biomes,
      siteFamily: family,
      contextKeys: contextKeysFor(slug, family),
      proximity,
      timeBands: merged.timeBands || [],
      seasons: merged.seasons || [],
      weather: merged.weather || [],
      showBeforeDiscovery: merged.showBeforeDiscovery !== undefined ? merged.showBeforeDiscovery : base.showBeforeDiscovery,
      creatureFree: true,
      fallbackKey,
      precedenceWeight: PROXIMITY_WEIGHT[proximity],
      contentHash: createHash('sha256').update(buf).digest('hex'),
      width: dim.width,
      height: dim.height,
      aspectRatio: Math.round((dim.width / dim.height) * 1000) / 1000,
      bytes: buf.length,
      provenance: { generator: 'playthrough-backdrop-gen', family },
    };
  });
}

function generate() {
  const backdrops = buildRecords();
  const manifest = { schemaVersion: SCHEMA_VERSION, assetRoot: ASSET_ROOT_REL, backdrops };
  writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2) + '\n');
  console.log(`Wrote ${backdrops.length} records -> ${relative(REPO, MANIFEST_PATH).replace(/\\/g, '/')}`);
}

function loadManifest() {
  return JSON.parse(readFileSync(MANIFEST_PATH, 'utf8'));
}

// Pure structural validation — everything provable from the manifest plus the set of filenames on
// disk, without reading image bytes. Exported so the negative-test harness can exercise it directly.
export function validateManifest(manifest, onDiskFilenames) {
  const errors = [];
  if (manifest.schemaVersion !== SCHEMA_VERSION) errors.push(`schemaVersion ${manifest.schemaVersion} != ${SCHEMA_VERSION}`);

  const onDisk = [...onDiskFilenames].sort();
  const inManifest = manifest.backdrops.map(b => b.filename).sort();

  // directory <-> manifest bijection
  const diskSet = new Set(onDisk), manSet = new Set(inManifest);
  for (const f of onDisk) if (!manSet.has(f)) errors.push(`asset on disk not registered: ${f}`);
  for (const f of inManifest) if (!diskSet.has(f)) errors.push(`manifest entry has no file: ${f}`);

  // uniqueness
  const seenKey = new Map(), seenFile = new Map(), seenHash = new Map();
  for (const b of manifest.backdrops) {
    if (seenKey.has(b.backdropKey)) errors.push(`duplicate backdropKey: ${b.backdropKey}`);
    if (seenFile.has(b.filename)) errors.push(`duplicate filename: ${b.filename}`);
    seenKey.set(b.backdropKey, b); seenFile.set(b.filename, b);
    if (seenHash.has(b.contentHash)) errors.push(`duplicate content hash: ${b.filename} == ${seenHash.get(b.contentHash)} (identical image)`);
    else seenHash.set(b.contentHash, b.filename);
  }

  const keys = new Set(manifest.backdrops.map(b => b.backdropKey));
  for (const b of manifest.backdrops) {
    const at = `[${b.backdropKey}]`;
    // enum integrity
    if (!LIFECYCLE_STATES.includes(b.lifecycle)) errors.push(`${at} invalid lifecycle: ${b.lifecycle}`);
    if (!SITE_FAMILIES.includes(b.siteFamily)) errors.push(`${at} invalid siteFamily: ${b.siteFamily}`);
    if (!PROXIMITY_CLASSES.includes(b.proximity)) errors.push(`${at} invalid proximity: ${b.proximity}`);
    if (!Array.isArray(b.biomes) || b.biomes.length === 0) errors.push(`${at} must declare >=1 biome`);
    for (const bi of (b.biomes || [])) if (!BASE_BIOMES.includes(bi)) errors.push(`${at} unknown biome: ${bi}`);
    // ecotone must name exactly two biomes
    if (b.siteFamily === 'ECOTONE' && (b.biomes || []).length !== 2) errors.push(`${at} ECOTONE must list exactly two biomes`);
    // creature-free guarantee for anything ACTIVE
    if (b.lifecycle === 'ACTIVE' && b.creatureFree !== true) errors.push(`${at} ACTIVE entry is not creatureFree`);
    // schema must NOT encode encounter presence
    if ('encounter' in b || 'creature' in b || 'animalPresent' in b) errors.push(`${at} encodes encounter presence (forbidden)`);
    // precedence must track proximity
    if (b.precedenceWeight !== PROXIMITY_WEIGHT[b.proximity]) errors.push(`${at} precedenceWeight ${b.precedenceWeight} != ${PROXIMITY_WEIGHT[b.proximity]} for ${b.proximity}`);
    // integrity metadata present and sane
    if (!/^[0-9a-f]{64}$/.test(b.contentHash || '')) errors.push(`${at} bad contentHash`);
    if (!(b.width > 0 && b.height > 0 && b.bytes > 0)) errors.push(`${at} non-positive dimensions/size`);
    // fallback integrity
    if (b.fallbackKey !== null && !keys.has(b.fallbackKey)) errors.push(`${at} fallbackKey points nowhere: ${b.fallbackKey}`);
    if (b.fallbackKey === b.backdropKey) errors.push(`${at} is its own fallback`);
  }

  // exactly one root (null fallback), and every chain terminates at it with no cycle
  const roots = manifest.backdrops.filter(b => b.fallbackKey === null).map(b => b.backdropKey);
  if (roots.length !== 1) errors.push(`expected exactly one root (null fallback), found ${roots.length}: ${roots.join(', ')}`);
  const byKey = new Map(manifest.backdrops.map(b => [b.backdropKey, b]));
  for (const b of manifest.backdrops) {
    const seen = new Set(); let cur = b;
    while (cur && cur.fallbackKey !== null) {
      if (seen.has(cur.backdropKey)) { errors.push(`[${b.backdropKey}] circular fallback chain`); break; }
      seen.add(cur.backdropKey); cur = byKey.get(cur.fallbackKey);
      if (!cur) break;
    }
  }

  return errors;
}

function check() {
  const manifest = loadManifest();
  const onDisk = readdirSync(ASSET_DIR).filter(f => FILE_RE.test(f));
  const errors = validateManifest(manifest, onDisk);

  // re-verify each file's hash/dimensions against disk (proves no silent asset edit)
  const diskSet = new Set(onDisk);
  for (const b of manifest.backdrops) {
    if (!diskSet.has(b.filename)) continue;
    const buf = readFileSync(join(ASSET_DIR, b.filename));
    const hash = createHash('sha256').update(buf).digest('hex');
    if (hash !== b.contentHash) errors.push(`[${b.backdropKey}] content hash drift — image changed without re-generating the manifest`);
    const dim = pngDimensions(buf);
    if (!dim) errors.push(`[${b.backdropKey}] file is not a readable PNG`);
    else if (dim.width !== b.width || dim.height !== b.height) errors.push(`[${b.backdropKey}] dimension drift ${dim.width}x${dim.height} != ${b.width}x${b.height}`);
  }

  if (errors.length) {
    console.error(`✗ backdrop manifest check FAILED — ${errors.length} problem(s):`);
    for (const e of errors) console.error(`  - ${e}`);
    process.exit(1);
  }
  console.log(`✓ backdrop manifest OK — ${manifest.backdrops.length} records, directory bijection, unique keys/hashes, fallbacks terminate, hashes match disk.`);
}

function report() {
  const manifest = loadManifest();
  const byFamily = new Map();
  for (const b of manifest.backdrops) {
    if (!byFamily.has(b.siteFamily)) byFamily.set(b.siteFamily, []);
    byFamily.get(b.siteFamily).push(b);
  }
  const lines = [`# Backdrop coverage report`, ``,
    `Schema v${manifest.schemaVersion} · ${manifest.backdrops.length} backdrops · source \`${manifest.assetRoot}\``, ``];
  const biomeCount = {};
  for (const b of manifest.backdrops) for (const bi of b.biomes) biomeCount[bi] = (biomeCount[bi] || 0) + 1;
  lines.push(`## By base biome`, ``, ...BASE_BIOMES.map(bi => `- **${bi}**: ${biomeCount[bi] || 0}`), ``);
  lines.push(`## By family`, ``);
  for (const fam of SITE_FAMILIES) {
    const items = (byFamily.get(fam) || []).sort((a, b) => a.backdropKey.localeCompare(b.backdropKey));
    if (!items.length) continue;
    lines.push(`### ${fam} (${items.length})`, ``);
    for (const b of items) {
      lines.push(`- \`${b.backdropKey}\` — ${b.biomes.join('+')} · ${b.proximity}` +
        `${b.showBeforeDiscovery ? '' : ' · discovery-gated'}${b.timeBands.length ? ' · ' + b.timeBands.join('/') : ''}`);
    }
    lines.push(``);
  }
  const out = join(REPO, 'docs', 'systems', 'backdrop-coverage.md');
  writeFileSync(out, lines.join('\n'));
  console.log(lines.join('\n'));
  console.log(`\n(written to ${relative(REPO, out).replace(/\\/g, '/')})`);
}

// Run the CLI only when executed directly, not when imported by the test harness.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const cmd = process.argv[2] || 'check';
  if (cmd === 'generate') generate();
  else if (cmd === 'check') check();
  else if (cmd === 'report') report();
  else { console.error(`unknown command: ${cmd} (use generate | check | report)`); process.exit(2); }
}
