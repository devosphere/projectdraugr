# Backdrop audit — #240

**Reviewer:** automated vision (Claude), under the #241 contract. **Date:** 2026-09-15. **Scope:** all 151 files in `frontend/src/assets/playthrough-*.png`, each inspected at its full stored resolution (1672×941).

> The contract is explicit: **automated vision may flag, never approve.** Nothing below is an approval. Every finding is recorded in `backdrop-manifest.json` under `review.findings` with `source: AUTOMATED_VISION`, and every image still needs a person to approve it before its key leaves `legacy-unreviewed.json`.

## Summary

| result | count | what happens |
|---|---|---|
| **Creature visible** | 10 | `QUARANTINED` — never selected until replaced and re-reviewed |
| **Ambiguous** — something could read as a creature | 8 | `QUARANTINED` — creator review |
| **Rendering artefact**, no creature | 83 | stays `ACTIVE` as LEGACY; flagged `REPLACE` — blocks approval, not display |
| **No finding** | 50 | stays `ACTIVE` as LEGACY; ready for human approval |

**The rendering artefact.** 83 images carry the same generator defect: a faceted, mosaic "crackle" laid over rock, leaf litter, mud and water, which reads as tiling or pixel noise at full size. It is the same artefact in every case, so it is almost certainly one generation setting, and one regeneration pass would clear most of the list. It fails the `noGridTilingOrPixelation` checklist item, so none of these can be APPROVED as they stand. They are **not** quarantined: they show no creature, and three of them (`plains`, `highland`, `mountain-crag`) are base-biome fallback anchors that the rest of the registry resolves through.

## The known corrected cases

The ticket names six images as previously corrected. All six were checked individually:

| backdrop | result |
|---|---|
| `raven-scavenging-range` | no creature — rendering artefact only |
| `woodland-mesopredator-territory` | no creature — clean |
| `arctic-fox-snowfield-den` | no creature — clean |
| `nocturnal-owl-woodland` | no creature — clean |
| `undercut-riverbank-catfish-eel` | no creature — clean |
| `earthworm-rich-forest-soil` | no creature — clean |

## Quarantined — creature visible (10)

| backdrop | finding | disposition |
|---|---|---|
| `aurochs-open-herd-range` | A line of dark grazing animals is visible in the far right distance at the forest edge. Also mild faceted texture on foreground stones. | QUARANTINE — replace |
| `giant-hornet-queen-nest` | Several hornets clearly visible: in flight upper-left and centre, and crawling on the nest. Also heavy faceted texture artefact. | QUARANTINE — replace |
| `golden-jackal-scrub-territory` | A jackal stands in the middle distance on the trail (centre), with further animals behind it. Faceted texture on scrub too. | QUARANTINE — replace |
| `nocturnal-firefly-marsh` | Dozens of glowing fireflies across the marsh, in the foreground and distance. Insects are living creatures. | QUARANTINE — replace |
| `reindeer-highland-range` | A herd of about fifteen reindeer walks across the middle distance, clearly visible. Rocks also carry heavy faceted texture artefact. | QUARANTINE — replace |
| `river-turtle-basking-bank` | Seven or more turtles clearly visible basking on the bank and on the log in the foreground. Faceted texture on bank too. | QUARANTINE — replace |
| `silk-moth-woodland-colony` | Two adult moths clearly visible: a large one on the tree at left, another on the trunk at right, among the cocoons. | QUARANTINE — replace |
| `water-lily-bulrush-pond` | A dragonfly is visible in flight over the reeds at left (about a fifth in from the left edge, just above the pond line). The pond surface also carries heavy faceted texture artefact. | QUARANTINE — replace |
| `wild-honeybee-tree-hive` | Honeycomb in a hollow trunk with several bees visible in flight at the comb opening (left of centre). Bark and litter also carry faceted texture artefact. | QUARANTINE — replace |
| `wolverine-talus-territory` | A four-legged animal stands silhouetted on a rock ridge in the middle distance (right of centre). Claw-scored boulder and bones are fine as evidence, but the animal is not. Heavy faceted texture throughout. | QUARANTINE — replace |

