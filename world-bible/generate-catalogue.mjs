import { readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = resolve(fileURLToPath(new URL('.', import.meta.url)));
const root = resolve(here, '..');
const migrationRoot = resolve(root, 'backend/src/main/resources/db/migration');
const output = resolve(here, 'catalogue-data.js');

const migrationFiles = (await readdir(migrationRoot))
  .filter(name => /^V\d+.*\.sql$/i.test(name))
  .sort((left, right) => Number(left.match(/^V(\d+)/i)[1]) - Number(right.match(/^V(\d+)/i)[1]) || left.localeCompare(right));

const tables = new Map();
const provenance = new Map();

function table(name) {
  if (!tables.has(name)) tables.set(name, []);
  return tables.get(name);
}

function stripLineComments(sql) {
  let result = '', quote = false;
  for (let index = 0; index < sql.length; index += 1) {
    const char = sql[index], next = sql[index + 1];
    if (char === "'" && quote && next === "'") { result += "''"; index += 1; continue; }
    if (char === "'") quote = !quote;
    if (!quote && char === '-' && next === '-') {
      while (index < sql.length && sql[index] !== '\n') index += 1;
      result += '\n';
      continue;
    }
    result += char;
  }
  return result;
}

function statementEnd(sql, start) {
  let quote = false, dollar = null, depth = 0;
  for (let index = start; index < sql.length; index += 1) {
    if (dollar) {
      if (sql.startsWith(dollar, index)) { index += dollar.length - 1; dollar = null; }
      continue;
    }
    if (!quote && sql[index] === '$') {
      const found = sql.slice(index).match(/^\$[A-Za-z0-9_]*\$/);
      if (found) { dollar = found[0]; index += dollar.length - 1; continue; }
    }
    if (sql[index] === "'" && quote && sql[index + 1] === "'") { index += 1; continue; }
    if (sql[index] === "'") { quote = !quote; continue; }
    if (quote) continue;
    if (sql[index] === '(' || sql[index] === '[') depth += 1;
    if (sql[index] === ')' || sql[index] === ']') depth -= 1;
    if (sql[index] === ';' && depth === 0) return index;
  }
  return sql.length;
}

function splitTopLevel(source) {
  const values = [];
  let quote = false, depth = 0, start = 0;
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];
    if (char === "'" && quote && source[index + 1] === "'") { index += 1; continue; }
    if (char === "'") { quote = !quote; continue; }
    if (quote) continue;
    if (char === '(' || char === '[' || char === '{') depth += 1;
    if (char === ')' || char === ']' || char === '}') depth -= 1;
    if (char === ',' && depth === 0) { values.push(source.slice(start, index).trim()); start = index + 1; }
  }
  values.push(source.slice(start).trim());
  return values;
}

function tuples(source) {
  const result = [];
  let quote = false, depth = 0, start = -1;
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];
    if (char === "'" && quote && source[index + 1] === "'") { index += 1; continue; }
    if (char === "'") { quote = !quote; continue; }
    if (quote) continue;
    if (char === '(') { if (depth === 0) start = index + 1; depth += 1; }
    if (char === ')') { depth -= 1; if (depth === 0 && start >= 0) result.push(source.slice(start, index)); }
  }
  return result;
}

function value(raw) {
  const token = raw.trim().replace(/::[a-z_][a-z0-9_]*(?:\[\])?$/i, '');
  if (/^NULL$/i.test(token)) return null;
  if (/^TRUE$/i.test(token)) return true;
  if (/^FALSE$/i.test(token)) return false;
  if (/^-?\d+(?:\.\d+)?$/.test(token)) return Number(token);
  if (token.startsWith("'") && token.endsWith("'")) return token.slice(1, -1).replaceAll("''", "'");
  return token;
}

function literalValue(raw) {
  const token = raw.trim().replace(/::[a-z_][a-z0-9_]*(?:\[\])?$/i, '');
  if (/^(?:NULL|TRUE|FALSE)$/i.test(token) || /^-?\d+(?:\.\d+)?$/.test(token) || (token.startsWith("'") && token.endsWith("'"))) {
    return { supported: true, value: value(raw) };
  }
  return { supported: false, value: null };
}

