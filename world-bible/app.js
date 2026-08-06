(() => {
  const bible = window.WorldBible;
  const content = document.querySelector('#page-content');
  const page = document.body.dataset.page;
  const nav = document.querySelector('#site-nav');
  const navItems = [['index.html','Home'],['atlas.html','Atlas'],['catalogue.html','Catalogue'],['industries.html','Industries'],['rules.html','World Rules']];
  nav.innerHTML = navItems.map(([href,label]) => `<a class="${location.pathname.endsWith(href) || (href === 'index.html' && location.pathname.endsWith('/world-bible/')) ? 'active' : ''}" href="${href}">${label}</a>`).join('');
  const badge = status => `<span class="badge ${status.toLowerCase().replaceAll(' ','-').replaceAll('/','-')}">${status}</span>`;
  const linkEntry = entry => `<a class="catalogue-card" href="entry.html#${entry.id}"><span class="card-icon">${entry.icon}</span><span class="card-body">${badge(entry.status)}<strong>${entry.name}</strong><small>${entry.type} · ${entry.habitat}</small></span><span class="arrow">↗</span></a>`;
  const section = (eyebrow, title, copy, body) => `<section class="section"><p class="eyebrow">${eyebrow}</p><h2>${title}</h2>${copy ? `<p class="section-copy">${copy}</p>` : ''}${body}</section>`;

  function home() {
    content.innerHTML = `<section class="hero"><div class="hero-copy"><p class="eyebrow">Creator reference · canonical world knowledge</p><h1>THE WORLD<br><em>BEFORE</em> THE CHRONICLE</h1><p>The Overseer World Bible records what exists, what may exist, and what remains only a blueprint. It is a living reference for a persistent world—not a player-facing map.</p><div class="hero-actions"><a class="button gold" href="atlas.html">Enter the Atlas</a><a class="button ghost" href="catalogue.html">Browse Catalogue</a></div></div><aside class="seed-card"><span>Canonical seed</span><strong>${bible.seed}</strong><dl><div><dt>Atlas extent</dt><dd>280 × 200 km</dd></div><div><dt>Strategic cell</dt><dd>10 km region</dd></div><div><dt>Human civilization</dt><dd>None</dd></div><div><dt>World memory</dt><dd>Persistent</dd></div></dl></aside></section>${section('World state', 'A world with consequences', 'The page is deliberately honest about implementation readiness.', `<div class="state-grid"><article><b>${bible.biomes.length}</b><span>seed-supported macro-biomes</span></article><article><b>${bible.entries.filter(e=>e.status==='Seed-supported').length}</b><span>reference entries grounded now</span></article><article><b>${bible.atlasZones.length}</b><span>canonical placement zones</span></article><article><b>0</b><span>active human civilizations</span></article></div>`)}${section('Reference paths', 'Read the world by its own categories', '', `<div class="path-grid"><a href="atlas.html"><span>🗺️</span><strong>Atlas & ecology</strong><small>Canonical placement zones, marker keys and activation gates.</small></a><a href="catalogue.html"><span>🦫</span><strong>Living catalogue</strong><small>Flora, wildlife, monsters, native peoples and materials.</small></a><a href="industries.html"><span>⚒️</span><strong>Human impact</strong><small>Handwork, agriculture, textiles, forestry and industry.</small></a><a href="rules.html"><span>◈</span><strong>World laws</strong><small>Identity, history, consequence and persistence.</small></a></div>`)}`;
  }

  function atlas() {
    const zones = bible.atlasZones.map(zone => `<button class="atlas-pin ${zone.kind}" style="--x:${zone.x}%;--y:${zone.y}%" data-zone="${zone.id}" aria-label="Open ${zone.title}"><span>${bible.atlasLegend.find(item=>item[0]===zone.kind)[1]}</span></button>`).join('');
    const legend = bible.atlasLegend.map(([kind, symbol, label]) => `<button data-kind="${kind}"><b class="legend-symbol ${kind}">${symbol}</b>${label}</button>`).join('');
    const zoneCards = bible.atlasZones.map(zone => `<article class="zone-card ${zone.kind}" id="zone-${zone.id}"><div><span class="zone-symbol">${bible.atlasLegend.find(item=>item[0]===zone.kind)[1]}</span>${badge(zone.state)}</div><h3>${zone.title}</h3><p class="zone-tags">${zone.tags}</p><p>${zone.scope}</p><p><strong>Placement role:</strong> ${zone.support}</p></article>`).join('');
    const biomeCards = bible.biomes.map(b => `<article class="biome-card"><span>${b.icon}</span><div>${badge(b.status)}<h3>${b.name}</h3><code>${b.key}</code><p>${b.detail}</p></div></article>`).join('');
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">Canonical placement ledger · creator-only knowledge</p><h1>OVERSEER ATLAS</h1><p>This is the spatial reference for the world seed. It places ecology, materials, wildlife, threats, native-society candidates and future industrial pressure on the real atlas image. A marker defines a zone and its activation gate; it does not mean the runtime already contains every dependent feature.</p></section><section class="atlas-ledger"><div class="atlas-stage"><img src="assets/overseer-atlas-v1.png" alt="Illustrated Project Draugr creator atlas with canonical placement markers" /><div class="atlas-pins">${zones}</div><div class="atlas-compass" aria-hidden="true"><i>▲</i><span>N</span></div></div><aside class="atlas-side"><p class="eyebrow">Marker keys</p><h2>Read by consequence</h2><p>Toggle a marker family, then select a pin or a ledger entry. Pale/dashed symbols are placement reservations whose runtime activation is still gated.</p><div class="atlas-legend">${legend}</div><div class="atlas-selection" id="atlas-selection"><p class="eyebrow">Selected zone</p><h3>Select a marker</h3><p>Every zone lists exactly what it can support and the GitHub requirements that must be satisfied before planned content activates.</p></div></aside></section>${section('Placement ledger', 'Every future system has a home', 'The ledger is intentionally more specific than the simulation today. It gives implementation work a spatial contract while keeping inactive content visibly inactive.', `<div class="zone-grid" id="zone-grid">${zoneCards}</div>`)}${section('Macro-biomes','The land that already exists','These are seed-supported macro-biomes; topology and population layers still determine the exact local possibilities.', `<div class="biome-grid">${biomeCards}</div>`)}`;
    const selection = document.querySelector('#atlas-selection');
    const showZone = id => {
      const zone = bible.atlasZones.find(entry => entry.id === id);
      selection.innerHTML = `<p class="eyebrow">${zone.tags}</p>${badge(zone.state)}<h3>${zone.title}</h3><p>${zone.scope}</p><p><strong>Supports:</strong> ${zone.support}</p>`;
      document.querySelectorAll('.atlas-pin').forEach(pin => pin.classList.toggle('selected', pin.dataset.zone === id));
      document.querySelectorAll('.zone-card').forEach(card => card.classList.toggle('selected', card.id === `zone-${id}`));
      document.querySelector(`#zone-${id}`).scrollIntoView({behavior:'smooth', block:'nearest'});
    };
    document.querySelector('.atlas-pins').addEventListener('click', event => { const pin = event.target.closest('.atlas-pin'); if (pin) showZone(pin.dataset.zone); });
    document.querySelector('#zone-grid').addEventListener('click', event => { const card = event.target.closest('.zone-card'); if (card) showZone(card.id.slice(5)); });
    document.querySelector('.atlas-legend').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; const kind = button.dataset.kind; button.classList.toggle('muted'); document.querySelectorAll(`.atlas-pin.${kind}, .zone-card.${kind}`).forEach(entry => entry.classList.toggle('filtered', button.classList.contains('muted'))); });
  }

  function catalogue() {
    const realms = { rabbit:'terrestrial', 'red-squirrel':'terrestrial', beaver:'aquatic', hare:'terrestrial', 'wild-boar':'terrestrial', 'river-trout':'aquatic', 'dire-wolf':'terrestrial', 'bog-wraith':'aquatic', wyvern:'aerial', 'goblin-band':'social', 'centaur-herd':'social', reedkin:'social', oak:'flora', nettle:'flora', cattail:'flora', blackberry:'flora', porcini:'flora', flint:'material', clay:'material', copper:'material', iron:'material', gold:'material' };
    const worldFilters = [['All','All living & material'],['terrestrial','Terrestrial'],['aquatic','Aquatic / amphibious'],['aerial','Aerial'],['subterranean','Subterranean'],['social','Native peoples'],['flora','Flora'],['material','Materials']];
    const tabButtons = [['world','Living world'], ...bible.recipeTabs.map(tab => [tab.id,tab.label])];
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">No reward tables · only physical consequence</p><h1>WORLD CATALOGUE</h1><p>Browse the living world by ecology, or open a practical record for crafting, storage, infrastructure, construction, fieldcraft and industry. Every procedure record identifies its sources, inputs, process, output, applications and implementation state.</p></section><div class="catalogue-tabs" id="catalogue-tabs">${tabButtons.map(([id,label], index) => `<button data-tab="${id}" class="${index===0?'selected':''}">${label}</button>`).join('')}</div><section id="catalogue-view"></section>`;
    const view = document.querySelector('#catalogue-view');
    const showWorld = () => {
      view.innerHTML = `<section class="catalogue-toolbar"><label>Search <input id="search" placeholder="Rabbit, flint, wetland…" /></label><div id="filters">${worldFilters.map(([id,label],index)=>`<button class="${index===0?'selected':''}" data-filter="${id}">${label}</button>`).join('')}</div></section><section class="database-note"><span>Creator database</span><p>Every record describes what exists in the world, where it belongs, and what a Chronicle can physically gain, lose, or disturb.</p></section><section class="catalogue-grid" id="catalogue-grid"></section>`;
      let active = 'All'; const grid = document.querySelector('#catalogue-grid'); const search = document.querySelector('#search');
      const render = () => { const term = search.value.toLowerCase(); grid.innerHTML = bible.entries.filter(entry => (active==='All' || realms[entry.id] === active) && `${entry.name} ${entry.type} ${entry.habitat} ${entry.status}`.toLowerCase().includes(term)).map(entry => linkEntry({...entry, type: `${entry.type} · ${realms[entry.id] || 'world'}`})).join('') || `<p class="empty">No world-bible entry matches that search.</p>`; };
      document.querySelector('#filters').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; active = button.dataset.filter; document.querySelectorAll('#filters button').forEach(item => item.classList.toggle('selected', item === button)); render(); });
      search.addEventListener('input',render); render();
    };
    const showRecipes = tabId => {
      const tab = bible.recipeTabs.find(item => item.id === tabId);
      const records = bible.recipeRecords.filter(item => item.tab === tabId);
      view.innerHTML = `<section class="recipe-intro"><p class="eyebrow">${tab.label}</p><h2>${tab.description}</h2><p>Quantities are shown only when the World Bible has a concrete design value. <b>Quantity specification pending</b> keeps uncertain parts visibly incomplete instead of pretending a recipe is ready.</p></section><section class="recipe-grid">${records.map(record => recipeCard(record)).join('') || `<p class="empty">No records have been written for this category yet.</p>`}</section>`;
    };
    const activate = tabId => { document.querySelectorAll('#catalogue-tabs button').forEach(button => button.classList.toggle('selected', button.dataset.tab === tabId)); tabId === 'world' ? showWorld() : showRecipes(tabId); };
    document.querySelector('#catalogue-tabs').addEventListener('click', event => { const button = event.target.closest('button'); if (button) activate(button.dataset.tab); });
    activate('world');
  }

  function recipeCard(record) {
    const list = (title, values) => `<div><h4>${title}</h4><ul>${values.map(value => `<li>${value}</li>`).join('')}</ul></div>`;
    return `<article class="recipe-card"><header>${badge(record.state)}<h3>${record.name}</h3><p>${record.sources}</p></header><div class="recipe-chain">${list('Inputs',record.inputs)}${list('Tools & conditions',record.tools)}<div><h4>Site requirement</h4><p>${record.site}</p></div><div><h4>Procedure</h4><ol>${record.process.map(step => `<li>${step}</li>`).join('')}</ol></div><div><h4>Output</h4><p>${record.output}</p></div><div><h4>Applications</h4><p>${record.applications}</p></div></div></article>`;
  }

  async function loadIssueRegister() {
    const register = document.querySelector('#issue-register');
    const issueApi = pageNo => `https://api.github.com/repos/devosphere/projectdraugr/issues?state=open&per_page=100&page=${pageNo}`;
    const classify = issue => {
      const title = issue.title.toLowerCase();
      if (/wildlife|avian|hunting|taming|husbandry|animal/.test(title)) return 'Wildlife & husbandry';
      if (/monster|native|creature/.test(title)) return 'Monsters & native peoples';
      if (/flora|forest|agriculture|seed|crop/.test(title)) return 'Flora, forestry & agriculture';
      if (/textile|fibre|leather|equipment|armour/.test(title)) return 'Textiles & equipment';
      if (/material|clay|industry|geolog|ore|metal/.test(title)) return 'Materials & industry';
      if (/tool|weapon|portable|storage|logistics|handwork/.test(title)) return 'Tools, storage & logistics';
      if (/construction|shelter|camp|structure|infrastructure/.test(title)) return 'Structures & settlement';
      return 'Procedures & world systems';
    };
    try {
      const response = await Promise.all([fetch(issueApi(1)), fetch(issueApi(2))]);
      if (!response.every(result => result.ok)) throw new Error('GitHub register unavailable');
      const issues = (await Promise.all(response.map(result => result.json()))).flat().filter(issue => issue.number >= 45 && issue.number <= 221 && !issue.pull_request);
      const groups = new Map();
      issues.forEach(issue => {
        const names = [...new Set([...((issue.body || '').matchAll(/`([a-z][a-z0-9_]{2,})`/g))].map(match => match[1]))];
        if (!names.length) return;
        const group = classify(issue);
        groups.set(group, [...(groups.get(group) || []), { number: issue.number, title: issue.title, names, body: issue.body || '' }]);
      });
      const total = [...groups.values()].flatMap(group => group.flatMap(issue => issue.names)).length;
      const safe = value => value.replace(/[&<>'"]/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[character]));
      register.innerHTML = `<div class="register-summary"><b>${total}</b><span>named requirement candidates across ${issues.length} open specification tickets</span><a href="https://github.com/devosphere/projectdraugr/issues?q=is%3Aissue%20is%3Aopen%20label%3Aneeds-specification" target="_blank" rel="noreferrer">Open issue register ↗</a></div><aside class="register-inspector" id="register-inspector"><p class="eyebrow">Specification inspector</p><h3>Select a named candidate</h3><p>Its owning requirement, exact source text and applicable procedure/material clauses will appear here.</p></aside>${[...groups.entries()].map(([group, ownerIssues]) => `<details class="register-group" open><summary><span>${group}</span><b>${ownerIssues.reduce((count, issue) => count + issue.names.length, 0)} names</b></summary><div>${ownerIssues.map(issue => `<article><a href="https://github.com/devosphere/projectdraugr/issues/${issue.number}" target="_blank" rel="noreferrer">#${issue.number} ${safe(issue.title.replace(/^\[[^\]]+\]\s*/, ''))}</a><p>${issue.names.map(name => `<button class="register-token" data-issue="${issue.number}" data-name="${name}">${safe(name.replaceAll('_',' '))}</button>`).join('')}</p></article>`).join('')}</div></details>`).join('')}`;
      register.addEventListener('click', event => {
        const token = event.target.closest('.register-token');
        if (!token) return;
        const source = issues.find(issue => issue.number === Number(token.dataset.issue));
        const literal = `\`${token.dataset.name}\``;
        const lines = (source.body || '').split('\n');
        const matching = lines.filter(line => line.includes(literal)).map(line => line.replace(/^[-*]\s*/, '').trim()).slice(0, 8);
        const sections = [...(source.body || '').matchAll(/^#{2,3}\s+(.+)\n([\s\S]*?)(?=^#{2,3}\s+|(?![\s\S]))/gm)]
          .filter(match => /source|material|ingredient|procedure|method|process|harvest|loot|application|acceptance|behaviour|site|habitat/i.test(match[1]))
          .slice(0, 4)
          .map(match => `<details><summary>${safe(match[1])}</summary><pre>${safe(match[2].trim())}</pre></details>`).join('');
        document.querySelector('#register-inspector').innerHTML = `<p class="eyebrow">Requirement #${source.number}</p><h3>${safe(token.dataset.name.replaceAll('_',' '))}</h3><p><a href="https://github.com/devosphere/projectdraugr/issues/${source.number}" target="_blank" rel="noreferrer">${safe(source.title)} ↗</a></p><h4>Exact catalogue context</h4><ul>${matching.map(line => `<li>${safe(line)}</li>`).join('') || '<li>The owning issue references this candidate in a broader specification block.</li>'}</ul><h4>Methods, materials & acceptance clauses</h4>${sections || '<p>This candidate needs a task-level source/process record before exact quantities can be approved.</p>'}`;
        document.querySelectorAll('.register-token').forEach(button => button.classList.toggle('selected', button === token));
      });
    } catch (error) {
      register.innerHTML = `<p class="register-error">The live issue register could not be reached. The curated cards above remain available; use the project’s <a href="https://github.com/devosphere/projectdraugr/issues" target="_blank" rel="noreferrer">open specification issues ↗</a> for the complete source list.</p>`;
    }
  }

  function industries() {
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">Civilization changes the land</p><h1>PHYSICAL SECTORS</h1><p>Each sector is a practical system: where it belongs, the methods it permits, the useful results it can make, and the consequences it leaves in the world.</p></section><section class="industry-list">${bible.industries.map(i=>`<article><span class="industry-icon">${i.icon}</span><div>${badge(i.status)}<h2>${i.name}</h2><p class="chain">${i.chain}</p><p class="industry-zone"><strong>Primary zones:</strong> ${i.zones}</p><div class="industry-details"><section><h3>Methods</h3><ul>${i.methods.map(method=>`<li>${method}</li>`).join('')}</ul></section><section><h3>Applications</h3><ul>${i.applications.map(application=>`<li>${application}</li>`).join('')}</ul></section></div><p>${i.rule}</p></div></article>`).join('')}</section>${section('Consequence contract','No industry is isolated','Work affects actor physiology, land, water, air, wildlife, monsters, native peoples, property, routes and future maintenance.', `<div class="impact-line"><span>Effort</span><b>→</b><span>Footprint</span><b>→</b><span>Response</span><b>→</b><span>Recovery</span></div>`)}`;
  }

  function rules() {
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">The Overseer’s contract</p><h1>WORLD LAWS</h1><p>These are not flavour text. They are the rules every future catalogue entry, industry and simulation feature must satisfy.</p></section><section class="rule-list">${bible.rules.map(([title,copy],index)=>`<article><span>${String(index+1).padStart(2,'0')}</span><div><h2>${title}</h2><p>${copy}</p></div></article>`).join('')}</section>${section('Activity impact','What every meaningful procedure must leave behind','A procedure is only complete when it has actor cost, physical inputs, footprint, output/waste, affected beings, time/neglect consequences and recovery paths.', `<div class="impact-line"><span>Actor</span><b>→</b><span>Inputs</span><b>→</b><span>Footprint</span><b>→</b><span>World response</span><b>→</b><span>History</span></div>`)}`;
  }

  function entry() {
    const id = location.hash.slice(1); const e = bible.entries.find(x=>x.id===id) || bible.entries[0]; document.title = `${e.name} — Project Draugr World Bible`;
    content.innerHTML = `<a class="back-link" href="catalogue.html">← Back to catalogue</a><section class="entry-hero"><div class="entry-icon">${e.icon}</div><div><p class="eyebrow">${e.type} · ${e.latin}</p>${badge(e.status)}<h1>${e.name}</h1><p>${e.habitat}</p></div></section><section class="entry-layout"><article class="entry-main"><div><h2>Behaviour & presence</h2><p>${e.behaviour}</p></div><div><h2>Ecological role</h2><p>${e.role}</p></div><div><h2>Physical recovery / harvest</h2><p>${e.recovery}</p></div><div><h2>Risk & consequence</h2><p>${e.danger}</p></div></article><aside class="entry-meta"><p class="eyebrow">Classification</p><dl><div><dt>Type</dt><dd>${e.type}</dd></div><div><dt>Status</dt><dd>${e.status}</dd></div><div><dt>Habitat</dt><dd>${e.habitat}</dd></div><div><dt>Related work</dt><dd>${e.related}</dd></div></dl><p class="muted">Catalogue entries describe physical possibilities. They do not grant the Chronicle knowledge, ownership, access or safety.</p></aside></section>`;
  }
  ({home,atlas,catalogue,industries,rules,entry}[page] || home)();
})();