## Quarantined — ambiguous, for creator review (8)

These may be innocent (a stone, a ripple, a knot of reeds). They are held back because the contract resolves doubt towards the neutral screen, not towards the picture.

| backdrop | finding | disposition |
|---|---|---|
| `bog-amphibian-nursery` | Small dark squiggle shapes in the clear water (centre and centre-left) could read as tadpoles. Needs a human look at full resolution. | CREATOR_REVIEW |
| `crocodilian-river-ambush-bank` | A small ridged shape breaking the surface (lower centre-left) and the ridged log at the bank can each read as a crocodilian. Grass carries the faceted texture artefact too. | CREATOR_REVIEW |
| `forest-spider-den` | Webbed hollow; two dark segmented shapes caught in the webs on the left (around mid-left and lower-left) read as insects or centipedes. Also heavy faceted texture artefact. | CREATOR_REVIEW |
| `locust-damaged-grassland` | Ground strewn with shed wings (habitat evidence), but small dark specks in the sky (upper centre-left, top centre) may read as flying locusts. Faceted texture on ground too. | CREATOR_REVIEW |
| `ordinary-hornet-nest` | Paper nest hanging from a branch over a forest path. A few small dark specks beside the nest (right of it, mid-air) may read as hornets in flight. Heavy faceted texture on boulders and litter too. | CREATOR_REVIEW |
| `roc-highland-eyrie` | Giant stick nest with shed feathers (habitat evidence), no roc. Two small winged silhouettes fly in the sky left of centre, above the peaks. Rock also carries heavy faceted texture artefact. | CREATOR_REVIEW |
| `stillwater-pike-weed-bed` | Clear lake shallows over weed beds. A small dark elongated shape near the submerged branch (centre-right) may read as a fish. Needs a human look at full resolution. | CREATOR_REVIEW |
| `sunken-shrine` | Flooded ruin with broken columns in misty woods, otherwise clean. A tiny dark shape on top of the tall left column (near the top edge) may read as a perched bird. | CREATOR_REVIEW |

## Rendering artefact, no creature (83)

