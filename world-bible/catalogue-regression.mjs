import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

async function browserData(filename, property) {
  const context = { window:{} };
  vm.runInNewContext(await readFile(new URL(filename, import.meta.url), 'utf8'), context, { filename });
  return context.window[property];
}

const snapshot = await browserData('./catalogue-data.js', 'WorldBibleCatalogue');
const app = await readFile(new URL('./app.js', import.meta.url), 'utf8');
const pages = await Promise.all(['index.html','atlas.html','catalogue.html','entry.html','industries.html','rules.html'].map(name => readFile(new URL(`./${name}`, import.meta.url), 'utf8')));
const expectedCategories = ['living','materials','actions','crafting','storage','infrastructure','construction','fieldcraft','industry'];
const ids = new Set(snapshot.records.map(record => record.id));

if (snapshot.latestMigration !== 'V393') throw new Error(`Expected V393 snapshot, got ${snapshot.latestMigration}`);
if (snapshot.records.length < 1800) throw new Error(`Catalogue unexpectedly small: ${snapshot.records.length}`);
if (ids.size !== snapshot.records.length) throw new Error('Catalogue record ids are not unique.');
if (expectedCategories.some(category => !snapshot.counts[category])) throw new Error(`An expected category is empty: ${JSON.stringify(snapshot.counts)}`);
if (snapshot.records.some(record => !record.name || !record.detail || !record.migration || !record.sections?.length)) throw new Error('A catalogue card lacks meaningful repository-backed detail.');
if (snapshot.records.some(record => /add\s+100|named\s+(?:shit|candidate)|github issue/i.test(`${record.name} ${record.detail}`))) throw new Error('Generic issue-language leaked into the catalogue.');
if (snapshot.records.filter(record => record.category === 'living').some(record => !record.lifeGroup)) throw new Error('A living record lacks its ecology subdivision.');
if (snapshot.world?.markers?.length !== 87) throw new Error(`Expected 87 canonical genesis markers, got ${snapshot.world?.markers?.length}`);
if (Object.keys(snapshot.world?.biomes || {}).length !== 10) throw new Error(`Expected 10 generated terrain classes, got ${JSON.stringify(snapshot.world?.biomes)}`);
if (snapshot.world.markers.some(marker => !marker.accepted.includes(marker.biome))) throw new Error('An Atlas marker is placed on terrain outside its accepted biome list.');
if (!['item:stone_knife','process:split_planks','species:rabbit','flora:oak','construction:LEAN_TO'].every(id => ids.has(id))) throw new Error('Representative implemented records are missing.');
if (app.includes('api.github.com') || app.includes('raw.githubusercontent.com')) throw new Error('The World Bible still depends on a live GitHub API.');
if (pages.some(html => !html.includes('catalogue-data.js?v=v393-20260929'))) throw new Error('A World Bible page does not load the current cache-busted catalogue snapshot.');
if (pages.some(html => /â€”|Â·|Ã—/.test(html))) throw new Error('Mojibake remains in a World Bible page.');

console.log(JSON.stringify({
  records:snapshot.records.length,
  categories:snapshot.counts,
  livingGroups:snapshot.livingGroups,
  migration:snapshot.latestMigration,
  revision:snapshot.revision,
  world:{ biomes:snapshot.world.biomes, markers:snapshot.world.markers.length },
  backdrops:snapshot.backdrop,
  checks:'PASS'
}, null, 2));
