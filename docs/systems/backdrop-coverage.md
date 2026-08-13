# Backdrop coverage report

Schema v1 · 151 backdrops · source `frontend/src/assets`

## By base biome

- **TEMPERATE_FOREST**: 69
- **WETLAND**: 27
- **GRASSLAND**: 23
- **HIGHLAND**: 15
- **MOUNTAIN**: 15
- **OCEAN**: 10

## By family

### BASE_BIOME (12)

- `coast` — OCEAN · REGIONAL
- `deep-peat-bog` — WETLAND · REGIONAL
- `dense-forest` — TEMPERATE_FOREST · REGIONAL
- `dry-scrub` — GRASSLAND · REGIONAL
- `forest` — TEMPERATE_FOREST · REGIONAL
- `highland` — HIGHLAND · REGIONAL
- `locust-damaged-grassland` — GRASSLAND · REGIONAL
- `mountain-crag` — MOUNTAIN · REGIONAL
- `open-ocean` — OCEAN · REGIONAL
- `plains` — GRASSLAND · REGIONAL
- `salt-marsh` — WETLAND · REGIONAL
- `wetland` — WETLAND · REGIONAL

### ECOTONE (7)

- `forest-grassland-ecotone` — TEMPERATE_FOREST+GRASSLAND · ECOTONE
- `forest-highland-foothill` — TEMPERATE_FOREST+HIGHLAND · ECOTONE
- `forest-wetland-ecotone` — TEMPERATE_FOREST+WETLAND · ECOTONE
- `grassland-coastal-dune` — GRASSLAND+OCEAN · ECOTONE
- `grassland-wetland-ecotone` — GRASSLAND+WETLAND · ECOTONE
- `highland-mountain-snowline` — HIGHLAND+MOUNTAIN · ECOTONE
- `plains-highland-foothill` — GRASSLAND+HIGHLAND · ECOTONE

### FRESHWATER (21)

- `alpine-headwater` — HIGHLAND · ADJACENT_VISIBLE
- `beaver-colony` — TEMPERATE_FOREST · EXACT_SITE
- `crayfish-stony-shallows` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `crocodilian-river-ambush-bank` — WETLAND · ADJACENT_VISIBLE
- `deep-slow-river-pool` — TEMPERATE_FOREST · REGIONAL
- `forest-lakebank` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `highland-tarn` — HIGHLAND · ADJACENT_VISIBLE
- `marsh-island` — WETLAND · EXACT_SITE
- `open-lakeshore` — GRASSLAND · ADJACENT_VISIBLE
- `otter-waterway` — TEMPERATE_FOREST · REGIONAL
- `river-floodplain` — WETLAND · REGIONAL
- `river-ford` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `river-turtle-basking-bank` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `sheltered-lake-reed-fishery` — WETLAND · REGIONAL
- `stillwater-pike-weed-bed` — TEMPERATE_FOREST · REGIONAL
- `stream` — TEMPERATE_FOREST · REGIONAL
- `trout-spawning-run` — TEMPERATE_FOREST · REGIONAL
- `undercut-riverbank-catfish-eel` — WETLAND · ADJACENT_VISIBLE
- `water-lily-bulrush-pond` — WETLAND · REGIONAL
- `wooded-riverbank` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `woodland-spring` — TEMPERATE_FOREST · EXACT_SITE

### COAST (5)

- `archipelago-shoreline` — OCEAN · ADJACENT_VISIBLE
- `coastal-cliff-dune` — OCEAN · ADJACENT_VISIBLE
- `estuary` — OCEAN+WETLAND · ECOTONE
- `shell-beach` — OCEAN · ADJACENT_VISIBLE
- `tidal-flat` — OCEAN · ADJACENT_VISIBLE

### KARST (6)

- `bat-cave-insect-roost` — MOUNTAIN · EXACT_SITE · discovery-gated
- `cave-bear-shelter` — MOUNTAIN · EXACT_SITE · discovery-gated
- `cave-mouth-lair` — MOUNTAIN · EXACT_SITE · discovery-gated
- `karst-cave` — MOUNTAIN · EXACT_SITE
- `karst-cave-interior` — MOUNTAIN · EXACT_SITE · discovery-gated
- `underground-karst-stream` — MOUNTAIN · EXACT_SITE · discovery-gated

