/**
 * #238 — load a backdrop by manifest key, lazily.
 *
 * The playthrough screen used to import four PNGs by hand and pick one from a seven-row table, so 147 of the
 * registry's images could never be shown. This loads whichever image the manifest and the backend's candidate chain
 * call for, and only when it is called for:
 *
 *   - Assets are discovered with a lazy `import.meta.glob`, so nothing is fetched or decoded until a scene wants it.
 *     A build only contains the images actually present in `src/assets`; every other key walks its fallback chain
 *     to one that is, ending at the registry root.
 *   - URLs are cached by filename and content hash, so a replaced image (new hash) is never served from the cache.
 *   - An image that fails to load or decode is rejected for the session and the next choice is taken, so a missing
 *     or corrupt file degrades to a valid neutral scene rather than a blank one.
 */
import { decodeArt } from './latestScene';
import { pickBackdrop, type RuntimeRecord } from './resolveBackdrop';

// The registry is loaded on first use, as its own chunk. Imported statically it rode in the main bundle — the whole
// manifest, review history and all, downloaded before the title screen — for a screen that needs it only once a
// scene is asked for.
let records: Promise<RuntimeRecord[]> | null = null;
function loadRecords(): Promise<RuntimeRecord[]> {
  if (!records) {
    records = import('./backdrop-manifest.json').then(module => (module.default as { backdrops: RuntimeRecord[] }).backdrops.map(b => ({
      backdropKey: b.backdropKey, filename: b.filename, contentHash: b.contentHash, label: b.label,
      lifecycle: b.lifecycle, fallbackKey: b.fallbackKey, precedenceWeight: b.precedenceWeight,
      contexts: b.contexts ?? [],
    })));
    records.catch(() => { records = null; }); // a failed fetch is retried next time rather than remembered
  }
  return records;
}

const ASSET_URLS = import.meta.glob('../assets/playthrough-*.png', { query: '?url', import: 'default' }) as
  Record<string, () => Promise<string>>;
const assetPath = (filename: string) => `../assets/${filename}`;
const available = (filename: string) => assetPath(filename) in ASSET_URLS;

/**
 * How many scenes the cache remembers (#243). A long journey passes through many places, and an unbounded cache
 * would hold a reference to every image the browser ever decoded. Least-recently-used is evicted; an evicted URL is
 * simply fetched again, and the browser's own HTTP cache usually answers that without touching the network.
 */
const MAX_CACHED_SCENES = 12;
const urlCache = new Map<string, Promise<string>>();
const rejected = new Set<string>();

function urlFor(record: RuntimeRecord): Promise<string> {
  const cacheKey = `${record.filename}#${record.contentHash}`;
  const cached = urlCache.get(cacheKey);
  if (cached) {
    urlCache.delete(cacheKey);       // re-insert so Map iteration order stays least-recently-used first
    urlCache.set(cacheKey, cached);
    return cached;
  }
  const url = ASSET_URLS[assetPath(record.filename)]();
  urlCache.set(cacheKey, url);
  while (urlCache.size > MAX_CACHED_SCENES) {
    const oldest = urlCache.keys().next();
    if (oldest.done) break;
    urlCache.delete(oldest.value);
  }
  return url;
}

export interface Scene {
  backdropKey: string;
  label: string;
  art: string;
}

/**
 * The scene a place is shown with, decoded and ready to paint, or null if not even the root can be shown. Tries each
 * choice in turn and rejects one that will not load, so the answer is always an image that actually painted.
 */
export async function loadScene(candidates: string[]): Promise<Scene | null> {
  const RECORDS = await loadRecords();
  for (let attempt = 0; attempt <= RECORDS.length; attempt++) {
    const pick = pickBackdrop(candidates, RECORDS, available, rejected);
    if (!pick) return null;
    try {
      const art = await urlFor(pick.record);
      await decodeArt(art);
      return { backdropKey: pick.record.backdropKey, label: pick.record.label, art };
    } catch {
      rejected.add(pick.record.filename);
      urlCache.delete(`${pick.record.filename}#${pick.record.contentHash}`);
    }
  }
  return null;
}

/** Warm the URLs of a few likely next scenes without decoding them. Bounded, so it can never fetch the catalogue. */
export async function preloadScenes(candidateLists: string[][], limit = 2): Promise<void> {
  const RECORDS = await loadRecords();
  for (const candidates of candidateLists.slice(0, limit)) {
    const pick = pickBackdrop(candidates, RECORDS, available, rejected);
    if (pick) urlFor(pick.record).catch(() => rejected.add(pick.record.filename));
  }
}
