/* Static creator-reference data. Planned entries never masquerade as active runtime content. */
window.WorldBible = {
  seed: '681013497',
  biomes: [
    { icon: '🌲', name: 'Temperate Forest', key: 'TEMPERATE_FOREST', status: 'Generated', detail: 'Canopy, finite tree stands, deadfall, understory and leaf litter; a major source of wood, fibre, forage and woodland ecology.' },
    { icon: '🌾', name: 'Grassland', key: 'GRASSLAND', status: 'Generated', detail: 'Open wind-exposed ground with grasses, seed heads, grazing routes, field stone and long sightlines.' },
    { icon: '🪷', name: 'Wetland', key: 'WETLAND', status: 'Generated', detail: 'Freshwater marsh ground with reeds, clay, fish and amphibious life; depth, rot, contamination and disease remain consequential.' },
    { icon: '⛰️', name: 'Highland', key: 'HIGHLAND', status: 'Generated', detail: 'Open slopes, thin turf, exposed stone and wind, supporting hardy plants, upland animals and difficult travel.' },
    { icon: '🏔️', name: 'Mountain', key: 'MOUNTAIN', status: 'Generated', detail: 'Cold, rocky terrain supporting exposed geology, cliff ecology and sparse high-altitude life.' },
    { icon: '🌊', name: 'Ocean', key: 'OCEAN', status: 'Generated', detail: 'Open salt water with severe travel and weather exposure.' },
    { icon: '🏖️', name: 'Coast', key: 'COAST', status: 'Generated', detail: 'Land directly touching open water: the implemented shore for littoral flora, shell beds, shore wildlife and salt-weather exposure.' },
    { icon: '〰️', name: 'River Bank', key: 'RIVER_BANK', status: 'Generated', detail: 'Deterministically carved freshwater channels connecting high ground to standing water, with river-specific gathering, travel and ecology.' },
    { icon: '◒', name: 'Cave Mouth', key: 'CAVE_MOUTH', status: 'Generated', detail: 'Sparse mountain entrances that open onto walkable ground and support cave sites, shelter and subterranean ecology.' },
    { icon: '●', name: 'Cave Interior', key: 'CAVE_INTERIOR', status: 'Generated', detail: 'Enclosed rock immediately behind a valid entrance, with darkness, reduced wind and cave-specific environmental rules.' }
  ],
  atlasLegend: [
    ['ecology', '◆', 'Ecology & survival'], ['material', '●', 'Material province'], ['wildlife', '✦', 'Wildlife range'], ['monster', '▲', 'Monster territory'], ['native', '◇', 'Native-society candidate'], ['ruin', '✚', 'Ancient ruin']
  ],
  rules: [
    ['Identity never changes', 'Every physical object has a UUID. Objects are not deleted merely because they are lost, used up, destroyed, or no longer owned.'],
    ['Current state is authoritative', 'The world’s present locations, conditions, containment, ownership and ecology are the truth used by play.'],
    ['History is immutable', 'Actions and consequences leave an enduring record. Repairs and recovery do not erase past damage.'],
    ['Everything has a place', 'Every object always has a physical location or owner. Remote access is not allowed.'],
    ['Ecology is alive', 'Wildlife, monsters and native peoples respond to real food, territory, disturbance, routes, property and relationships. They do not silently despawn.'],
    ['No catalogue-only content', 'An entry may only become active when it has seed-compatible habitat, physical sources, a procedure, lifecycle, tests and Auditor evidence.'],
    ['The narrator witnesses', 'Narration describes physical results. It does not disclose hidden recipes, rescue a careless Chronicle, or act as a hint system.'],
    ['The world remembers', 'Every life leaves a mark. The world remembers. The Overseer never forgets.']
  ]
};