### GEOLOGICAL (10)

- `clear-quartz-crystal-pocket` — TEMPERATE_FOREST · EXACT_SITE
- `flint-chert-field` — TEMPERATE_FOREST · EXACT_SITE
- `limestone-lime-outcrop` — TEMPERATE_FOREST · EXACT_SITE
- `obsidian-field` — MOUNTAIN · EXACT_SITE
- `precision-tool-stone-exposure` — TEMPERATE_FOREST · EXACT_SITE
- `pumice-basalt-volcanic-scree` — MOUNTAIN · EXACT_SITE
- `quarry` — MOUNTAIN · EXACT_SITE
- `rounded-hammerstone-cobble-bar` — GRASSLAND · EXACT_SITE
- `sandstone-abrasive-outcrop` — TEMPERATE_FOREST · EXACT_SITE
- `soapstone-outcrop` — TEMPERATE_FOREST · EXACT_SITE

### RESOURCE_SITE (12)

- `bog-iron-iron-sand-bar` — WETLAND · EXACT_SITE
- `clay-deposit` — WETLAND · EXACT_SITE
- `copper-seam` — TEMPERATE_FOREST · EXACT_SITE
- `iron-pyrite-exposure` — TEMPERATE_FOREST · EXACT_SITE
- `iron-vein` — TEMPERATE_FOREST · EXACT_SITE
- `ochre-pigment-earth-bank` — GRASSLAND · EXACT_SITE
- `placer-gold-gravel-bar` — TEMPERATE_FOREST · EXACT_SITE
- `refractory-clay-bed` — WETLAND · EXACT_SITE
- `rock-salt-exposure` — MOUNTAIN · EXACT_SITE
- `silver-lead-exposure` — TEMPERATE_FOREST · EXACT_SITE
- `sulphur-deposit` — TEMPERATE_FOREST · EXACT_SITE
- `tin-exposure` — TEMPERATE_FOREST · EXACT_SITE

### RUIN (9)

- `ancient-observatory` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `broken-aqueduct` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `buried-granary` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `collapsed-causeway` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `fallen-city-exterior` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `flooded-archive` — WETLAND · EXACT_SITE · discovery-gated
- `overgrown-watchtower` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `ruin-archive-interior` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `sunken-shrine` — WETLAND · EXACT_SITE · discovery-gated

### FLORA_SITE (14)

- `blackberry-thorn-brush` — TEMPERATE_FOREST · REGIONAL
- `bog-medicinal-toxic-flora` — WETLAND · EXACT_SITE
- `edible-root-patch` — TEMPERATE_FOREST · REGIONAL
- `fibre-grassland` — GRASSLAND · REGIONAL
- `highland-bilberry-juniper-heath` — HIGHLAND · REGIONAL
- `mushroom-hollow` — TEMPERATE_FOREST · EXACT_SITE
- `old-growth-timber` — TEMPERATE_FOREST · REGIONAL
- `reed-root-marsh` — WETLAND · REGIONAL
- `shelter-grove` — TEMPERATE_FOREST · REGIONAL
- `wetland-willow-carr` — WETLAND · REGIONAL
- `wild-fibre-meadow` — GRASSLAND · REGIONAL
- `wild-grain-seed-grass-stand` — GRASSLAND · REGIONAL
- `wild-herb-grove` — TEMPERATE_FOREST · REGIONAL
- `wild-mast-nut-grove` — TEMPERATE_FOREST · REGIONAL

### FAUNA_RANGE (37)

