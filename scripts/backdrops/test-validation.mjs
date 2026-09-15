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
import { validateManifest, loadLegacyKeys, mergeRecord, REVIEW_CHECKLIST } from './build-manifest.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..');
const manifest = JSON.parse(readFileSync(join(REPO, 'frontend', 'src', 'backdrops', 'backdrop-manifest.json'), 'utf8'));
const DISK = manifest.backdrops.map(b => b.filename); // the real, in-sync file set

const LEGACY = loadLegacyKeys();
const clone = () => JSON.parse(JSON.stringify(manifest));
let failed = 0;
let classes = 0;
function expect(name, mutate, needle) {
  classes++;
  const m = clone();
  const disk = mutate(m) || DISK;               // mutate may return a modified disk set
  const errors = validateManifest(m, disk, LEGACY);
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
  const errors = validateManifest(clone(), DISK, LEGACY);
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

// 3. the #241 review and provenance contract — each isolates one way an image could go live unreviewed
const approve = b => {
  b.review = { state: 'APPROVED', checklist: Object.fromEntries(REVIEW_CHECKLIST.map(k => [k, true])),
    reviewedBy: 'reviewer', reviewedAt: '2026-09-14' };
  b.creatureFree = true;
  return b;
};
expect('no review at all', m => { delete m.backdrops[0].review; }, 'has no review');
expect('invalid review state', m => { m.backdrops[0].review.state = 'LOOKED_AT_IT'; }, 'invalid review state');
expect('checklist item missing', m => { delete m.backdrops[0].review.checklist.uiSafeComposition; }, 'missing review checklist item: uiSafeComposition');
expect('ACTIVE while still pending', m => {
  const b = m.backdrops[1]; b.review.state = 'PENDING';
}, 'ACTIVE without a completed review');
expect('APPROVED with an item left unchecked', m => {
  const b = approve(m.backdrops[2]); b.review.checklist.noLivingCreatures = false;
}, 'APPROVED with an unchecked item: noLivingCreatures');
expect('APPROVED with nobody named', m => { approve(m.backdrops[3]).review.reviewedBy = ''; }, 'APPROVED without a reviewer');
expect('APPROVED with no date', m => { approve(m.backdrops[4]).review.reviewedAt = null; }, 'APPROVED without a review date');
expect('a new image claiming LEGACY', m => {
  // Rename a record out of the frozen list: exactly what a new asset slipping in as LEGACY would look like. Anything
  // falling back to it follows the rename, so the only thing wrong with the manifest is the claim itself.
  const b = m.backdrops[7], oldKey = b.backdropKey;
  b.backdropKey = 'brand-new-unreviewed-image';
  for (const o of m.backdrops) if (o.fallbackKey === oldKey) o.fallbackKey = b.backdropKey;
}, 'not in the frozen legacy list');
expect('a replaced image staying LEGACY', m => {
  const b = m.backdrops[6]; b.version = 2;
  b.provenance.supersedes = [{ version: 1, contentHash: 'a'.repeat(64), retiredAt: '2026-09-14' }];
}, 'cannot stay LEGACY');
expect('creature-free claimed without the reviewer saying so', m => {
  const b = approve(m.backdrops[8]); b.review.state = 'PENDING'; b.lifecycle = 'PENDING_REVIEW'; b.review.checklist.noLivingCreatures = false;
}, 'claims creatureFree');
expect('ACTIVE but not widescreen', m => { const b = m.backdrops[9]; b.width = 1024; b.height = 1024; b.aspectRatio = 1; }, 'not widescreen');
expect('replacement that kept no history', m => {
  const b = approve(m.backdrops[12]); b.version = 2; b.provenance.supersedes = [];
}, 'keeps no record of version 1');
expect('supersedes entry that is the current image', m => {
  const b = approve(m.backdrops[13]); b.version = 2;
  b.provenance.supersedes = [{ version: 1, contentHash: b.contentHash, retiredAt: '2026-09-14' }];
}, 'is the current image');
expect('supersedes that is not a list', m => { m.backdrops[14].provenance.supersedes = null; }, 'must be a list');
expect('legacy list naming a deleted backdrop', m => {
  const gone = m.backdrops.findIndex(b => b.fallbackKey !== null && !m.backdrops.some(o => o.fallbackKey === b.backdropKey));
  m.backdrops.splice(gone, 1);
  return DISK.filter(f => m.backdrops.some(b => b.filename === f));
}, 'no longer exists');

// A properly reviewed replacement is accepted: the contract admits good work, it does not only refuse.
{
  classes++;
  const m = clone();
  const b = approve(m.backdrops[15]); b.version = 2;
  b.provenance.supersedes = [{ version: 1, contentHash: 'b'.repeat(64), retiredAt: '2026-09-14' }];
  const mine = validateManifest(m, DISK, LEGACY).filter(e => e.includes(`[${b.backdropKey}]`));
  if (mine.length) { failed++; console.error(`✗ an approved, recorded replacement must pass, got:\n    ${mine.join('\n    ')}`); }
  else console.log('✓ approved replacement with history is accepted');
}

// 4. regenerating merges over history — it never overwrites a review or loses what an image replaced
{
  classes++;
  const prior = approve(clone().backdrops[20]);
  const same = { ...prior, review: undefined, lifecycle: 'PENDING_REVIEW', creatureFree: false,
    provenance: { ...prior.provenance, supersedes: [] } };
  const kept = mergeRecord(prior, same, '2026-09-14');
  const changed = mergeRecord(prior, { ...same, contentHash: 'c'.repeat(64), version: 2 }, '2026-09-14');
  const problems = [];
  if (kept.review.state !== 'APPROVED' || kept.lifecycle !== prior.lifecycle || kept.creatureFree !== true) problems.push('an unchanged image lost its review');
  if (changed.lifecycle !== 'PENDING_REVIEW' || changed.review.state !== 'PENDING' || changed.creatureFree !== false) problems.push('a replaced image kept the old review');
  if (!changed.provenance.supersedes.some(h => h.version === prior.version && h.contentHash === prior.contentHash)) problems.push('a replaced image forgot what it replaced');
  if (mergeRecord(undefined, same) !== same) problems.push('a brand-new image was not left pending');
  if (problems.length) { failed++; console.error(`✗ regeneration merge:\n    ${problems.join('\n    ')}`); }
  else console.log('✓ regeneration keeps reviews for unchanged images and records replacements');
}

if (failed) { console.error(`\n${failed} validation test(s) FAILED`); process.exit(1); }
console.log(`\nALL PASS — real manifest valid, ${classes} classes checked.`);
