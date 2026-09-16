#!/usr/bin/env node
// Backdrop budget gate (EPIC #222, story #229 — task #243).
//
//   npm run build && npm run backdrops:budget
//
// Reads the BUILT artifact rather than the source, because the question it answers is what a player actually
// downloads before anything appears. Vite's build manifest names each entry's static imports, dynamic imports and
// assets, so "this image ships with the app" and "this image is fetched when a scene asks for it" are different
// facts here, not a matter of reading the loader and hoping.
//
// Node built-ins only, so it runs the same locally and in CI.

import { readFileSync, existsSync, statSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const DIST = join(REPO, 'frontend', 'dist');
const VITE_MANIFEST = join(DIST, '.vite', 'manifest.json');
// An alternative budgets file may be given, which is how the gate's own failure is proved rather than assumed.
const BUDGETS_PATH = process.argv[2] ? process.argv[2] : join(HERE, 'budgets.json');
const BUDGETS = JSON.parse(readFileSync(BUDGETS_PATH, 'utf8'));
const REGISTRY = JSON.parse(readFileSync(join(REPO, 'frontend', 'src', 'backdrops', 'backdrop-manifest.json'), 'utf8'));

const BACKDROP_IMAGE = /playthrough-.*\.(png|jpe?g|webp|avif)$/i;
const kb = bytes => (bytes / 1024).toFixed(1) + ' kB';

function sizes(file) {
  const path = join(DIST, file);
  if (!existsSync(path)) return { bytes: 0, gzip: 0, missing: true };
  const buffer = readFileSync(path);
  return { bytes: statSync(path).size, gzip: gzipSync(buffer).length, missing: false };
}

/** Everything the browser must have before the app can paint: the entry, its CSS, and all it statically imports. */
function initialPayload(manifest) {
  const entries = Object.values(manifest).filter(chunk => chunk.isEntry);
  const files = new Set();
  const walk = (chunk, seen = new Set()) => {
    if (!chunk || seen.has(chunk.file)) return;
    seen.add(chunk.file);
    files.add(chunk.file);
    for (const css of chunk.css || []) files.add(css);
    for (const asset of chunk.assets || []) files.add(asset);
    for (const key of chunk.imports || []) walk(manifest[key], seen);   // static imports only — never dynamicImports
  };
  for (const entry of entries) walk(entry);
  return [...files];
}

/** Every chunk reachable only through a dynamic import — fetched when something asks for it, not before. */
function lazyFiles(manifest) {
  const files = new Set();
  for (const chunk of Object.values(manifest)) {
    for (const key of chunk.dynamicImports || []) {
      const target = manifest[key];
      if (target) files.add(target.file);
    }
  }
  return [...files];
}

function main() {
  if (!existsSync(VITE_MANIFEST)) {
    console.error(`✗ no build to measure at ${VITE_MANIFEST}\n  run "npm run build" in frontend/ first.`);
    process.exit(2);
  }
  const manifest = JSON.parse(readFileSync(VITE_MANIFEST, 'utf8'));
  const failures = [];
  const notes = [];

  // 1. The initial payload, code and images budgeted apart: gzip is a fact about script, not about a PNG.
  const initial = initialPayload(manifest);
  const IMAGE = /\.(png|jpe?g|webp|avif|gif|svg)$/i;
  const codeFiles = initial.filter(file => !IMAGE.test(file));
  const imageFiles = initial.filter(file => IMAGE.test(file));

  const code = codeFiles.reduce((sum, file) => {
    const s = sizes(file);
    return { bytes: sum.bytes + s.bytes, gzip: sum.gzip + s.gzip };
  }, { bytes: 0, gzip: 0 });
  notes.push(`initial code: ${codeFiles.length} file(s), ${kb(code.bytes)} (${kb(code.gzip)} gzip)`);
  if (code.bytes > BUDGETS.initialCode.maxBytes) {
    failures.push(`initial code ${kb(code.bytes)} over budget ${kb(BUDGETS.initialCode.maxBytes)}`);
  }
  if (code.gzip > BUDGETS.initialCode.maxGzipBytes) {
    failures.push(`initial code ${kb(code.gzip)} gzip over budget ${kb(BUDGETS.initialCode.maxGzipBytes)} gzip`);
  }

  const imageBytes = imageFiles.reduce((sum, file) => sum + sizes(file).bytes, 0);
  notes.push(`initial images: ${imageFiles.length} (${kb(imageBytes)})`
    + (imageFiles.length ? ` — ${imageFiles.map(f => `${f} ${kb(sizes(f).bytes)}`).join(', ')}` : ''));
  if (imageFiles.length > BUDGETS.initialImages.maxCount) {
    failures.push(`${imageFiles.length} image(s) in the initial payload (budget ${BUDGETS.initialImages.maxCount}): ${imageFiles.join(', ')}`);
  }
  if (imageBytes > BUDGETS.initialImages.maxBytes) {
    failures.push(`initial images ${kb(imageBytes)} over budget ${kb(BUDGETS.initialImages.maxBytes)}`);
  }
  const eagerImages = imageFiles.filter(file => BACKDROP_IMAGE.test(file));
  if (eagerImages.length > BUDGETS.initialImages.maxBackdropImages) {
    failures.push(`${eagerImages.length} backdrop image(s) ship in the initial payload (budget ${BUDGETS.initialImages.maxBackdropImages}): ${eagerImages.join(', ')}`);
  }

  // 2. The registry must be fetched when a scene asks for it, never bundled with the app.
  const lazy = lazyFiles(manifest);
  const registryInInitial = initial.filter(file => /backdrop-manifest.*\.js$/.test(file));
  const registryLazy = lazy.filter(file => /backdrop-manifest.*\.js$/.test(file));
  if (BUDGETS.registryChunk.mustBeLazy) {
    if (registryInInitial.length) failures.push(`the backdrop registry ships in the initial payload: ${registryInInitial.join(', ')}`);
    else if (!registryLazy.length) failures.push('the backdrop registry is in no dynamic import — it must be fetched on first use');
  }
  for (const file of registryLazy) {
    const s = sizes(file);
    notes.push(`registry chunk ${file}: ${kb(s.bytes)} (${kb(s.gzip)} gzip), fetched on first scene`);
    if (s.gzip > BUDGETS.registryChunk.maxGzipBytes) {
      failures.push(`registry chunk ${kb(s.gzip)} gzip over budget ${kb(BUDGETS.registryChunk.maxGzipBytes)} gzip`);
    }
  }

  // 3. Lazily fetched images are still images a player waits for: cap each one, and the catalogue's size.
  const oversize = REGISTRY.backdrops.filter(b => b.bytes > BUDGETS.images.maxBytesEach);
  if (oversize.length) {
    failures.push(`${oversize.length} image(s) over ${kb(BUDGETS.images.maxBytesEach)} each: `
      + oversize.slice(0, 5).map(b => `${b.backdropKey} ${kb(b.bytes)}`).join(', '));
  }
  if (REGISTRY.backdrops.length > BUDGETS.images.maxCount) {
    failures.push(`${REGISTRY.backdrops.length} records over the catalogue budget of ${BUDGETS.images.maxCount}`);
  }
  const largest = REGISTRY.backdrops.reduce((a, b) => (b.bytes > a.bytes ? b : a), REGISTRY.backdrops[0]);
  const reachable = REGISTRY.backdrops.filter(b => (b.contexts || []).length).length;
  notes.push(`registry: ${REGISTRY.backdrops.length} records, largest ${largest.backdropKey} ${kb(largest.bytes)}, `
    + `${reachable} reachable by a declared context, ${REGISTRY.backdrops.length - reachable} gated`);
  notes.push(`lifecycle: ` + Object.entries(REGISTRY.backdrops.reduce((counts, b) => {
    counts[b.lifecycle] = (counts[b.lifecycle] || 0) + 1; return counts;
  }, {})).map(([k, v]) => `${k} ${v}`).join(', '));

  for (const note of notes) console.log(`  · ${note}`);
  if (failures.length) {
    console.error(`✗ backdrop budgets FAILED — ${failures.length} breach(es):`);
    for (const f of failures) console.error(`  - ${f}`);
    process.exit(1);
  }
  console.log('✓ backdrop budgets OK — initial payload within budget, no backdrop image or registry bundled with the app.');
}

main();
