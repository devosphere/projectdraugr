(() => {
  const bible = window.WorldBible;
  const catalogueData = window.WorldBibleCatalogue;
  const content = document.querySelector('#page-content');
  const page = document.body.dataset.page;
  const nav = document.querySelector('#site-nav');
  const tabs = [
    ['living','Living world'], ['materials','Materials'], ['actions','Actions'],
    ['crafting','Crafting & processing'], ['storage','Storage & logistics'],
    ['infrastructure','Infrastructure'], ['construction','Construction & camp'],
    ['fieldcraft','Hunting & husbandry'], ['industry','Industry']
  ];
  const tabNames = Object.fromEntries(tabs);
  const navItems = [['index.html','Home'],['atlas.html','Atlas'],['catalogue.html','Catalogue'],['industries.html','Industries'],['rules.html','World Rules']];
  nav.innerHTML = navItems.map(([href,label]) => `<a class="${location.pathname.endsWith(href) || (href === 'index.html' && location.pathname.endsWith('/world-bible/')) ? 'active' : ''}" href="${href}">${label}</a>`).join('');

  const safe = value => String(value ?? '').replace(/[&<>"']/g, character => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[character]));
  const badge = status => `<span class="badge ${String(status).toLowerCase().replace(/[^a-z0-9]+/g,'-')}">${safe(status)}</span>`;
  const section = (eyebrow, title, copy, body) => `<section class="section"><p class="eyebrow">${eyebrow}</p><h2>${title}</h2>${copy ? `<p class="section-copy">${copy}</p>` : ''}${body}</section>`;
  const count = category => catalogueData.counts[category] || 0;
  const snapshotLabel = `${catalogueData.records.length.toLocaleString()} records · ${catalogueData.latestMigration} · revision ${catalogueData.revision}`;

  function home() {
    content.innerHTML = `<section class="hero"><div class="hero-copy"><p class="eyebrow">Creator reference · generated from the current game catalogue</p><h1>THE WORLD<br><em>BEFORE</em> THE CHRONICLE</h1><p>The Overseer World Bible now reads the implemented materials, procedures, organisms, constructions, equipment, sources, and physical relationships recorded by the game. It is a static creator reference, so it remains fast and does not need the backend or GitHub to be available.</p><div class="hero-actions"><a class="button gold" href="atlas.html">Enter the Atlas</a><a class="button ghost" href="catalogue.html">Browse Catalogue</a></div></div><aside class="seed-card"><span>Canonical seed</span><strong>${bible.seed}</strong><dl><div><dt>Catalogue revision</dt><dd>${safe(catalogueData.latestMigration)}</dd></div><div><dt>World records</dt><dd>${catalogueData.records.length.toLocaleString()}</dd></div><div><dt>Backdrop registry</dt><dd>${catalogueData.backdrop.active} active / ${catalogueData.backdrop.total}</dd></div><div><dt>World memory</dt><dd>Persistent</dd></div></dl></aside></section>${section('Current repository snapshot', 'The current foundation is now visible', snapshotLabel, `<div class="state-grid"><article><b>${count('living')}</b><span>living-world records</span></article><article><b>${count('materials')}</b><span>materials and physical stock</span></article><article><b>${count('crafting') + count('industry') + count('fieldcraft')}</b><span>craft, industry and field procedures</span></article><article><b>${count('construction') + count('infrastructure')}</b><span>construction and infrastructure records</span></article></div>`)}${section('Reference paths', 'Read the world by its own categories', '', `<div class="path-grid"><a href="atlas.html"><span>🗺️</span><strong>Atlas & ecology</strong><small>Canonical placement zones, marker keys, and world topology.</small></a><a href="catalogue.html"><span>🦫</span><strong>Complete catalogue</strong><small>Implemented flora, wildlife, monsters, materials, objects, and procedures.</small></a><a href="industries.html"><span>⚒️</span><strong>Human impact</strong><small>Handwork, agriculture, textiles, forestry, and industry.</small></a><a href="rules.html"><span>◈</span><strong>World laws</strong><small>Identity, history, consequence, and persistence.</small></a></div>`)}`;
  }

  function atlas() {
    const kindFor = category => ({ RESOURCE:'material', WILDLIFE:'wildlife', MONSTER:'monster', RUIN:'ruin' }[category] || 'ecology');
    const atlasZones = (catalogueData.world?.markers || []).map(marker => ({
      id:marker.id, x:marker.xPercent, y:marker.yPercent, kind:kindFor(marker.category), title:marker.label,
      state:'Seeded site', tags:`${marker.category} · ${marker.biome.replaceAll('_',' ')}`,
      scope:`Canonical ${marker.category.toLowerCase()} site at chunk ${marker.x},${marker.y} (${marker.x * catalogueData.world.chunkKilometres}–${(marker.x + 1) * catalogueData.world.chunkKilometres} km east; ${marker.y * catalogueData.world.chunkKilometres}–${(marker.y + 1) * catalogueData.world.chunkKilometres} km south).`,
      support:`Placed by WorldGenesisService on ${marker.biome.replaceAll('_',' ')}. Accepted terrain: ${marker.accepted.join(', ').replaceAll('_',' ')}${marker.edge ? '; adjacent-biome edge condition enforced' : ''}.`
    }));
    const symbol = kind => bible.atlasLegend.find(item => item[0] === kind)?.[1] || '◆';
    const pins = atlasZones.map(zone => `<button class="atlas-pin ${zone.kind}" style="--x:${zone.x}%;--y:${zone.y}%" data-zone="${zone.id}" aria-label="Open ${safe(zone.title)}"><span>${symbol(zone.kind)}</span></button>`).join('');
    const presentKinds = new Set(atlasZones.map(zone => zone.kind));
    const legend = bible.atlasLegend.filter(([kind]) => presentKinds.has(kind)).map(([kind, marker, label]) => `<button data-kind="${kind}"><b class="legend-symbol ${kind}">${marker}</b>${label}</button>`).join('');
    const zoneCards = atlasZones.map(zone => `<article class="zone-card ${zone.kind}" id="zone-${zone.id}" tabindex="0"><div><span class="zone-symbol">${symbol(zone.kind)}</span>${badge(zone.state)}</div><h3>${safe(zone.title)}</h3><p class="zone-tags">${safe(zone.tags)}</p><p>${safe(zone.scope)}</p><p><strong>Placement role:</strong> ${safe(zone.support)}</p></article>`).join('');
    const biomeMeta = Object.fromEntries(bible.biomes.map(biome => [biome.key, biome]));
    const biomeCards = Object.entries(catalogueData.world?.biomes || {}).sort(([left],[right]) => left.localeCompare(right)).map(([key,total]) => {
      const biome = biomeMeta[key] || { icon:'◆', name:key.replaceAll('_',' '), detail:'Generated terrain class used by world ecology and simulation.' };
      return `<article class="biome-card"><span>${biome.icon}</span><div>${badge('Generated')}<h3>${safe(biome.name)}</h3><code>${safe(key)}</code><p>${total} of ${catalogueData.world.widthChunks * catalogueData.world.heightChunks} canonical chunks. ${safe(biome.detail)}</p></div></article>`;
    }).join('');
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">Canonical placement ledger · generated from backend world genesis</p><h1>OVERSEER ATLAS</h1><p>This atlas reproduces the current ${catalogueData.world.widthChunks} × ${catalogueData.world.heightChunks}-chunk placement plan for world seed ${bible.seed}. Every pin is generated from the same biome rules, marker order, accepted terrain, and placement salt used by WorldGenesisService.</p></section><section class="snapshot-strip"><span>Current implementation</span><strong>${atlasZones.length} canonical sites · ${Object.keys(catalogueData.world.biomes).length} generated terrain classes</strong><small>${catalogueData.backdrop.active} active visual contexts from ${catalogueData.backdrop.total} registered backdrops · ${snapshotLabel}</small></section><section class="atlas-ledger"><div class="atlas-stage"><img src="assets/overseer-atlas-v1.png" alt="Illustrated Project Draugr creator atlas with canonical placement markers" /><div class="atlas-pins">${pins}</div><div class="atlas-compass" aria-hidden="true"><i>▲</i><span>N</span></div></div><aside class="atlas-side"><p class="eyebrow">Marker keys</p><h2>Read by consequence</h2><p>Toggle a marker family, then select a pin or ledger entry. Resource, wildlife, monster, and ruin sites shown here are the exact deterministic genesis plan; native communities are seeded separately from viable terrain and remain catalogued under Living World.</p><div class="atlas-legend">${legend}</div><div class="atlas-selection" id="atlas-selection"><p class="eyebrow">Selected site</p><h3>Select a marker</h3><p>The selected site will show its exact chunk, generated biome, and placement constraints.</p></div></aside></section>${section('Placement ledger', 'Every genesis site has physical ground', 'These are the backend marker specifications placed on the canonical seed—not planning cards or issue placeholders.', `<div class="zone-grid" id="zone-grid">${zoneCards}</div>`)}${section('Generated terrain','The land beneath every local site','Counts are recomputed from the same canonical 28 × 20 seed algorithm used at genesis.', `<div class="biome-grid">${biomeCards}</div>`)}`;
    const selection = document.querySelector('#atlas-selection');
    const showZone = id => {
      const zone = atlasZones.find(entry => entry.id === id);
      if (!zone) return;
      selection.innerHTML = `<p class="eyebrow">${safe(zone.tags)}</p>${badge(zone.state)}<h3>${safe(zone.title)}</h3><p>${safe(zone.scope)}</p><p><strong>Supports:</strong> ${safe(zone.support)}</p>`;
      document.querySelectorAll('.atlas-pin').forEach(pin => pin.classList.toggle('selected', pin.dataset.zone === id));
      document.querySelectorAll('.zone-card').forEach(card => card.classList.toggle('selected', card.id === `zone-${id}`));
    };
    document.querySelector('.atlas-pins').addEventListener('click', event => { const pin = event.target.closest('.atlas-pin'); if (pin) showZone(pin.dataset.zone); });
    document.querySelector('#zone-grid').addEventListener('click', event => { const card = event.target.closest('.zone-card'); if (card) showZone(card.id.slice(5)); });
    document.querySelector('.atlas-legend').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; button.classList.toggle('muted'); document.querySelectorAll(`.atlas-pin.${button.dataset.kind}, .zone-card.${button.dataset.kind}`).forEach(entry => entry.classList.toggle('filtered', button.classList.contains('muted'))); });
  }

  function recordCard(record) {
    return `<a class="catalogue-card" href="entry.html#${encodeURIComponent(record.id)}"><span class="card-icon">${record.icon}</span><span class="card-body">${badge(record.status)}<strong>${safe(record.name)}</strong><small>${safe(record.detail)}</small></span><span class="arrow">↗</span></a>`;
  }

  function catalogue() {
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">Repository-authoritative · no live API dependency</p><h1>WORLD CATALOGUE</h1><p>Every card below comes from the current migration-backed catalogue. Open an entry to see its physical properties, habitat, harvest, ingredients, tools, procedure, output, equipment compatibility, or construction stages.</p></section><section class="snapshot-strip"><span>Compiled snapshot</span><strong>${snapshotLabel}</strong><small>${catalogueData.migrationCount} migration files · ${catalogueData.backdrop.active} active backdrops</small></section><div class="catalogue-tabs" id="catalogue-tabs">${tabs.map(([id,label], index) => `<button data-tab="${id}" class="${index === 0 ? 'selected' : ''}">${label}<b>${count(id).toLocaleString()}</b></button>`).join('')}</div><section id="catalogue-view"></section>`;
    const view = document.querySelector('#catalogue-view');
    let activeTab = 'living', activeFilter = 'all', term = '', pageIndex = 0;
    const pageSize = 48;

    const filtersFor = category => {
      const source = catalogueData.records.filter(record => record.category === category);
      if (category === 'living') {
        const labels = {
          'wildlife-terrestrial':'Wildlife · terrestrial','wildlife-aquatic':'Wildlife · aquatic / amphibious','wildlife-aerial':'Wildlife · aerial','wildlife-subterranean':'Wildlife · subterranean',
          'monster-terrestrial':'Monsters · terrestrial','monster-aquatic':'Monsters · aquatic / amphibious','monster-aerial':'Monsters · aerial','monster-subterranean':'Monsters · subterranean',
          'native-terrestrial':'Native peoples · terrestrial','native-aquatic':'Native peoples · aquatic / amphibious','native-aerial':'Native peoples · aerial','native-subterranean':'Native peoples · subterranean','flora':'Flora'
        };
        return [...new Set(source.map(record => record.lifeGroup).filter(Boolean))].sort().map(id => [id, labels[id] || id.replaceAll('-',' ')]);
      }
      return [...new Set(source.map(record => record.subcategory).filter(Boolean))].sort().map(label => [label, label]);
    };

    const render = () => {
      const filters = filtersFor(activeTab);
      if (activeFilter !== 'all' && !filters.some(([id]) => id === activeFilter)) activeFilter = 'all';
      const matches = catalogueData.records.filter(record => record.category === activeTab)
        .filter(record => activeFilter === 'all' || (activeTab === 'living' ? record.lifeGroup === activeFilter : record.subcategory === activeFilter))
        .filter(record => record.searchable.toLowerCase().includes(term));
      const pages = Math.max(1, Math.ceil(matches.length / pageSize));
      pageIndex = Math.min(pageIndex, pages - 1);
      const visible = matches.slice(pageIndex * pageSize, pageIndex * pageSize + pageSize);
      view.innerHTML = `<section class="catalogue-toolbar"><label>Search <input id="search" value="${safe(term)}" placeholder="Search ${safe(tabNames[activeTab].toLowerCase())}…" /></label><div id="filters"><button class="${activeFilter === 'all' ? 'selected' : ''}" data-filter="all">All ${safe(tabNames[activeTab])}</button>${filters.map(([id,label]) => `<button class="${activeFilter === id ? 'selected' : ''}" data-filter="${safe(id)}">${safe(label)}</button>`).join('')}</div></section><section class="database-note"><span>${safe(tabNames[activeTab])}</span><p>${matches.length.toLocaleString()} matching records. Select a card for the exact persisted definition and its linked world relationships.</p></section><div class="catalogue-summary"><span>${matches.length.toLocaleString()} records</span><span>Page ${pageIndex + 1} of ${pages}</span></div><section class="catalogue-grid" id="catalogue-grid">${visible.map(recordCard).join('') || '<p class="empty">No catalogue record matches this search.</p>'}</section><div class="complete-pagination"><button data-page="previous" ${pageIndex === 0 ? 'disabled' : ''}>Previous</button><button data-page="next" ${pageIndex >= pages - 1 ? 'disabled' : ''}>Next</button></div>`;
      const search = document.querySelector('#search');
      search.addEventListener('input', event => { term = event.target.value.toLowerCase(); pageIndex = 0; render(); requestAnimationFrame(() => { const next = document.querySelector('#search'); next.focus(); next.setSelectionRange(next.value.length, next.value.length); }); });
      document.querySelector('#filters').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; activeFilter = button.dataset.filter; pageIndex = 0; render(); });
      document.querySelector('.complete-pagination').addEventListener('click', event => { const button = event.target.closest('button'); if (!button || button.disabled) return; pageIndex += button.dataset.page === 'next' ? 1 : -1; render(); document.querySelector('#catalogue-tabs').scrollIntoView({ behavior:'smooth', block:'start' }); });
    };
    document.querySelector('#catalogue-tabs').addEventListener('click', event => { const button = event.target.closest('button'); if (!button) return; activeTab = button.dataset.tab; activeFilter = 'all'; term = ''; pageIndex = 0; document.querySelectorAll('#catalogue-tabs button').forEach(item => item.classList.toggle('selected', item === button)); render(); });
    render();
  }

  function industries() {
    const records = catalogueData.records.filter(record => record.category === 'industry');
    const domains = [...records.reduce((groups, record) => groups.set(record.subcategory, [...(groups.get(record.subcategory) || []), record]), new Map()).entries()].sort((left, right) => right[1].length - left[1].length || left[0].localeCompare(right[0]));
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">Civilization changes the land</p><h1>PHYSICAL SECTORS</h1><p>This page is built from the current industrial procedure catalogue. Every listed method has a repository-backed record with its actual inputs, conditions, duration, and result.</p></section><section class="snapshot-strip"><span>Current industrial catalogue</span><strong>${records.length.toLocaleString()} implemented records across ${domains.length} domains</strong><small>Generated from ${catalogueData.latestMigration}</small></section><section class="industry-list">${domains.map(([domain, domainRecords]) => `<article><span class="industry-icon">⚙</span><div>${badge(`${domainRecords.length} records`)}<h2>${safe(domain)}</h2><p class="chain">${domainRecords.slice(0,8).map(record => safe(record.name)).join(' → ')}</p><div class="industry-details"><section><h3>Implemented methods</h3><ul>${domainRecords.slice(0,12).map(record => `<li><a href="entry.html#${encodeURIComponent(record.id)}">${safe(record.name)}</a></li>`).join('')}</ul></section><section><h3>Physical outputs</h3><ul>${domainRecords.slice(0,12).map(record => `<li>${safe(record.detail)}</li>`).join('')}</ul></section></div>${domainRecords.length > 12 ? `<p>${(domainRecords.length - 12).toLocaleString()} additional records are available in the Industry catalogue tab.</p>` : ''}</div></article>`).join('')}</section>${section('Consequence contract','No industry is isolated','Work affects actor physiology, land, water, air, wildlife, monsters, native peoples, property, routes, and future maintenance.', `<div class="impact-line"><span>Effort</span><b>→</b><span>Footprint</span><b>→</b><span>Response</span><b>→</b><span>Recovery</span></div>`)}`;
  }

  function rules() {
    content.innerHTML = `<section class="page-hero compact"><p class="eyebrow">The Overseer’s contract</p><h1>WORLD LAWS</h1><p>These rules govern every catalogue entry, industry, and simulation feature.</p></section><section class="rule-list">${bible.rules.map(([title,copy],index) => `<article><span>${String(index + 1).padStart(2,'0')}</span><div><h2>${safe(title)}</h2><p>${safe(copy)}</p></div></article>`).join('')}</section>${section('Activity impact','What every meaningful procedure leaves behind','A procedure is complete only when it accounts for actor cost, physical inputs, footprint, output or waste, affected beings, time, neglect, and recovery.', `<div class="impact-line"><span>Actor</span><b>→</b><span>Inputs</span><b>→</b><span>Footprint</span><b>→</b><span>World response</span><b>→</b><span>History</span></div>`)}`;
  }

  function entry() {
    const id = decodeURIComponent(location.hash.slice(1));
    const record = catalogueData.records.find(item => item.id === id) || catalogueData.records[0];
    if (!record) { content.innerHTML = '<p class="empty">The catalogue snapshot is empty.</p>'; return; }
    document.title = `${record.name} — Project Draugr World Bible`;
    const sections = record.sections.map(block => `<div><h2>${safe(block.title)}</h2>${block.rows.length > 1 ? `<ul>${block.rows.map(row => `<li>${safe(row)}</li>`).join('')}</ul>` : `<p>${safe(block.rows[0])}</p>`}</div>`).join('');
    content.innerHTML = `<a class="back-link" href="catalogue.html">← Back to catalogue</a><section class="entry-hero"><div class="entry-icon">${record.icon}</div><div><p class="eyebrow">${safe(tabNames[record.category] || record.category)} · ${safe(record.type)}</p>${badge(record.status)}<h1>${safe(record.name)}</h1><p>${safe(record.detail)}</p></div></section><section class="entry-layout"><article class="entry-main">${sections}</article><aside class="entry-meta"><p class="eyebrow">Repository record</p><dl><div><dt>Canonical key</dt><dd><code>${safe(record.key)}</code></dd></div><div><dt>Category</dt><dd>${safe(tabNames[record.category] || record.category)}</dd></div><div><dt>Classification</dt><dd>${safe(record.subcategory)}</dd></div><div><dt>Status</dt><dd>${safe(record.status)}</dd></div><div><dt>Introduced / updated</dt><dd>${safe(record.migration)}</dd></div></dl><p class="muted">This page is generated from the game’s persisted catalogue declarations. It describes the world record without granting player knowledge, ownership, or reachability.</p></aside></section>`;
  }

  ({ home, atlas, catalogue, industries, rules, entry }[page] || home)();
})();