| backdrop | finding | disposition |
|---|---|---|
| `alpine-goat-range` | No animal visible. Rock faces and grass carry a faceted mosaic texture artefact across the whole frame, which reads as tiling/pixel noise at full size. | REPLACE |
| `alpine-headwater` | No animal visible. Boulders, scree and water carry the same faceted mosaic texture artefact. | REPLACE |
| `badger-sett` | No animal visible; sett entrances only. Leaf litter and moss carry a heavy faceted mosaic texture artefact. | REPLACE |
| `bat-cave-insect-roost` | No bats or insects visible. Every cave surface carries the faceted mosaic texture artefact. | REPLACE |
| `bear-den` | Den mouth under a fallen trunk, no bear. Bark, boulders and leaf litter carry heavy faceted mosaic texture artefact. | REPLACE |
| `boar-wallow` | Muddy wallow in a clearing, no boar. Mud and leaf litter carry the faceted mosaic texture artefact. | REPLACE |
| `bog-warden-lair` | Misty bog mound, no creature. Grass, moss and water are covered by a net-like faceted texture artefact. | REPLACE |
| `cave-bear-shelter` | Cave mouth with scattered bones (habitat evidence), no bear. Rock and ground carry heavy faceted mosaic texture artefact. | REPLACE |
| `cave-mouth-lair` | Dark cave mouth among boulders, no creature. Every rock surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `cave-troll-shelter` | Cave with huge footprints, rib bones and stone cairns (habitat evidence), no troll. Faceted mosaic texture on rock and mud. | REPLACE |
| `centaur-territory-camp` | Empty hide-shelter camp with trough and gate; no people or centaurs. Stones, grass and hides carry faceted mosaic texture artefact. | REPLACE |
| `clear-quartz-crystal-pocket` | Quartz points in a rock cleft; nothing alive. The host rock walls carry the faceted mosaic texture artefact (distinct from the crystals themselves). | REPLACE |
| `coast` | Rocky shore with sea stacks; no birds or animals visible. Foreground boulders carry the faceted mosaic texture artefact. | REPLACE |
| `coastal-cliff-dune` | Dune grass above a pebble beach; nothing alive. Boulders on the dune carry the faceted mosaic texture artefact. | REPLACE |
| `copper-seam` | Green-stained copper outcrop on a mountainside; nothing alive. Rock surfaces carry the faceted mosaic texture artefact. | REPLACE |
| `deepwater-maw-territory` | Open storm sea from a rock ledge, no creature. Rock and sea surface carry the faceted mosaic texture artefact. | REPLACE |
| `deer-range` | Forest edge over a valley with antler-stripped bark (habitat evidence), no deer. Ground, stones and foliage carry the faceted mosaic texture artefact. | REPLACE |
| `dense-forest` | Old cedar forest with ferns, nothing alive. Leaf litter on the path carries a diamond-faceted mosaic texture artefact. | REPLACE |
| `dire-wolf-pack-ground` | Night forest path with a large skeleton (habitat evidence), no wolves. Heavy faceted mosaic texture on every surface. | REPLACE |
| `domestication-paddock` | Empty wattle paddock and shelter, no livestock. Rock and trodden ground carry the faceted mosaic texture artefact. | REPLACE |
| `dry-scrub` | Sunlit juniper scrub, nothing alive. Foreground rocks carry heavy faceted mosaic texture artefact. | REPLACE |
| `dusk-prowler-territory` | Dusk forest with claw-scored log (habitat evidence), no creature. Log bark and litter carry faceted mosaic texture artefact. | REPLACE |
| `edible-root-patch` | Root plants with exposed roots, nothing alive besides plants. Soil and rocks carry faceted mosaic texture artefact. | REPLACE |
| `elk-range` | Highland meadow valley, no elk. Grass and rocks carry faceted mosaic texture artefact. | REPLACE |
| `estuary` | Tidal channels in salt grass, no birds visible. Water and mud carry faceted mosaic texture artefact. | REPLACE |
| `fallen-city-exterior` | Overgrown ruined city avenue, nothing alive. Stones, vines and debris carry heavy faceted mosaic texture artefact. | REPLACE |
| `flint-chert-field` | Stony upland field below peaks, nothing alive. The whole ground surface is covered by heavy faceted mosaic texture artefact. | REPLACE |
| `forest-ant-mound-colony` | Needle mound between roots, no ants visible. Mound, rocks and bark carry faceted mosaic texture artefact. | REPLACE |
| `forest-highland-foothill` | Stepped rocky path up a wooded slope, nothing alive. Boulders and path carry faceted mosaic texture artefact. | REPLACE |
| `forest-lakebank` | Misty lake through conifers, nothing alive. Leaf litter on the shore path carries the diamond-faceted texture artefact. | REPLACE |
| `forest-rat-groundbird-edge` | Forest floor with fallen branches, no rats or birds visible. Leaf litter carries heavy diamond-faceted texture artefact. | REPLACE |
| `fox-earth` | Burrow entrances in a root mound, no fox. Foliage and ground carry faceted mosaic texture artefact. | REPLACE |
| `glasswing-roost` | Mountain cliff ledge with glassy shards, no creature. Every rock surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `grassland-insect-colony` | Tall-grass path, no insects visible. The path and stones carry faceted mosaic texture artefact. | REPLACE |
| `harpy-cliff-territory` | Stick nest on a cliff ledge with scattered feathers (habitat evidence), no creature. Every rock surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `herd-migration-corridor` | Broad grass valley towards a lake, no herd visible. Foreground boulders carry faceted mosaic texture artefact. | REPLACE |
| `highland-bilberry-juniper-heath` | Heath shrubs below peaks, nothing alive besides plants. Shrubs, rocks and slopes carry heavy faceted mosaic texture artefact. | REPLACE |
| `highland-tarn` | Mountain tarn in a rocky cirque, no fish or animals. Boulders, scree and slopes carry heavy faceted mosaic texture artefact. | REPLACE |
| `highland` | Rocky pass looking to misty peaks, nothing alive. Boulders and scree carry faceted mosaic texture artefact. | REPLACE |
| `iron-pyrite-exposure` | Pyrite-studded rock face on a mountain path, nothing alive. The rock face carries heavy faceted mosaic texture artefact. | REPLACE |
| `iron-vein` | Rust-stained vein across a mountainside, nothing alive. Every rock surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `karst-cave-interior` | Stalactite passage with a stream, no bats or creatures visible. Cave walls and floor carry faceted mosaic texture artefact. | REPLACE |
| `karst-cave` | Cave mouth in a limestone cliff among ferns, nothing alive. The cliff face carries faceted mosaic texture artefact. | REPLACE |
| `limestone-lime-outcrop` | Pale limestone blocks above a gorge, nothing alive. Blocks and scree carry faceted texture artefact. | REPLACE |
| `marsh-island` | Willow islet in a misty pool, nothing alive besides plants. Water, moss and foliage carry heavy faceted mosaic texture artefact. | REPLACE |
| `mire-hydra-nest` | Flattened reed nest with water-filled hollows (habitat evidence), no creature. Every surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `mountain-crag` | Crag and cliff ledge over a valley, nothing alive. Boulders and scree carry faceted mosaic texture artefact. | REPLACE |
| `native-settlement-edge` | Empty drying racks and stone walls at a cave mouth, no people. Every surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `obsidian-field` | Glassy black obsidian slope below peaks, nothing alive. The mid-slope scree and far slopes carry the faceted mosaic texture artefact (separate from the natural conchoidal glass). | REPLACE |
| `ochre-pigment-earth-bank` | Red and yellow ochre bank above a stream, nothing alive. The bank, stones and cliff carry faceted mosaic texture artefact. | REPLACE |
| `old-growth-timber` | Giant conifers with ferns and fallen logs, nothing alive besides plants. Leaf litter and log bark carry heavy faceted mosaic texture artefact. | REPLACE |
| `open-lakeshore` | Calm lake with reeds and a forested far shore, no birds or animals. Foreground boulders carry faceted mosaic texture artefact; the rest is clean. | REPLACE |
| `otter-waterway` | Clear forest stream with a holt under a root, no otter. Water, banks and foliage carry heavy faceted mosaic texture artefact. | REPLACE |
| `placer-gold-gravel-bar` | Gravel bar with gold flecks beside a river, nothing alive. Stones and gravel carry faceted mosaic texture artefact. | REPLACE |
| `plains-highland-foothill` | Grassy slope rising to a rocky hill, nothing alive besides plants. Rocks, grass and the hillside carry faceted mosaic texture artefact. | REPLACE |
| `plains` | Misty wildflower grassland towards a forest edge, nothing alive besides plants. Foreground rocks carry faceted mosaic texture artefact. | REPLACE |
| `pumice-basalt-volcanic-scree` | Vesicular pumice and basalt scree on a volcanic ridge, nothing alive. The large boulders carry faceted mosaic texture beyond the natural vesicles. | REPLACE |
| `raptor-cliff-nesting-range` | Stick nest on a cliff ledge over a river valley, no raptor. Rock and path carry heavy faceted mosaic texture artefact; a tiny dark speck at the top edge (right of centre) could be a distant bird. | REPLACE |
| `raven-scavenging-range` | Known corrected case: carcass skeleton and a bleached dead tree on an upland, no raven visible. Foreground boulders carry mild faceted mosaic texture artefact. | REPLACE |
| `reed-root-marsh` | Pulled reed roots and lily pads at a misty pool, nothing alive besides plants. Mud and water surface carry faceted mosaic texture artefact. | REPLACE |
| `reptile-basking-heath` | Heather heath with flat basking rocks and a path, no reptiles. Rocks and path carry heavy faceted mosaic texture artefact. | REPLACE |
| `ridge-stalker-ambush-ground` | Misty ridge path with bark-stripped trunks (habitat evidence), no creature. Path and boulders carry faceted mosaic texture artefact. | REPLACE |
| `river-floodplain` | Braided river across a green floodplain, nothing alive besides plants. Foreground boulders and mud carry faceted mosaic texture artefact. | REPLACE |
| `river-ford` | Shallow forest river ford over gravel, nothing alive. Water and gravel carry heavy faceted mosaic texture artefact. | REPLACE |
| `rock-salt-exposure` | Banded salt seam under a rock overhang, nothing alive. Rock faces and salt fall carry heavy faceted mosaic texture artefact. | REPLACE |
| `rounded-hammerstone-cobble-bar` | River cobble bar below mountains, nothing alive. Every cobble carries faceted mosaic texture artefact. | REPLACE |
| `ruin-archive-interior` | Collapsed archive hall with empty shelves and vines, nothing alive. Every surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `salt-marsh` | Salt-crusted marsh pools under storm cloud, nothing alive besides plants. The salt crust and mud carry faceted mosaic texture artefact. | REPLACE |
| `shell-beach` | Grey beach with shells, wrack and driftwood, nothing alive. The sand surface carries faceted mosaic texture artefact. | REPLACE |
| `shelter-grove` | Dim conifer grove with a fallen trunk, nothing alive besides plants. Leaf litter and foliage carry heavy faceted mosaic texture artefact. | REPLACE |
| `silver-lead-exposure` | Galena-bearing rock face with quartz veins, nothing alive. The rock wall at left carries faceted mosaic texture artefact. | REPLACE |
| `soapstone-outcrop` | Green soapstone slabs on a mountain slope, nothing alive. Every rock surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `squirrel-canopy-range` | Tall conifer forest path, no squirrels visible. Bark and leaf litter carry heavy faceted mosaic texture artefact. | REPLACE |
| `sulphur-deposit` | Steaming fumarole field with yellow sulphur crust below peaks, nothing alive. Every ground surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `thornback-wallow` | Churned mud wallow with claw furrows and a scored trunk (habitat evidence), no creature. Every surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `tin-exposure` | Dark tin-bearing rock outcrop on a mountain slope, nothing alive. Every rock surface carries heavy faceted mosaic texture artefact. | REPLACE |
| `trout-spawning-run` | Mountain stream riffles over boulders, no fish visible. Water and boulders carry heavy faceted mosaic texture artefact. | REPLACE |
| `underground-karst-stream` | Cave river passage with stalactites, nothing alive. Cave walls and ledges carry faceted mosaic texture artefact. | REPLACE |
| `waterfowl-nesting-ground` | Flattened reed nest platform at a misty pool (habitat evidence), no birds. Reeds, mud and water carry heavy faceted mosaic texture artefact. | REPLACE |
| `wetland-willow-carr` | Flooded willow carr, nothing alive besides plants. Duckweed, water and roots carry heavy faceted mosaic texture artefact. | REPLACE |
| `wild-herb-grove` | Forest understory of flowering herbs and ferns, nothing alive besides plants. Foliage, litter and boulders carry faceted mosaic texture artefact. | REPLACE |
| `wild-mast-nut-grove` | Beech and chestnut forest floor with fallen nuts and husks, nothing alive besides plants. Litter, bark and boulders carry heavy faceted mosaic texture artefact. | REPLACE |
| `woodland-spring` | Spring cascade into a pool among ferns, nothing alive besides plants. Water, pebbles and boulders carry faceted mosaic texture artefact. | REPLACE |