- `alpine-goat-range` — HIGHLAND · REGIONAL
- `arctic-fox-snowfield-den` — HIGHLAND · EXACT_SITE
- `aurochs-open-herd-range` — GRASSLAND · REGIONAL
- `badger-sett` — TEMPERATE_FOREST · EXACT_SITE
- `bear-den` — TEMPERATE_FOREST · EXACT_SITE
- `boar-wallow` — TEMPERATE_FOREST · EXACT_SITE
- `bog-amphibian-nursery` — WETLAND · EXACT_SITE
- `cricket-night-meadow` — GRASSLAND · REGIONAL · NIGHT
- `deer-range` — TEMPERATE_FOREST · REGIONAL
- `dire-wolf-pack-ground` — TEMPERATE_FOREST · REGIONAL
- `earthworm-rich-forest-soil` — TEMPERATE_FOREST · EXACT_SITE
- `elk-range` — HIGHLAND · REGIONAL
- `forest-ant-mound-colony` — TEMPERATE_FOREST · EXACT_SITE
- `forest-rat-groundbird-edge` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `forest-spider-den` — TEMPERATE_FOREST · EXACT_SITE
- `fox-earth` — TEMPERATE_FOREST · REGIONAL
- `giant-hornet-queen-nest` — TEMPERATE_FOREST · EXACT_SITE
- `golden-jackal-scrub-territory` — GRASSLAND · REGIONAL
- `gopher-colony` — GRASSLAND · EXACT_SITE
- `grassland-insect-colony` — GRASSLAND · EXACT_SITE
- `herd-migration-corridor` — GRASSLAND · REGIONAL
- `nocturnal-firefly-marsh` — WETLAND · EXACT_SITE · NIGHT
- `nocturnal-owl-woodland` — TEMPERATE_FOREST · REGIONAL · NIGHT
- `ordinary-hornet-nest` — TEMPERATE_FOREST · EXACT_SITE
- `raptor-cliff-nesting-range` — HIGHLAND · ADJACENT_VISIBLE
- `raven-scavenging-range` — TEMPERATE_FOREST · REGIONAL
- `reindeer-highland-range` — HIGHLAND · REGIONAL
- `reptile-basking-heath` — HIGHLAND · ADJACENT_VISIBLE
- `seabird-island-colony` — OCEAN · EXACT_SITE
- `silk-moth-woodland-colony` — TEMPERATE_FOREST · EXACT_SITE
- `squirrel-canopy-range` — TEMPERATE_FOREST · REGIONAL
- `warren-ground` — GRASSLAND · REGIONAL
- `waterfowl-nesting-ground` — WETLAND · REGIONAL
- `wild-honeybee-tree-hive` — TEMPERATE_FOREST · EXACT_SITE
- `wild-water-buffalo-range` — GRASSLAND · REGIONAL
- `wolverine-talus-territory` — MOUNTAIN · REGIONAL
- `woodland-mesopredator-territory` — TEMPERATE_FOREST · REGIONAL

### MONSTER_TERRITORY (15)

- `ash-hound-den` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `bog-warden-lair` — WETLAND · EXACT_SITE · discovery-gated
- `cave-troll-shelter` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `centaur-territory-camp` — GRASSLAND · EXACT_SITE · discovery-gated
- `deepwater-maw-territory` — OCEAN · EXACT_SITE · discovery-gated
- `dusk-prowler-territory` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `fen-siren-pool` — WETLAND · EXACT_SITE · discovery-gated
- `glasswing-roost` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `gloom-moth-colony` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated · NIGHT
- `harpy-cliff-territory` — HIGHLAND · EXACT_SITE · discovery-gated
- `mire-hydra-nest` — WETLAND · EXACT_SITE · discovery-gated
- `ridge-stalker-ambush-ground` — HIGHLAND · EXACT_SITE · discovery-gated
- `roc-highland-eyrie` — MOUNTAIN · EXACT_SITE · discovery-gated
- `thornback-wallow` — TEMPERATE_FOREST · EXACT_SITE · discovery-gated
- `wyvern-roost` — MOUNTAIN · EXACT_SITE · discovery-gated

### NATIVE_TERRITORY (2)

- `native-settlement-edge` — TEMPERATE_FOREST · ADJACENT_VISIBLE
- `reedkin-river-isle-settlement` — WETLAND · EXACT_SITE · discovery-gated

### DOMESTICATION (1)

- `domestication-paddock` — GRASSLAND · EXACT_SITE
