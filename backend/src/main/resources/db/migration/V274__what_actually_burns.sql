-- V274 — fire reaches everything that burns (#219).
--
-- An unattended roaring fire scorches what stands beside it, and the list of what could catch was three
-- hardcoded project kinds in Java: LEAN_TO, FUEL_RACK, BRUSH_FENCE. The catalogue has since grown to 45
-- construction kinds, twenty-odd of them built out of thatch, reed, brush, bark, hide and untrimmed timber — a
-- reed hut, a bark cabin, a debris hut, a hay rack, a fodder store, a wattle fence. Every one of them stood
-- beside a roaring hearth in dry weather completely fireproof, because its name was not in that list.
--
-- This is the same shape as is_shelter and the tool lists before it: a hand-written list in code that the data
-- outgrew. Flammability becomes a property of the kind, so a new structure declares whether it burns and the
-- hazard finds it without anyone remembering to edit Java.
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS flammable boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN construction_kind.flammable IS
    'Whether this kind can be scorched by a roaring fire on the same ground (#219). Thatch, reed, brush, bark, hide and dry timber burn; stone, earth, snow and open pits do not.';

-- What burns: anything made of plant matter, bark, hide or dry timber.
UPDATE construction_kind SET flammable = true WHERE project_kind IN (
    'BARK_CABIN','BARK_SHELTER','BRUSH_DOME','BRUSH_FENCE','DEBRIS_HUT','FODDER_STORE','FUEL_RACK','HAY_RACK',
    'HIDE_LODGE','HIDE_TENT','LEANING_BRANCH_SHELTER','LEAN_TO','LOG_SHELTER','REED_HUT','SEWING_TABLE',
    'STONEWORKING_TABLE','STORAGE_AREA','TETHER_LINE','TOOL_SHED','WATTLE_FENCE','WEAVING_TABLE',
    'WINDWARD_SCREEN','WOODWORKING_TABLE','ARCHIVE_SHELF','CAMP_ALARM','HITCHING_POST','KNOWLEDGE_STATION',
    'LOOKOUT','SMOKE_VENT');

-- What does not: stone, earth, snow, water and holes in the ground. Named explicitly so the reasoning is on the
-- record rather than left as an absence — a daub wall is earth over a frame and does not carry flame; a snow
-- shelter beside a fire has a different problem entirely.
UPDATE construction_kind SET flammable = false WHERE project_kind IN (
    'ANIMAL_PEN','CAVE_NICHE','COMPOST_BAY','COOL_FOOD_PIT','DAUB_WALL','DRY_STONE_WALL','EARTH_BERM_WALL',
    'FISHING_WEIR','LATRINE','MANURE_PIT','PIT_HOUSE','ROOT_CELLAR','SNOW_SHELTER','STONE_FIRE_PIT',
    'STONE_WALL_LOW','WATERING_STATION');