for (const filename of migrationFiles) {
  const sql = stripLineComments(await readFile(resolve(migrationRoot, filename), 'utf8'));
  const matcher = /INSERT\s+INTO\s+([a-z_][a-z0-9_]*)\s*\(([^)]+)\)\s*VALUES\s*/gi;
  for (let match; (match = matcher.exec(sql));) {
    const tableName = match[1].toLowerCase();
    const columns = match[2].split(',').map(column => column.trim().replaceAll('"', '').toLowerCase());
    const end = statementEnd(sql, match.index);
    const body = sql.slice(match.index + match[0].length, end).replace(/\s+ON\s+CONFLICT[\s\S]*$/i, '');
    for (const tuple of tuples(body)) {
      const values = splitTopLevel(tuple).map(value);
      if (values.length !== columns.length) continue;
      const row = Object.fromEntries(columns.map((column, index) => [column, values[index]]));
      row.__migration = filename.match(/^V\d+/i)[0].toUpperCase();
      table(tableName).push(row);
    }
  }


  // Apply the simple literal UPDATE statements used by catalogue migrations.
  // This keeps statuses and classifications faithful to the final migrated state
  // (for example Reedkin becoming ACTIVE rather than remaining a candidate).
  const updateMatcher = /UPDATE\s+([a-z_][a-z0-9_]*)(?:\s+[a-z_][a-z0-9_]*)?\s+SET\s+/gi;
  for (let match; (match = updateMatcher.exec(sql));) {
    const tableName = match[1].toLowerCase();
    const end = statementEnd(sql, match.index);
    const statement = sql.slice(match.index + match[0].length, end);
    const whereMatch = statement.match(/\s+WHERE\s+/i);
    if (!whereMatch || whereMatch.index == null) continue;

    const assignments = {};
    for (const assignment of splitTopLevel(statement.slice(0, whereMatch.index))) {
      const parsed = assignment.match(/^([a-z_][a-z0-9_.]*)\s*=\s*([\s\S]+)$/i);
      if (!parsed) continue;
      const literal = literalValue(parsed[2]);
      if (literal.supported) assignments[parsed[1].split('.').pop().toLowerCase()] = literal.value;
    }
    if (!Object.keys(assignments).length) continue;

    const condition = statement.slice(whereMatch.index + whereMatch[0].length).trim();
    const equality = condition.match(/^([a-z_][a-z0-9_.]*)\s*=\s*([^\s]+(?:\s*::\s*[a-z_][a-z0-9_]*(?:\[\])?)?)(?:\s|$)/i);
    const membership = condition.match(/^([a-z_][a-z0-9_.]*)\s+IN\s*\(([\s\S]*?)\)(?:\s|$)/i);
    let column = null;
    let accepted = [];
    if (equality) {
      const literal = literalValue(equality[2]);
      if (literal.supported) {
        column = equality[1].split('.').pop().toLowerCase();
        accepted = [literal.value];
      }
    } else if (membership) {
      column = membership[1].split('.').pop().toLowerCase();
      accepted = splitTopLevel(membership[2]).map(literalValue).filter(entry => entry.supported).map(entry => entry.value);
    }
    if (!column || !accepted.length) continue;

    for (const row of table(tableName)) {
      if (!accepted.some(candidate => String(candidate) === String(row[column]))) continue;
      Object.assign(row, assignments, { __migration: filename.match(/^V\d+/i)[0].toUpperCase() });
    }
  }
}

const indexBy = (name, key) => {
  const result = new Map();
  for (const row of table(name)) if (row[key] != null) result.set(String(row[key]), row);
  return result;
};
const groupBy = (name, key) => {
  const result = new Map();
  for (const row of table(name)) {
    const id = String(row[key] ?? '');
    if (!result.has(id)) result.set(id, []);
    result.get(id).push(row);
  }
  return result;
};
const title = key => String(key || '').replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase());
const truth = value => value === true || String(value).toUpperCase() === 'TRUE';
const number = value => Number.isFinite(Number(value)) ? Number(value) : null;
const list = value => String(value || '').split(',').map(part => part.trim()).filter(Boolean);
const compact = values => [...new Set(values.filter(value => value != null && value !== ''))];
const formatMass = grams => grams == null ? null : grams >= 1000 ? `${(grams / 1000).toFixed(grams % 1000 ? 2 : 0)} kg` : `${grams} g`;
const formatVolume = ml => ml == null ? null : ml >= 1000 ? `${(ml / 1000).toFixed(ml % 1000 ? 2 : 0)} L` : `${ml} mL`;
const migrate = row => row?.__migration || 'Migration record';

const items = indexBy('item_definition', 'item_key');
const processes = indexBy('material_process', 'process_key');
const processInputs = groupBy('material_process_input', 'process_key');
const processGroups = groupBy('material_process_input_group', 'process_key');
const sources = groupBy('item_source', 'item_key');
const flora = indexBy('flora_definition', 'flora_key');
const floraDrops = groupBy('flora_drop', 'flora_key');
const wildlife = indexBy('wildlife_species', 'species_key');
const wildlifeDrops = groupBy('wildlife_drop', 'species_key');
const wildlifeSigns = groupBy('wildlife_sign', 'species_key');
const monsters = indexBy('monster_profile', 'species_key');
const natives = indexBy('native_candidate', 'candidate_key');
const minerals = indexBy('mineral_definition', 'mineral_key');
const constructions = indexBy('construction_kind', 'project_kind');
const assemblies = indexBy('assembly_definition', 'assembly_key');
const assemblyStages = groupBy('assembly_stage', 'assembly_key');
const assemblyRequirements = groupBy('assembly_stage_requirement', 'assembly_key');
const compatibilities = groupBy('item_equipment_compatibility', 'item_key');
const capacities = indexBy('container_capacity_default', 'item_key');
const weaponProfiles = indexBy('weapon_profile', 'item_key');
const armourProfiles = indexBy('armour_protection', 'item_key');
const tools = indexBy('tool_profile', 'item_key');
const techniques = indexBy('technique_definition', 'technique_key');
const processSubjects = groupBy('process_subject', 'process_key');
const activityCategories = indexBy('activity_category', 'category_key');
const categoryTerms = groupBy('category_term', 'category_key');

