# Backdrop coverage report

Schema v1 · 151 backdrops · source `frontend/src/assets`

## By base biome

- **TEMPERATE_FOREST**: 69
- **WETLAND**: 27
- **GRASSLAND**: 23
- **HIGHLAND**: 15
- **MOUNTAIN**: 15
- **OCEAN**: 10

## Reachability

**91 of 151** records are reachable by a declared world context; **60** are gated.

| waiting on | count | reason |
|---|---|---|
| #232, #224 | 38 | The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. |
| #155 | 10 | The world has no site kind or biome for this feature yet. |
| #236, #232 | 3 | Night-only scenery needs night eligibility per asset, and a habitat feature in the visual context. |
| #117, #120 | 3 | No world site places these creatures yet. |
| #109, #115 | 2 | No native settlement is placed in the world yet. |
| #160 | 2 | Gold and sulphur provinces were rejected until a consumer exists. |
| #154 | 1 | Centaurs are a candidate native people, not yet placed. |
| #207 | 1 | Needs a locust disturbance event on the ground. |

## By family

### BASE_BIOME (12)

- `coast` — OCEAN · REGIONAL · reached by `biome.coast`, `COAST`
- `deep-peat-bog` — WETLAND · REGIONAL · reached by `biome.wetland.deep`
- `dense-forest` — TEMPERATE_FOREST · REGIONAL · reached by `biome.temperate-forest.deep`
- `dry-scrub` — GRASSLAND · REGIONAL · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `forest` — TEMPERATE_FOREST · REGIONAL · reached by `world.default`, `biome.temperate-forest`, `TEMPERATE_FOREST`
- `highland` — HIGHLAND · REGIONAL · reached by `biome.highland`, `HIGHLAND`
- `locust-damaged-grassland` — GRASSLAND · REGIONAL · QUARANTINED · **gated**: Needs a locust disturbance event on the ground. (#207)
- `mountain-crag` — MOUNTAIN · REGIONAL · reached by `biome.mountain`, `MOUNTAIN`
- `open-ocean` — OCEAN · REGIONAL · reached by `biome.ocean`, `OCEAN`
- `plains` — GRASSLAND · REGIONAL · reached by `biome.grassland`, `GRASSLAND`
- `salt-marsh` — WETLAND · REGIONAL · reached by `site.salt-marsh`
- `wetland` — WETLAND · REGIONAL · reached by `biome.wetland`, `WETLAND`

### ECOTONE (7)

- `forest-grassland-ecotone` — TEMPERATE_FOREST+GRASSLAND · ECOTONE · reached by `edge-or-beside TEMPERATE_FOREST/GRASSLAND`
- `forest-highland-foothill` — TEMPERATE_FOREST+HIGHLAND · ECOTONE · reached by `edge-or-beside TEMPERATE_FOREST/HIGHLAND`
- `forest-wetland-ecotone` — TEMPERATE_FOREST+WETLAND · ECOTONE · reached by `edge-or-beside TEMPERATE_FOREST/WETLAND`
- `grassland-coastal-dune` — GRASSLAND+OCEAN · ECOTONE · reached by `edge-or-beside GRASSLAND/OCEAN`
- `grassland-wetland-ecotone` — GRASSLAND+WETLAND · ECOTONE · reached by `edge-or-beside GRASSLAND/WETLAND`
- `highland-mountain-snowline` — HIGHLAND+MOUNTAIN · ECOTONE · reached by `edge-or-beside HIGHLAND/MOUNTAIN`
- `plains-highland-foothill` — GRASSLAND+HIGHLAND · ECOTONE · reached by `edge-or-beside GRASSLAND/HIGHLAND`

### FRESHWATER (21)

- `alpine-headwater` — HIGHLAND · ADJACENT_VISIBLE · reached by `site.headwater-spring`
- `beaver-colony` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.beaver-pool`, `site.beaver-lodge`
- `crayfish-stony-shallows` — TEMPERATE_FOREST · ADJACENT_VISIBLE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `crocodilian-river-ambush-bank` — WETLAND · ADJACENT_VISIBLE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `deep-slow-river-pool` — TEMPERATE_FOREST · REGIONAL · reached by `site.slow-river-reach`
- `forest-lakebank` — TEMPERATE_FOREST · ADJACENT_VISIBLE · reached by `site.lake-margin in TEMPERATE_FOREST`
- `highland-tarn` — HIGHLAND · ADJACENT_VISIBLE · reached by `site.still-pond in HIGHLAND`
- `marsh-island` — WETLAND · EXACT_SITE · reached by `site.marsh-island`
- `open-lakeshore` — GRASSLAND · ADJACENT_VISIBLE · reached by `site.lake-margin`
- `otter-waterway` — TEMPERATE_FOREST · REGIONAL · reached by `site.otter-waterway`
- `river-floodplain` — WETLAND · REGIONAL · reached by `site.floodplain`
- `river-ford` — TEMPERATE_FOREST · ADJACENT_VISIBLE · reached by `site.river-ford`, `site.shallow-ford`
- `river-turtle-basking-bank` — TEMPERATE_FOREST · ADJACENT_VISIBLE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `sheltered-lake-reed-fishery` — WETLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `stillwater-pike-weed-bed` — TEMPERATE_FOREST · REGIONAL · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `stream` — TEMPERATE_FOREST · REGIONAL · reached by `site.fast-stream`, `biome.river-bank`, `RIVER_BANK`
- `trout-spawning-run` — TEMPERATE_FOREST · REGIONAL · reached by `site.river-fishing-run`
- `undercut-riverbank-catfish-eel` — WETLAND · ADJACENT_VISIBLE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `water-lily-bulrush-pond` — WETLAND · REGIONAL · QUARANTINED · reached by `site.still-pond`
- `wooded-riverbank` — TEMPERATE_FOREST · ADJACENT_VISIBLE · reached by `site.riverside-withy-bed`
- `woodland-spring` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.freshwater-spring`

### COAST (5)

- `archipelago-shoreline` — OCEAN · ADJACENT_VISIBLE · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `coastal-cliff-dune` — OCEAN · ADJACENT_VISIBLE · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `estuary` — OCEAN+WETLAND · ECOTONE · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `shell-beach` — OCEAN · ADJACENT_VISIBLE · reached by `site.shell-bed`
- `tidal-flat` — OCEAN · ADJACENT_VISIBLE · **gated**: The world has no site kind or biome for this feature yet. (#155)

### KARST (6)

- `bat-cave-insect-roost` — MOUNTAIN · EXACT_SITE · discovery-gated · reached by `site.bat-roost`, `site.deep-cave-roost`
- `cave-bear-shelter` — MOUNTAIN · EXACT_SITE · discovery-gated · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `cave-mouth-lair` — MOUNTAIN · EXACT_SITE · discovery-gated · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `karst-cave` — MOUNTAIN · EXACT_SITE · reached by `biome.cave-mouth`, `CAVE_MOUTH`
- `karst-cave-interior` — MOUNTAIN · EXACT_SITE · discovery-gated · reached by `interior.cave.lit`, `interior.cave.dark`, `CAVE_INTERIOR`
- `underground-karst-stream` — MOUNTAIN · EXACT_SITE · discovery-gated · reached by `site.underground-stream`

### GEOLOGICAL (10)

- `clear-quartz-crystal-pocket` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.crystal-pocket`
- `flint-chert-field` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.flint-field`
- `limestone-lime-outcrop` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.limestone-quarry`
- `obsidian-field` — MOUNTAIN · EXACT_SITE · reached by `site.obsidian-field`
- `precision-tool-stone-exposure` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.precision-tool-stone-exposure`
- `pumice-basalt-volcanic-scree` — MOUNTAIN · EXACT_SITE · reached by `site.volcanic-scree`
- `quarry` — MOUNTAIN · EXACT_SITE · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `rounded-hammerstone-cobble-bar` — GRASSLAND · EXACT_SITE · reached by `site.gravel-bar`
- `sandstone-abrasive-outcrop` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.stone-outcrop`
- `soapstone-outcrop` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.soapstone-outcrop`

### RESOURCE_SITE (12)

- `bog-iron-iron-sand-bar` — WETLAND · EXACT_SITE · reached by `site.iron-sand-bar`
- `clay-deposit` — WETLAND · EXACT_SITE · reached by `site.clay-beds`, `CLAY_DEPOSIT`
- `copper-seam` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.copper-seam`
- `iron-pyrite-exposure` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.pyrite-exposure`
- `iron-vein` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.iron-vein`
- `ochre-pigment-earth-bank` — GRASSLAND · EXACT_SITE · reached by `site.ochre-earth-bank`
- `placer-gold-gravel-bar` — TEMPERATE_FOREST · EXACT_SITE · **gated**: Gold and sulphur provinces were rejected until a consumer exists. (#160)
- `refractory-clay-bed` — WETLAND · EXACT_SITE · reached by `site.refractory-clay-bed`
- `rock-salt-exposure` — MOUNTAIN · EXACT_SITE · reached by `SALT_DEPOSIT`
- `silver-lead-exposure` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.silver-lead-exposure`
- `sulphur-deposit` — TEMPERATE_FOREST · EXACT_SITE · **gated**: Gold and sulphur provinces were rejected until a consumer exists. (#160)
- `tin-exposure` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.tin-exposure`

### RUIN (9)

- `ancient-observatory` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.ancient-observatory`
- `broken-aqueduct` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.broken-aqueduct`
- `buried-granary` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.buried-granary`
- `collapsed-causeway` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.collapsed-causeway`
- `fallen-city-exterior` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `flooded-archive` — WETLAND · EXACT_SITE · discovery-gated · reached by `site.flooded-archive`
- `overgrown-watchtower` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.overgrown-watchtower`
- `ruin-archive-interior` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · **gated**: The world has no site kind or biome for this feature yet. (#155)
- `sunken-shrine` — WETLAND · EXACT_SITE · discovery-gated · QUARANTINED · reached by `site.sunken-shrine`

### FLORA_SITE (14)

- `blackberry-thorn-brush` — TEMPERATE_FOREST · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `bog-medicinal-toxic-flora` — WETLAND · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `edible-root-patch` — TEMPERATE_FOREST · REGIONAL · reached by `site.edible-root-patch`
- `fibre-grassland` — GRASSLAND · REGIONAL · reached by `site.fiber-grassland`
- `highland-bilberry-juniper-heath` — HIGHLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `mushroom-hollow` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.mushroom-hollow`
- `old-growth-timber` — TEMPERATE_FOREST · REGIONAL · reached by `site.old-growth-timber`
- `reed-root-marsh` — WETLAND · REGIONAL · reached by `site.reed-marsh`
- `shelter-grove` — TEMPERATE_FOREST · REGIONAL · reached by `site.shelter-grove`
- `wetland-willow-carr` — WETLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `wild-fibre-meadow` — GRASSLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `wild-grain-seed-grass-stand` — GRASSLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `wild-herb-grove` — TEMPERATE_FOREST · REGIONAL · reached by `site.wild-herb-grove`
- `wild-mast-nut-grove` — TEMPERATE_FOREST · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)

### FAUNA_RANGE (37)

- `alpine-goat-range` — HIGHLAND · REGIONAL · reached by `site.goat-cliff-range`
- `arctic-fox-snowfield-den` — HIGHLAND · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `aurochs-open-herd-range` — GRASSLAND · REGIONAL · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `badger-sett` — TEMPERATE_FOREST · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `bear-den` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.bear-den`
- `boar-wallow` — TEMPERATE_FOREST · EXACT_SITE · reached by `site.boar-range`
- `bog-amphibian-nursery` — WETLAND · EXACT_SITE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `cricket-night-meadow` — GRASSLAND · REGIONAL · NIGHT · **gated**: Night-only scenery needs night eligibility per asset, and a habitat feature in the visual context. (#236, #232)
- `deer-range` — TEMPERATE_FOREST · REGIONAL · reached by `site.deer-range`
- `dire-wolf-pack-ground` — TEMPERATE_FOREST · REGIONAL · reached by `site.wolf-pack-ground`
- `earthworm-rich-forest-soil` — TEMPERATE_FOREST · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `elk-range` — HIGHLAND · REGIONAL · reached by `site.elk-range`
- `forest-ant-mound-colony` — TEMPERATE_FOREST · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `forest-rat-groundbird-edge` — TEMPERATE_FOREST · ADJACENT_VISIBLE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `forest-spider-den` — TEMPERATE_FOREST · EXACT_SITE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `fox-earth` — TEMPERATE_FOREST · REGIONAL · reached by `site.fox-earth`
- `giant-hornet-queen-nest` — TEMPERATE_FOREST · EXACT_SITE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `golden-jackal-scrub-territory` — GRASSLAND · REGIONAL · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `gopher-colony` — GRASSLAND · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `grassland-insect-colony` — GRASSLAND · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `herd-migration-corridor` — GRASSLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `nocturnal-firefly-marsh` — WETLAND · EXACT_SITE · NIGHT · QUARANTINED · **gated**: Night-only scenery needs night eligibility per asset, and a habitat feature in the visual context. (#236, #232)
- `nocturnal-owl-woodland` — TEMPERATE_FOREST · REGIONAL · NIGHT · **gated**: Night-only scenery needs night eligibility per asset, and a habitat feature in the visual context. (#236, #232)
- `ordinary-hornet-nest` — TEMPERATE_FOREST · EXACT_SITE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `raptor-cliff-nesting-range` — HIGHLAND · ADJACENT_VISIBLE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `raven-scavenging-range` — TEMPERATE_FOREST · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `reindeer-highland-range` — HIGHLAND · REGIONAL · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `reptile-basking-heath` — HIGHLAND · ADJACENT_VISIBLE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `seabird-island-colony` — OCEAN · EXACT_SITE · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `silk-moth-woodland-colony` — TEMPERATE_FOREST · EXACT_SITE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `squirrel-canopy-range` — TEMPERATE_FOREST · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `warren-ground` — GRASSLAND · REGIONAL · reached by `site.hare-warren`
- `waterfowl-nesting-ground` — WETLAND · REGIONAL · reached by `site.marsh-fowl-nesting`
- `wild-honeybee-tree-hive` — TEMPERATE_FOREST · EXACT_SITE · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `wild-water-buffalo-range` — GRASSLAND · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `wolverine-talus-territory` — MOUNTAIN · REGIONAL · QUARANTINED · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)
- `woodland-mesopredator-territory` — TEMPERATE_FOREST · REGIONAL · **gated**: The visual context reports sites and completed builds only; species habitat and flora stands are not yet features a place can carry. (#232, #224)

### MONSTER_TERRITORY (15)

- `ash-hound-den` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.ash-hound-den`
- `bog-warden-lair` — WETLAND · EXACT_SITE · discovery-gated · reached by `site.bog-warden-lair`
- `cave-troll-shelter` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.troll-shelter`
- `centaur-territory-camp` — GRASSLAND · EXACT_SITE · discovery-gated · **gated**: Centaurs are a candidate native people, not yet placed. (#154)
- `deepwater-maw-territory` — OCEAN · EXACT_SITE · discovery-gated · reached by `site.deepwater-maw`
- `dusk-prowler-territory` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.dusk-prowler-territory`
- `fen-siren-pool` — WETLAND · EXACT_SITE · discovery-gated · reached by `site.fen-siren-pool`
- `glasswing-roost` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.glasswing-roost`
- `gloom-moth-colony` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · NIGHT · reached by `site.gloom-moth-colony`
- `harpy-cliff-territory` — HIGHLAND · EXACT_SITE · discovery-gated · **gated**: No world site places these creatures yet. (#117, #120)
- `mire-hydra-nest` — WETLAND · EXACT_SITE · discovery-gated · reached by `site.mire-hydra-nest`
- `ridge-stalker-ambush-ground` — HIGHLAND · EXACT_SITE · discovery-gated · reached by `site.ridge-stalker-lair`
- `roc-highland-eyrie` — MOUNTAIN · EXACT_SITE · discovery-gated · QUARANTINED · **gated**: No world site places these creatures yet. (#117, #120)
- `thornback-wallow` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · reached by `site.thornback-wallow`
- `wyvern-roost` — MOUNTAIN · EXACT_SITE · discovery-gated · **gated**: No world site places these creatures yet. (#117, #120)

### NATIVE_TERRITORY (2)

- `native-settlement-edge` — TEMPERATE_FOREST · ADJACENT_VISIBLE · **gated**: No native settlement is placed in the world yet. (#109, #115)
- `reedkin-river-isle-settlement` — WETLAND · EXACT_SITE · discovery-gated · **gated**: No native settlement is placed in the world yet. (#109, #115)

### DOMESTICATION (1)

- `domestication-paddock` — GRASSLAND · EXACT_SITE · reached by `built.animal-pen`
