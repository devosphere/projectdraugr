import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('./app.js', import.meta.url), 'utf8');
const begin = source.indexOf('    const categoryFor =');
const end = source.indexOf('    const safe =', begin);
if (begin < 0 || end < 0) throw new Error('World Bible classifier was not found.');
const { recordClassFor } = new Function(`${source.slice(begin, end)}; return { recordClassFor };`)();
const sourceIssue = issue => issue.number >= 45 && issue.number <= 221 && !issue.pull_request && !/^\[(EPIC|Audit)\]|\b(regression|fixture|auditor|test coverage|long-play audit)\b/i.test(issue.title);
const pages = await Promise.all([1, 2].map(page => fetch(`https://api.github.com/repos/devosphere/projectdraugr/issues?state=open&per_page=100&page=${page}`, { headers: { 'User-Agent': 'ProjectDraugr-WorldBible' } }).then(response => response.json())));
const records = new Map();
for (const issue of pages.flat().filter(sourceIssue).sort((left, right) => left.number - right.number)) {
  const ids = new Set([...String(issue.body || '').matchAll(/`([a-z][a-z0-9_]{2,})`/g)].map(match => match[1]));
  for (const id of ids) if (!records.has(id)) records.set(id, { ...recordClassFor(issue.number, issue.title, id), id, issue: issue.number });
}
const all = [...records.values()];
const categories = ['living', 'nonliving', 'actions', 'crafting', 'storage', 'infrastructure', 'construction', 'fieldcraft', 'industry'];
const counts = Object.fromEntries(categories.map(category => [category, all.filter(record => record.category === category).length]));
const livingGroups = ['wildlife-terrestrial', 'wildlife-aquatic', 'wildlife-aerial', 'wildlife-subterranean', 'monster-terrestrial', 'monster-aquatic', 'monster-aerial', 'monster-subterranean', 'native-terrestrial', 'native-aquatic', 'native-aerial', 'native-subterranean', 'flora'];
const livingCounts = Object.fromEntries(livingGroups.map(group => [group, all.filter(record => record.lifeGroup === group).length]));
const materialPattern = /(ore|stone|clay|soil|sand|gravel|flint|chert|timber|wood|branch|bark|resin|fibre|fiber|reeds?|straw|seed|feather|bone|hide|pelt|horn|tusk|shell|salt|ash|charcoal)/;
const manufacturedPattern = /(scraper|burin|maul|mortar|pestle|rake|hoe|spear|sling|trap|backpack|basket|bucket|crate|bedroll|adze|chisel|awl|hook|shovel|waterskin|quiver|arrow|knife|hammer|pickaxe|axe|yoke|harness|cart|sledge|travois|pen|coop|stable|trough|wall|gate|tower|hearth|hut|bridge|road|kiln|loom|garment|boot|hood|vest|belt|rope)$/;
const materialInLiving = all.filter(record => record.category === 'living' && materialPattern.test(record.id));
const manufacturedInNonliving = all.filter(record => record.category === 'nonliving' && manufacturedPattern.test(record.id));
if (Object.values(counts).some(count => count === 0)) throw new Error(`Expected category is empty: ${JSON.stringify(counts)}`);
if (!livingGroups.every(group => source.includes(`'${group}'`))) throw new Error('A required Living World subdivision filter is missing from the interface.');
if (materialInLiving.length || manufacturedInNonliving.length) throw new Error(`Category leak: ${JSON.stringify({ materialInLiving: materialInLiving.slice(0, 8), manufacturedInNonliving: manufacturedInNonliving.slice(0, 8) })}`);
console.log(JSON.stringify({ records: all.length, categories: counts, livingGroups: livingCounts, checks: 'PASS' }, null, 2));