const processByOutput = new Map();
for (const process of processes.values()) {
  const key = String(process.output_item_key || '');
  if (!processByOutput.has(key)) processByOutput.set(key, []);
  processByOutput.get(key).push(process);
}
const usedBy = new Map();
for (const row of [...table('material_process_input'), ...table('material_process_input_group')]) {
  const key = String(row.item_key || '');
  if (!usedBy.has(key)) usedBy.set(key, []);
  usedBy.get(key).push(row);
}

const records = [];
const add = record => {
  const normalized = { status:'Implemented', icon:'◆', searchable:[], sections:[], ...record };
  normalized.searchable = compact([normalized.name, normalized.key, normalized.type, normalized.category, normalized.subcategory, normalized.detail, ...normalized.searchable]).join(' ');
  provenance.set(normalized.id, normalized.migration);
  records.push(normalized);
};

const itemCategory = category => {
  const value = String(category || '').toUpperCase();
  if (value === 'CONTAINER') return ['storage', 'Containers', '▣'];
  if (['MATERIAL','FOOD','INGREDIENT','MEDICINE','FUEL'].includes(value)) return ['materials', title(value), '●'];
  if (['WEAPON','ARMOR','ARMOUR','CLOTHING','TOOL','EQUIPMENT'].includes(value)) return ['crafting', title(value), value === 'WEAPON' ? '†' : value === 'TOOL' ? '⚒' : '◈'];
  return ['crafting', title(value || 'Object'), '◆'];
};

function procedureCategory(process) {
  const text = `${process.category_key || ''} ${process.domain_key || ''} ${process.keywords || ''}`.toLowerCase();
  if (/hunt|fish|trap|track|butcher|husband|taming|animal|wildlife/.test(text)) return 'fieldcraft';
  if (/construct/.test(text)) return 'construction';
  if (/inhabit|storage|store|haul|logistic/.test(text)) return 'storage';
  if (/metall|smith|smelt|forge|industry|agricultur|farm|crop|textile|weav|forestry|woodworking|stoneworking|ceramic|pottery|lime|charcoal|tann/.test(text)) return 'industry';
  return 'crafting';
}

for (const item of items.values()) {
  const key = String(item.item_key);
  const [category, subcategory, icon] = itemCategory(item.category);
  const madeBy = processByOutput.get(key) || [];
  const acquisition = compact([
    ...(sources.get(key) || []).map(source => `${title(source.source_kind)}: ${String(source.detail || '').replaceAll('_',' ')}`),
    ...madeBy.map(process => `Procedure: ${process.display_name}`),
    ...[...floraDrops.values()].flat().filter(drop => drop.item_key === key).map(drop => `Harvest from ${title(drop.flora_key)} (${drop.yield_min}–${drop.yield_max})`),
    ...[...wildlifeDrops.values()].flat().filter(drop => drop.item_key === key).map(drop => `Recover from ${title(drop.species_key)} (${drop.yield_min}–${drop.yield_max})`)
  ]);
  const uses = compact((usedBy.get(key) || []).map(input => {
    const process = processes.get(String(input.process_key));
    return process ? `${input.quantity || 1} × for ${process.display_name}` : null;
  }));
  const compatibility = compatibilities.get(key) || [];
  const capacity = capacities.get(key);
  const weapon = weaponProfiles.get(key), armour = armourProfiles.get(key), tool = tools.get(key);
  add({
    id:`item:${key}`, key, name:String(item.display_name || title(key)), category, subcategory,
    type:'Physical object', icon, migration:migrate(item),
    detail:`${subcategory} · ${formatMass(number(item.unit_mass_grams)) || 'mass not recorded'} · ${formatVolume(number(item.unit_volume_ml)) || 'volume not recorded'}`,
    searchable:[item.category, ...acquisition, ...uses],
    sections:[
      { title:'Physical properties', rows:compact([`Mass: ${formatMass(number(item.unit_mass_grams)) || 'not recorded'}`, `Volume: ${formatVolume(number(item.unit_volume_ml)) || 'not recorded'}`, `Stackable: ${truth(item.stackable) ? 'yes' : 'no'}`, `Equippable: ${truth(item.equippable) ? 'yes' : 'no'}`]) },
      { title:'Acquisition', rows:acquisition.length ? acquisition : ['No direct source row was found; obtainability is governed by its linked world procedure or persisted source.'] },
      { title:'Applications', rows:uses.length ? uses : ['No material-process consumer is currently registered.'] },
      ...(compatibility.length || capacity || weapon || armour || tool ? [{ title:'Use profile', rows:compact([
        ...compatibility.map(row => `${title(row.body_position)} · ${title(row.layer)}`),
        capacity ? `Capacity: ${formatMass(number(capacity.max_mass_grams))}; ${formatVolume(number(capacity.max_volume_ml))}` : null,
        weapon ? `Weapon: ${title(weapon.weapon_class || weapon.attack_class || '')}${weapon.damage ? ` · damage ${weapon.damage}` : ''}` : null,
        armour ? `Protection: ${armour.protection_value ?? armour.protection ?? 'registered'}` : null,
        tool ? `Tool class: ${title(tool.tool_class || tool.action_class || '')}` : null
      ]) }] : [])
    ]
  });
}