## No finding (50)

| backdrop | finding | disposition |
|---|---|---|
| `ancient-observatory` | Ruined stone circle and arches under mountains; nothing alive, clean rendering. | HUMAN_APPROVAL |
| `archipelago-shoreline` | Tidal shore with wooded islets; no birds or animals visible. | HUMAN_APPROVAL |
| `arctic-fox-snowfield-den` | Known corrected case: snow den mouth with a line of paw tracks, and no fox. Habitat evidence only. | HUMAN_APPROVAL |
| `ash-hound-den` | Dark rock overhang and worn path; no creature or silhouette visible. | HUMAN_APPROVAL |
| `beaver-colony` | Lodge, dam and gnawed stumps in a misty pond; no beaver. Habitat evidence only. | HUMAN_APPROVAL |
| `blackberry-thorn-brush` | Bramble thicket with ripe fruit; nothing alive besides plants. | HUMAN_APPROVAL |
| `bog-iron-iron-sand-bar` | Rust-streaked bog margin under rain; no animals. Cracked mud texture reads as natural. | HUMAN_APPROVAL |
| `bog-medicinal-toxic-flora` | Misty bog with herbs and pools; nothing alive besides plants. | HUMAN_APPROVAL |
| `broken-aqueduct` | Collapsed stone aqueduct across a valley; nothing alive, clean rendering. | HUMAN_APPROVAL |
| `buried-granary` | Earth-roofed store rooms with storage jars and broken sherds; nothing alive. | HUMAN_APPROVAL |
| `clay-deposit` | Red clay bank beside a forest stream; nothing alive. Painterly but clean. | HUMAN_APPROVAL |
| `collapsed-causeway` | Broken stone causeway across a marsh towards a ruined hill-fort; nothing alive, clean rendering. | HUMAN_APPROVAL |
| `crayfish-stony-shallows` | Clear stony stream shallows; no crayfish or fish visible. Painterly, clean. | HUMAN_APPROVAL |
| `cricket-night-meadow` | Moonlit meadow at a forest edge; nothing alive visible. | HUMAN_APPROVAL |
| `deep-peat-bog` | Dark misty peat bog with hummocks and stumps; nothing alive visible. | HUMAN_APPROVAL |
| `deep-slow-river-pool` | Green forest pool with fallen logs; no fish or animals visible. | HUMAN_APPROVAL |
| `earthworm-rich-forest-soil` | Known corrected case: bare dark soil with fallen maple leaves, no earthworms visible. Clean rendering. | HUMAN_APPROVAL |
| `fen-siren-pool` | Dark misty fen pool among reeds, no creature or figure visible. Mild painterly texture only. | HUMAN_APPROVAL |
| `fibre-grassland` | Sunlit seed-grass meadow with daisies; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `flooded-archive` | Flooded ruin interior with shelving and fallen stone; ripples only, nothing alive. Clean rendering. | HUMAN_APPROVAL |
| `forest-grassland-ecotone` | Sunlit forest edge opening onto meadow; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `forest` | Misty old forest with fallen log and stream; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `forest-wetland-ecotone` | Mossy forest slope into a sedge swamp; nothing alive besides plants. Only faint texture on the water. | HUMAN_APPROVAL |
| `gloom-moth-colony` | Hollow log tunnel with hanging cocoons (habitat evidence), no moths visible. Clean rendering. | HUMAN_APPROVAL |
| `gopher-colony` | Meadow pocked with burrow mounds (habitat evidence), no gophers. Mild texture on grass only. | HUMAN_APPROVAL |
| `grassland-coastal-dune` | Grassy dunes along an empty beach under mountains; no birds or animals. Clean rendering. | HUMAN_APPROVAL |
| `grassland-wetland-ecotone` | Flooded grassland pools below snowy hills; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `highland-mountain-snowline` | Heather slope rising to snowy peaks; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `mushroom-hollow` | Fungi and bracket shelves around a fallen log in old forest; fungi are not animals, nothing else alive. Clean rendering. | HUMAN_APPROVAL |
| `nocturnal-owl-woodland` | Known corrected case: moonlit clearing framed by oaks with an empty trunk hollow, no owl. Clean rendering. | HUMAN_APPROVAL |
| `open-ocean` | Open sea under cumulus sky; no birds, fish or animals. Clean rendering. | HUMAN_APPROVAL |
| `overgrown-watchtower` | Ivy-clad ruined round tower in forest; nothing alive. Clean rendering. | HUMAN_APPROVAL |
| `precision-tool-stone-exposure` | Flint nodules eroding from a chalk bank below peaks; nothing alive. Conchoidal fracture is the stone itself, not an artefact. | HUMAN_APPROVAL |
| `quarry` | Pale rock quarry basin with a green pool and pines; nothing alive. Clean painterly rendering. | HUMAN_APPROVAL |
| `reedkin-river-isle-settlement` | Empty stilt huts, woven walkway and hung nets over reed water; no people or reedkin. Clean rendering. | HUMAN_APPROVAL |
| `refractory-clay-bed` | Pale layered clay bank beside a forest stream; nothing alive. Painterly, clean. | HUMAN_APPROVAL |
| `sandstone-abrasive-outcrop` | Layered sandstone ledge beside clear water; nothing alive. Clean rendering. | HUMAN_APPROVAL |
| `seabird-island-colony` | Sea stacks streaked white with guano (habitat evidence), no birds visible. Clean rendering. | HUMAN_APPROVAL |
| `sheltered-lake-reed-fishery` | Clear reedy lake shallows in a forested valley; no fish or birds visible. Only faint texture on a few foreground stones. | HUMAN_APPROVAL |
| `stream` | Painterly forest stream over mossy rocks; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `tidal-flat` | Wet tidal flat with wrack and sea stacks under storm light; no birds or animals. Only faint texture in the foreground seaweed. | HUMAN_APPROVAL |
| `undercut-riverbank-catfish-eel` | Known corrected case: undercut bank with exposed roots over dark water and a submerged log; no fish or eels visible. Clean rendering. | HUMAN_APPROVAL |
| `warren-ground` | Burrow holes under a gorse mound in a misty meadow (habitat evidence), no rabbits. Only faint texture. | HUMAN_APPROVAL |
| `wetland` | Misty marsh pools with bulrushes and moss hummocks at dawn; nothing alive besides plants. Only faint texture. | HUMAN_APPROVAL |
| `wild-fibre-meadow` | Flowering meadow of flax and nettles above a river valley; nothing alive besides plants. Clean rendering. | HUMAN_APPROVAL |
| `wild-grain-seed-grass-stand` | Golden seed-grass valley below limestone cliffs; nothing alive besides plants. Clean painterly rendering. | HUMAN_APPROVAL |
| `wild-water-buffalo-range` | Hoof-churned mud trail through wet grassland towards karst peaks (habitat evidence), no buffalo. Clean rendering. | HUMAN_APPROVAL |
| `wooded-riverbank` | Misty conifer river with mossy boulders; nothing alive besides plants. Only faint texture on the water surface. | HUMAN_APPROVAL |
| `woodland-mesopredator-territory` | Known corrected case: mossy old-growth path with a root hollow and fallen log, no animal. Clean rendering. | HUMAN_APPROVAL |
| `wyvern-roost` | Overhung cliff ledge above a sea of cloud with a scraped nest hollow (habitat evidence), no creature. Clean rendering. | HUMAN_APPROVAL |

## What the gate now refuses

`npm run backdrops:check` / `backdrops:test` fail when:
- an image with a CREATURE or AMBIGUOUS finding is anything but `QUARANTINED`;
- a review is APPROVED over an unresolved blocking finding, or APPROVED by automated vision;
- a FLAGGED review gives no finding;
- a quarantined image is still in the legacy list, or claims `creatureFree`;
- any record falls back to a quarantined image, so resolution can never walk around the quarantine.

No quarantined key is a fallback target today, and no Java or TypeScript source names one, so quarantining them changes nothing a Chronicle currently sees. It keeps the future loader (#238) from ever selecting them.
