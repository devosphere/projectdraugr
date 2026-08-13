#!/usr/bin/env node
// Validation-gate tests (story #223 / task #231): prove the committed manifest is valid AND that
// the validator rejects every drift class it must catch. Node built-ins only.
//
//   node scripts/backdrops/test-validation.mjs
//
// Exits 0 with "ALL PASS" when the real manifest validates clean and every negative fixture is
// rejected by exactly the expected error; non-zero on the first failure.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { validateManifest } from './build-manifest.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const manifest = JSON.parse(readFileSync(join(REPO, 'frontend', 'src', 'backdrops', 'backdrop-manifest.json'), 'utf8'));
const DISK = manifest.backdrops.map(b => b.filename); // the real, in-sync file set

const clone = () => JSON.parse(JSON.stringify(manifest));
let failed = 0;
function expect(name, mutate, needle) {
  const m = clone();
  const disk = mutate(m) || DISK;               // mutate may return a modified disk set
  const errors = validateManifest(m, disk);
  const hit = errors.some(e => e.includes(needle));
  if (!hit) {
    failed++;
    console.error(`✗ ${name}: expected an error containing "${needle}" but got:\n    ${errors.join('\n    ') || '(none)'}`);
  } else {
    console.log(`✓ ${name}`);
  }
}

// 1. the real manifest is valid
{
  const errors = validateManifest(clone(), DISK);
  if (errors.length) { failed++; console.error(`✗ real manifest should be valid, got:\n    ${errors.join('\n    ')}`); }
  else console.log('✓ real manifest validates clean');
}

// 2. negative fixtures — each isolates one drift class
expect('unknown fallback key', m => { m.backdrops[0].fallbackKey = 'no-such-key'; }, 'points nowhere');
expect('self fallback', m => { m.backdrops[5].fallbackKey = m.backdrops[5].backdropKey; }, 'is its own fallback');
expect('circular fallback', m => {
  // point two records at each other
  const a = m.backdrops[10], b = m.backdrops[11];
  a.fallbackKey = b.backdropKey; b.fallbackKey = a.backdropKey;
}, 'circular fallback');
expect('invalid lifecycle', m => { m.backdrops[3].lifecycle = 'RETIRED'; }, 'invalid lifecycle');
expect('unknown biome', m => { m.backdrops[4].biomes = ['JUNGLE']; }, 'unknown biome');
expect('duplicate key', m => { m.backdrops[2].backdropKey = m.backdrops[1].backdropKey; }, 'duplicate backdropKey');
expect('duplicate hash', m => { m.backdrops[2].contentHash = m.backdrops[1].contentHash; }, 'duplicate content hash');
expect('missing file (manifest has extra)', m => { m.backdrops[0].filename = 'playthrough-ghost-v1.png'; return DISK; }, 'has no file');
expect('unregistered asset on disk', m => { return [...DISK, 'playthrough-orphan-v9.png']; }, 'not registered');
expect('ecotone with one biome', m => {
  const e = m.backdrops.find(b => b.siteFamily === 'ECOTONE'); e.biomes = [e.biomes[0]];
}, 'ECOTONE must list exactly two');
expect('precedence not tracking proximity', m => { m.backdrops[0].precedenceWeight = 999; }, 'precedenceWeight');
expect('ACTIVE not creature-free', m => { m.backdrops[0].creatureFree = false; }, 'not creatureFree');
expect('encodes encounter presence', m => { m.backdrops[0].creature = 'wolf'; }, 'encodes encounter presence');
expect('bad content hash format', m => { m.backdrops[0].contentHash = 'xyz'; }, 'bad contentHash');
expect('schema version mismatch', m => { m.schemaVersion = 99; }, 'schemaVersion');

if (failed) { console.error(`\n${failed} validation test(s) FAILED`); process.exit(1); }
console.log(`\nALL PASS — real manifest valid, ${15} drift classes rejected.`);