for (const process of processes.values()) {
  const key = String(process.process_key);
  const direct = (processInputs.get(key) || []).map(input => `${input.quantity || 1} × ${items.get(String(input.item_key))?.display_name || title(input.item_key)}`);
  const alternatives = new Map();
  for (const input of processGroups.get(key) || []) {
    if (!alternatives.has(input.group_name)) alternatives.set(input.group_name, []);
    alternatives.get(input.group_name).push(`${input.quantity || 1} × ${items.get(String(input.item_key))?.display_name || title(input.item_key)}`);
  }
  const inputRows = [...direct, ...[...alternatives.entries()].map(([group, values]) => `${title(group)} — choose one: ${values.join(' / ')}`)];
  const outputItem = items.get(String(process.output_item_key));
  const aliases = compact([...(list(process.keywords)), ...(processSubjects.get(key) || []).map(row => row.subject_term || row.subject || row.term)]);
  const category = procedureCategory(process);
  add({
    id:`process:${key}`, key, name:String(process.display_name || title(key)), category, subcategory:title(process.domain_key || process.category_key || 'Procedure'),
    type:'Executable procedure', icon:'›', migration:migrate(process),
    detail:`${title(process.domain_key || 'Procedure')} · ${process.duration_minutes || '?'} minutes · produces ${outputItem?.display_name || title(process.output_item_key)}`,
    searchable:[process.keywords, process.narration, ...aliases, ...inputRows],
    sections:[
      { title:'Required inputs', rows:inputRows.length ? inputRows : ['No carried material input is required.'] },
      { title:'Tools and conditions', rows:compact([process.tool_class ? `Tool class: ${title(process.tool_class)}` : 'Bare hands or no tool class', truth(process.requires_fire) ? 'Requires fire' : null, truth(process.requires_water) ? 'Requires water' : null, `Duration: ${process.duration_minutes || '?'} minutes`]) },
      { title:'Physical result', rows:[`${process.output_min || 1}–${process.output_max || process.output_min || 1} × ${outputItem?.display_name || title(process.output_item_key)}`] },
      { title:'Recognised language', rows:aliases.length ? aliases : ['The procedure key is the canonical action phrase.'] },
      { title:'World response', rows:[String(process.narration || 'The result is resolved from the physical inputs and persisted to world state.')] }
    ]
  });
}

for (const activity of activityCategories.values()) {
  const key = String(activity.category_key), terms = (categoryTerms.get(key) || []).sort((a,b) => Number(b.weight || 0) - Number(a.weight || 0) || String(a.term).localeCompare(String(b.term)));
  add({ id:`action:${key.toLowerCase()}`, key, name:String(activity.display_name || title(key)), category:'actions', subcategory:'Action interpretation', type:'Action family', icon:'›', migration:migrate(activity),
    detail:String(activity.description || 'Recognised Chronicle action family.'), searchable:terms.map(row => row.term),
    sections:[
      { title:'World meaning', rows:[String(activity.description)] },
      { title:'Recognised language', rows:terms.length ? terms.map(row => `${row.term} · weight ${row.weight}`) : ['No vocabulary terms are registered.'] },
      { title:'Resolution order', rows:[`Precedence: ${activity.precedence}`] }
    ] });
}

for (const plant of flora.values()) {
  const key = String(plant.flora_key), drops = floraDrops.get(key) || [];
  add({ id:`flora:${key}`, key, name:title(key), category:'living', subcategory:'Flora', lifeGroup:'flora', type:title(plant.organism_type || 'Flora'), icon:'🌿', migration:migrate(plant),
    detail:`${title(plant.organism_type || 'Flora')} · ${list(plant.biome_affinity).map(title).join(', ')}`,
    searchable:[plant.biome_affinity, plant.tool_required, ...drops.map(drop => drop.item_key)],
    sections:[
      { title:'Habitat and renewal', rows:compact([`Biomes: ${list(plant.biome_affinity).map(title).join(', ')}`, `Regrowth: ${plant.regrowth_days ?? '?'} days`, truth(plant.is_poisonous) ? 'Poisonous' : 'Not marked poisonous']) },
      { title:'Harvest', rows:drops.length ? drops.map(drop => `${drop.yield_min || 1}–${drop.yield_max || drop.yield_min || 1} × ${items.get(String(drop.item_key))?.display_name || title(drop.item_key)}${drop.season ? ` · ${title(drop.season)}` : ''}${drop.tool_condition ? ` · ${title(drop.tool_condition)}` : ''}`) : ['No harvest product is registered.'] },
      { title:'Gathering condition', rows:[plant.tool_required ? `Requires ${title(plant.tool_required)}` : 'Can be gathered by hand where reachable and seasonally present.'] }
    ] });
}

