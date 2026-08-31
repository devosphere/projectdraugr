-- V224 — #153 draft-animal readiness for ox, water buffalo, donkey, and horse. Each is registered as a real
-- registry species (tameable, present in its biome via the #74 ambient mechanism) AND as a draft_species, so once
-- tamed and hitched to a travois/sledge/cart it adds its haul to carry capacity through the existing data-driven
-- draft mechanic (#100, PhysicalItemService.loadState JOINs draft_species). Pure data — the haulage, fatigue,
-- forage, and terrain systems all read these tables, so the new beasts work exactly as the aurochs/elk/reindeer do.
INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier, base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity) VALUES
('ox',            'MAMMALIA','HERBIVORE','DIURNAL','TERRESTRIAL','HUGE',  90, FALSE, FALSE, FALSE, 70, 'GRASSLAND,TEMPERATE_FOREST'),
('water_buffalo', 'MAMMALIA','HERBIVORE','DIURNAL','TERRESTRIAL','HUGE',  95, FALSE, FALSE, TRUE,  60, 'WETLAND,GRASSLAND'),
('donkey',        'MAMMALIA','HERBIVORE','DIURNAL','TERRESTRIAL','MEDIUM',40, FALSE, FALSE, FALSE, 75, 'GRASSLAND,HIGHLAND'),
('horse',         'MAMMALIA','HERBIVORE','DIURNAL','TERRESTRIAL','LARGE', 50, FALSE, FALSE, FALSE, 65, 'GRASSLAND')
ON CONFLICT (species_key) DO NOTHING;

-- Draft readiness — haul and bulk a tamed beast adds when hitched to a load-bed (grams / millilitres).
INSERT INTO draft_species (species_key, haul_bonus_grams, bulk_bonus_ml) VALUES
('ox',            280000, 380000),
('water_buffalo', 300000, 400000),
('donkey',         90000, 130000),
('horse',         180000, 240000)
ON CONFLICT (species_key) DO NOTHING;