const movementRealm = movement => /AQUATIC|AMPHIBIOUS/i.test(String(movement)) ? 'aquatic' : /AERIAL/i.test(String(movement)) ? 'aerial' : /SUBTERRANEAN/i.test(String(movement)) ? 'subterranean' : 'terrestrial';
for (const animal of wildlife.values()) {
  const key = String(animal.species_key), monster = monsters.get(key), realm = movementRealm(animal.movement_class), drops = wildlifeDrops.get(key) || [], signs = wildlifeSigns.get(key) || [];
  const native = [...natives.values()].find(candidate => candidate.species_key === key);
  const group = native ? 'native' : monster || String(animal.kingdom_class).toUpperCase() === 'MONSTRUM' ? 'monster' : 'wildlife';
  add({ id:`species:${key}`, key, name:title(key), category:'living', subcategory:`${title(group)} · ${title(realm)}`, lifeGroup:`${group}-${realm}`, type:title(group), icon:group === 'monster' ? '▲' : group === 'native' ? '◇' : realm === 'aerial' ? '🪶' : realm === 'aquatic' ? '🐟' : '✦', migration:migrate(animal),
    detail:`${title(group)} · ${title(realm)} · ${list(animal.biome_affinity).map(title).join(', ')}`,
    searchable:[animal.kingdom_class, animal.ecological_role, animal.activity_cycle, animal.size_tier, animal.biome_affinity, ...drops.map(drop => drop.item_key)],
    sections:[
      { title:'Ecology', rows:compact([`Class: ${title(animal.kingdom_class)}`, `Role: ${title(animal.ecological_role)}`, `Activity: ${title(animal.activity_cycle)}`, `Movement: ${title(animal.movement_class)}`, `Size: ${title(animal.size_tier)}`, `Biomes: ${list(animal.biome_affinity).map(title).join(', ')}`]) },
      { title:'Behaviour', rows:compact([truth(animal.ambush_hunter) ? 'Ambush hunter' : null, truth(animal.pack_hunter) ? 'Pack hunter' : null, truth(animal.territorial) ? 'Territorial' : null, `Tamability: ${animal.tamability ?? 0}`, monster ? `Aggression: ${title(monster.aggression)} · resistance ${monster.resistance} · sight ${monster.sight_radius} chunks` : null, monster?.special_mechanic ? `Special mechanic: ${title(monster.special_mechanic)}` : null]) },
      { title:'Signs and tracking', rows:signs.length ? signs.map(sign => compact([title(sign.sign_kind || sign.sign_type), sign.description, sign.persistence_hours ? `${sign.persistence_hours} hours` : null]).join(' · ')) : ['Encounter evidence is resolved by the species and local ecology systems.'] },
      { title:'Physical recovery', rows:drops.length ? drops.map(drop => `${drop.yield_min || 1}–${drop.yield_max || drop.yield_min || 1} × ${items.get(String(drop.item_key))?.display_name || title(drop.item_key)}${drop.rarity != null ? ` · recovery factor ${drop.rarity}` : ''}`) : ['No carcass or harvest product is registered.'] }
    ] });
}

for (const candidate of natives.values()) {
  const key = String(candidate.candidate_key);
  if (candidate.species_key && wildlife.has(String(candidate.species_key))) continue;
  const realm = /CAVE|MOUNTAIN|DEEP/i.test(String(candidate.ground)) ? 'subterranean' : /WETLAND|RIVER|LAKE/i.test(String(candidate.ground)) ? 'aquatic' : /AERIAL|CLIFF|AERIE/i.test(String(candidate.ground)) ? 'aerial' : 'terrestrial';
  add({ id:`native:${key}`, key, name:String(candidate.display_name || title(key)), category:'living', subcategory:`Native people · ${title(realm)}`, lifeGroup:`native-${realm}`, type:'Native candidate', icon:'◇', status:title(candidate.status || 'Candidate'), migration:migrate(candidate),
    detail:`Tier ${candidate.proposed_tier} ${title(candidate.proposed_class)} · ${String(candidate.ground || '').replaceAll('_',' ')}`,
    searchable:[candidate.ground, candidate.home_form, candidate.note],
    sections:[
      { title:'Classification', rows:[`Proposed tier: ${candidate.proposed_tier}`, `Cognition: ${title(candidate.proposed_class)}`, `Status: ${title(candidate.status || 'Candidate')}`] },
      { title:'Territory and home', rows:[String(candidate.ground), String(candidate.home_form)] },
      { title:'World contract', rows:[String(candidate.note)] }
    ] });
}

for (const mineral of minerals.values()) {
  const key = String(mineral.mineral_key);
  add({ id:`mineral:${key}`, key, name:String(mineral.display_name || title(key)), category:'materials', subcategory:'Mineral source', type:'Geological material', icon:'⬡', migration:migrate(mineral),
    detail:`Mineral source · ${list(mineral.biome_affinity || mineral.biomes).map(title).join(', ') || 'geology-bound'}`,
    searchable:Object.values(mineral),
    sections:[{ title:'Deposit and access', rows:compact(Object.entries(mineral).filter(([field]) => !field.startsWith('__') && !['mineral_key','display_name'].includes(field)).map(([field, entry]) => `${title(field)}: ${entry}`)) }] });
}

for (const construction of constructions.values()) {
  const key = String(construction.project_kind), assembly = assemblies.get(key) || [...assemblies.values()].find(row => row.project_kind === key || row.output_key === key);
  const stages = assembly ? assemblyStages.get(String(assembly.assembly_key)) || [] : [];
  const requirements = assembly ? assemblyRequirements.get(String(assembly.assembly_key)) || [] : [];
  const category = truth(construction.is_shelter) ? 'construction' : 'infrastructure';
  add({ id:`construction:${key}`, key, name:String(construction.display_name || title(key)), category, subcategory:truth(construction.is_shelter) ? 'Shelter and camp' : truth(construction.is_workstation) ? 'Workstation' : 'World infrastructure', type:'Persistent construction', icon:truth(construction.is_shelter) ? '⌂' : '▤', migration:migrate(construction),
    detail:`${title(construction.domain_key || 'Construction')} · ${truth(construction.is_shelter) ? 'shelter' : truth(construction.is_workstation) ? 'workstation' : 'infrastructure'} · ${truth(construction.decays) ? 'decays' : 'persistent'}`,
    searchable:[construction.domain_key, ...stages.map(stage => stage.display_name), ...requirements.map(row => row.item_key)],
    sections:[
      { title:'Construction profile', rows:[`Domain: ${title(construction.domain_key)}`, `Shelter: ${truth(construction.is_shelter) ? 'yes' : 'no'}`, `Workstation: ${truth(construction.is_workstation) ? 'yes' : 'no'}`, `Decay model: ${truth(construction.decays) ? 'yes' : 'no'}`] },
      { title:'Stages', rows:stages.length ? stages.sort((a,b) => Number(a.stage_order || a.stage_number || 0) - Number(b.stage_order || b.stage_number || 0)).map(stage => String(stage.display_name || title(stage.stage_key))) : ['The construction kind is registered without a linked staged assembly.'] },
      { title:'Materials', rows:requirements.length ? requirements.map(row => `${row.quantity || 1} × ${items.get(String(row.item_key))?.display_name || title(row.item_key)}${row.stage_key ? ` · ${title(row.stage_key)}` : ''}`) : ['Requirements are resolved by its construction handler or linked assembly.'] }
    ] });
}

for (const assembly of assemblies.values()) {
  const key = String(assembly.assembly_key);
  if (constructions.has(String(assembly.project_kind || key))) continue;
  const stages = assemblyStages.get(key) || [], requirements = assemblyRequirements.get(key) || [];
  const outputKey = String(assembly.output_item_key || assembly.output_key || '');
  const output = items.get(outputKey);
  const category = output?.category === 'CONTAINER' ? 'storage' : /shelter|wall|bridge|road|tower|pen|coop|stable|hut|camp|well|kiln|furnace/i.test(`${key} ${assembly.display_name || ''}`) ? 'construction' : 'crafting';
  add({ id:`assembly:${key}`, key, name:String(assembly.display_name || title(key)), category, subcategory:'Staged assembly', type:'Assembly procedure', icon:category === 'construction' ? '⌂' : category === 'storage' ? '▣' : '⚒', migration:migrate(assembly),
    detail:`${stages.length} stages · ${requirements.length} material requirements${output ? ` · produces ${output.display_name}` : ''}`,
    searchable:[outputKey, ...stages.map(row => row.display_name), ...requirements.map(row => row.item_key)],
    sections:[
      { title:'Stages', rows:stages.length ? stages.sort((a,b) => Number(a.stage_order || a.stage_number || 0) - Number(b.stage_order || b.stage_number || 0)).map(stage => String(stage.display_name || title(stage.stage_key))) : ['No explicit stage rows are registered.'] },
      { title:'Materials', rows:requirements.length ? requirements.map(row => `${row.quantity || 1} × ${items.get(String(row.item_key))?.display_name || title(row.item_key)}${row.stage_key ? ` · ${title(row.stage_key)}` : ''}`) : ['No explicit material rows are registered.'] },
      { title:'Result', rows:[output ? output.display_name : title(outputKey || key)] }
    ] });
}

for (const technique of techniques.values()) {
  const key = String(technique.technique_key);
  const category = /hunt|fish|track|trap|animal|husband/i.test(String(technique.domain_key)) ? 'fieldcraft' : /metal|agric|textile|forest|stone|wood|industry/i.test(String(technique.domain_key)) ? 'industry' : 'crafting';
  add({ id:`technique:${key}`, key, name:String(technique.display_name || title(key)), category, subcategory:`Technique · ${title(technique.domain_key)}`, type:'Technique', icon:'›', migration:migrate(technique),
    detail:`${title(technique.domain_key)} · difficulty ${technique.difficulty ?? 'not recorded'}`,
    searchable:Object.values(technique),
    sections:[{ title:'Technique', rows:compact([technique.principle, technique.produces_item ? `Produces ${items.get(String(technique.produces_item))?.display_name || title(technique.produces_item)}` : null, technique.requires_tool ? `Requires ${title(technique.requires_tool)}` : null]) }] });
}

const deduplicated = [...new Map(records.map(record => [record.id, record])).values()].sort((left, right) => left.name.localeCompare(right.name));
const categoryOrder = ['living','materials','actions','crafting','storage','infrastructure','construction','fieldcraft','industry'];
const counts = Object.fromEntries(categoryOrder.map(category => [category, deduplicated.filter(record => record.category === category).length]));
const livingGroups = Object.fromEntries([...new Set(deduplicated.filter(record => record.category === 'living').map(record => record.lifeGroup).filter(Boolean))].sort().map(group => [group, deduplicated.filter(record => record.lifeGroup === group).length]));
const latestMigration = migrationFiles.at(-1).match(/^V\d+/i)[0].toUpperCase();
let revision = 'working-tree';
try { revision = execFileSync('git', ['rev-parse','--short','HEAD'], { cwd:root, encoding:'utf8' }).trim(); } catch {}
let backdrop = { total:0, active:0, inactive:0, schemaVersion:null };
try {
  const manifest = JSON.parse(await readFile(resolve(root, 'frontend/src/backdrops/backdrop-manifest.json'), 'utf8'));
  backdrop = { total:manifest.backdrops.length, active:manifest.backdrops.filter(item => item.lifecycle === 'ACTIVE').length, inactive:manifest.backdrops.filter(item => item.lifecycle !== 'ACTIVE').length, schemaVersion:manifest.schemaVersion };
} catch {}

// Reproduce the canonical MVP world placement plan from WorldGenesisService.
// Keeping this beside the catalogue build makes the static Atlas follow the same
// seed, terrain rules, marker ordering, and placement salts as the backend.
const world = await buildWorldSnapshot();

async function buildWorldSnapshot() {
  const seed = 681013497n, width = 28, height = 20;
  const source = stripLineComments(await readFile(resolve(root, 'backend/src/main/java/com/devosphere/draugr/world/genesis/WorldGenesisService.java'), 'utf8'));
  const specs = [];
  for (const match of source.matchAll(/new MarkerSpec\("([^"]+)",\s*"([^"]+)"((?:,\s*"[^"]+")+?)\)/g)) {
    specs.push({ category:match[1], label:match[2], accepted:[...match[3].matchAll(/"([^"]+)"/g)].map(entry => entry[1]) });
  }
  for (const match of source.matchAll(/new EdgeMarkerSpec\("([^"]+)",\s*"([^"]+)",\s*new String\[\]\{([^}]+)\},\s*new String\[\]\{([^}]+)\}\)/g)) {
    specs.push({ category:match[1], label:match[2], accepted:[...match[3].matchAll(/"([^"]+)"/g)].map(entry => entry[1]), besides:[...match[4].matchAll(/"([^"]+)"/g)].map(entry => entry[1]), edge:true });
  }

  const long = entry => BigInt.asIntN(64, entry);
  const unsignedShift = (entry, bits) => BigInt.asUintN(64, entry) >> BigInt(bits);
  const floorMod = (entry, divisor) => ((entry % divisor) + divisor) % divisor;
  const variation = (x, y) => {
    let entry = long(seed ^ long(BigInt(x) * 0x9E3779B97F4A7C15n) ^ long(BigInt(y) * 0xC2B2AE3D27D4EB4Fn));
    entry = long(entry ^ unsignedShift(entry, 30)); entry = long(entry * 0xBF58476D1CE4E5B9n);
    entry = long(entry ^ unsignedShift(entry, 27)); entry = long(entry * 0x94D049BB133111EBn);
    entry = long(entry ^ unsignedShift(entry, 31));
    return Number(BigInt.asUintN(64, entry) & 0xFFFFn) / 65535 - 0.5;
  };
  const rawElevation = (x, y) => {
    const nx = x / Math.max(1, width - 1), ny = y / Math.max(1, height - 1);
    return 0.49 + 0.22 * Math.sin(nx * 7 + Number(seed) * 0.00000003) + 0.16 * Math.cos(ny * 8) + 0.09 * Math.sin((nx + ny) * 15) + variation(x, y) * 0.10;
  };
  const cellKey = (x, y) => `${x},${y}`;
  const rivers = new Set();
  const channels = Math.max(1, Math.min(4, Math.trunc((width + height) / 16)));
  for (let sector = 0; sector < channels; sector += 1) {
    const from = Math.trunc(width * sector / channels), to = Math.trunc(width * (sector + 1) / channels);
    let headX = -1, headY = -1, highest = -1;
    for (let y = 0; y < height; y += 1) for (let x = from; x < to; x += 1) {
      const elevation = rawElevation(x, y);
      if (elevation > highest && !rivers.has(cellKey(x, y))) { highest = elevation; headX = x; headY = y; }
    }
    if (headX < 0 || highest < 0.68) continue;
    const run = [], walked = new Set(); let x = headX, y = headY, reachedWater = false;
    for (let step = 0; step < width + height; step += 1) {
      if (rawElevation(x, y) < 0.38) { reachedWater = true; break; }
      run.push(cellKey(x, y)); walked.add(cellKey(x, y));
      let nextX = -1, nextY = -1, lowest = Number.MAX_VALUE;
      for (let dy = -1; dy <= 1; dy += 1) for (let dx = -1; dx <= 1; dx += 1) {
        if (!dx && !dy) continue;
        const tx = x + dx, ty = y + dy;
        if (tx < 0 || ty < 0 || tx >= width || ty >= height || walked.has(cellKey(tx, ty)) || rivers.has(cellKey(tx, ty))) continue;
        const elevation = rawElevation(tx, ty);
        if (elevation < lowest) { lowest = elevation; nextX = tx; nextY = ty; }
      }
      if (nextX < 0) break;
      x = nextX; y = nextY;
    }
    if (reachedWater && run.length >= 2) run.forEach(entry => rivers.add(entry));
  }
  const touchesWater = (x, y) => {
    for (let dy = -1; dy <= 1; dy += 1) for (let dx = -1; dx <= 1; dx += 1) {
      if (!dx && !dy) continue;
      const tx = x + dx, ty = y + dy;
      if (tx >= 0 && ty >= 0 && tx < width && ty < height && rawElevation(tx, ty) < 0.30) return true;
    }
    return false;
  };
  const caveMouth = (x, y) => {
    let walkable = false;
    for (let dy = -1; dy <= 1; dy += 1) for (let dx = -1; dx <= 1; dx += 1) {
      if (!dx && !dy) continue;
      const tx = x + dx, ty = y + dy;
      if (tx < 0 || ty < 0 || tx >= width || ty >= height) continue;
      const elevation = rawElevation(tx, ty);
      if (elevation >= 0.30 && elevation <= 0.82) walkable = true;
    }
    let hash = long(seed ^ long(BigInt(x) * 0x100000001B3n) ^ long(BigInt(y) * 0xD6E8FEB86659FD93n));
    hash = long(hash ^ unsignedShift(hash, 29)); hash = long(hash * 0xBF58476D1CE4E5B9n); hash = long(hash ^ unsignedShift(hash, 32));
    return walkable && floorMod(hash, 4n) === 0n;
  };
  const surfaceBiome = (x, y) => {
    const nx = x / Math.max(1, width - 1), ny = y / Math.max(1, height - 1), local = variation(x, y);
    const elevation = 0.49 + 0.22 * Math.sin(nx * 7 + Number(seed) * 0.00000003) + 0.16 * Math.cos(ny * 8) + 0.09 * Math.sin((nx + ny) * 15) + local * 0.10;
    const moisture = 0.54 + 0.20 * Math.cos(nx * 8 - ny * 3) + local * 0.14;
    let biome = elevation < 0.30 ? 'OCEAN' : elevation < 0.38 ? 'WETLAND' : elevation > 0.82 ? 'MOUNTAIN' : elevation > 0.68 ? 'HIGHLAND' : moisture > 0.58 ? 'TEMPERATE_FOREST' : 'GRASSLAND';
    if (['TEMPERATE_FOREST','GRASSLAND','HIGHLAND'].includes(biome) && touchesWater(x, y)) biome = 'COAST';
    if (elevation >= 0.38 && rivers.has(cellKey(x, y))) biome = 'RIVER_BANK';
    if (biome === 'MOUNTAIN' && caveMouth(x, y)) biome = 'CAVE_MOUTH';
    return biome;
  };
  const terrain = (x, y) => {
    const surfaced = surfaceBiome(x, y);
    if (surfaced !== 'MOUNTAIN') return surfaced;
    let mouth = false;
    for (const [dx,dy] of [[0,-1],[0,1],[-1,0],[1,0]]) {
      const tx=x+dx, ty=y+dy; if (tx>=0 && ty>=0 && tx<width && ty<height && surfaceBiome(tx,ty)==='CAVE_MOUTH') mouth=true;
    }
    if (!mouth) return surfaced;
    for (let dy=-1;dy<=1;dy+=1) for (let dx=-1;dx<=1;dx+=1) {
      if (!dx && !dy) continue; const tx=x+dx,ty=y+dy; if (tx<0||ty<0||tx>=width||ty>=height) continue;
      const elevation=rawElevation(tx,ty); if (elevation>=0.30&&elevation<=0.82) return surfaced;
    }
    return 'CAVE_INTERIOR';
  };
  const placed = specs.map((spec, index) => {
    const salt = spec.edge ? 7919 + (index - specs.filter(entry => !entry.edge).length) * 31 : 11 + index * 17;
    const count = width * height;
    const mixed = long(seed ^ long(BigInt(salt) * 0x9E3779B9n));
    const start = Number(floorMod(BigInt.asIntN(32, mixed), BigInt(count)));
    let x = Math.trunc(width / 2), y = Math.trunc(height / 2);
    for (let attempt = 0; attempt < count; attempt += 1) {
      const position = Number(floorMod(BigInt(start + attempt * 37), BigInt(count))), tx = position % width, ty = Math.trunc(position / width);
      if (!spec.accepted.includes(terrain(tx, ty))) continue;
      if (spec.besides && ![[0,-1],[0,1],[-1,0],[1,0]].some(([dx,dy]) => tx+dx>=0&&ty+dy>=0&&tx+dx<width&&ty+dy<height&&spec.besides.includes(terrain(tx+dx,ty+dy)))) continue;
      x = tx; y = ty; break;
    }
    const biome = terrain(x, y);
    return { id:`seed-marker-${index + 1}-${spec.label.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')}`, category:spec.category, label:spec.label, x, y, xPercent:Number((((x + .5) / width) * 100).toFixed(3)), yPercent:Number((((y + .5) / height) * 100).toFixed(3)), biome, accepted:spec.accepted, edge:!!spec.edge };
  });
  const biomes = {};
  for (let y=0;y<height;y+=1) for (let x=0;x<width;x+=1) biomes[terrain(x,y)] = (biomes[terrain(x,y)] || 0) + 1;
  return { seed:Number(seed), widthChunks:width, heightChunks:height, chunkKilometres:10, biomes, markers:placed };
}

const snapshot = {
  generatedAt:new Date().toISOString(), revision, latestMigration, migrationCount:migrationFiles.length,
  counts, livingGroups, backdrop, world,
  sourceCounts:Object.fromEntries([...tables.entries()].map(([name, rows]) => [name, rows.length]).sort(([a],[b]) => a.localeCompare(b))),
  records:deduplicated
};

await writeFile(output, `/* Generated by world-bible/generate-catalogue.mjs from repository-authoritative migrations. */\nwindow.WorldBibleCatalogue = ${JSON.stringify(snapshot)};\n`, 'utf8');
console.log(JSON.stringify({ output, records:deduplicated.length, counts, livingGroups, latestMigration, backdrop }, null, 2));
