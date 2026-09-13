-- #160 / #155 — the iron sand bar.
--
-- Both tickets name it. #155's heavy-industry list requires "`iron_ore_vein`, `bog_iron_patch`, and `iron_sand_bar`"
-- as physically placed geology, and #160's coverage focus names iron sand among the materials that must not come
-- off unrelated ground. It is the one candidate on either list whose consumer already stands in the world: the
-- bloomery. Gold wants a gold chain, coal wants a coal chain; iron sand wants only a way to be dug and a smelt.
--
-- WHAT IT IS. Black sand — magnetite and ilmenite — sorted out of eroded rock by running water and waves, because it
-- is two to three times heavier than the quartz sand around it. It lies in dark streaks on the inside of river
-- bends and along beaches, and people have smelted it for iron wherever it collects: the Japanese tatara ran on it.
-- It is washed and scooped, not broken out of rock, so it takes no tool, like the river sand beside it.
--
-- WHY A PROVINCE. Heavy-mineral sand is a deposit, not a quality of every bank: it concentrates only where the
-- water sorts it. mineral_province binds it to its bar and gatherMineral will not give it anywhere else. The bar is
-- a new marker appended to WorldGenesisService.MARKER_SPECIFICATIONS on RIVER_BANK and COAST.
--
-- WHY A NEW SMELT, NOT A WIDER OLD ONE. smelt_iron takes iron ore by a fixed input, and widening it to "ore or sand"
-- would change a recipe every iron chain in play already runs. A second process leaves it exactly as it was. Sand
-- asks more of the furnace: fine grains are carried up and out of the shaft on the blast before they reduce, so it
-- takes three measures of sand to the bloom where lump ore takes two. 4.5 kg of sand for a 1.4 kg bloom is within
-- what iron sand yields, and far from matter out of nothing.
--
-- KEYWORDS. "smelt iron" belongs to smelt_iron. Every keyword here names the sand, so it is longer than any of
-- smelt_iron's and never ties with it (#38): "smelt iron" still smelts ore, and only a text that says sand smelts sand.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable) VALUES
  ('iron_sand', 'Iron sand', 'MATERIAL', 1500, 500, TRUE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
  ('iron_sand', 'Iron sand', 'RIVER_BANK,COAST', 0.35, NULL, 1, 3,
   'Black, heavy sand washed out of the rock and sorted into streaks where the water slackens. A magnet would pull it; a smelter will take iron from it.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
  ('iron_sand', 'MINERAL', 'black heavy-mineral sand scooped from an iron sand bar on a river bend or a beach')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO mineral_province (mineral_key, site_kind, site_biomes, notes) VALUES
  ('iron_sand', 'Iron sand bar', 'RIVER_BANK,COAST',
   'Heavy-mineral sand concentrates only where water sorts it out of lighter quartz: the inside of a bend, a beach face. Not every bank.')
ON CONFLICT (mineral_key, site_kind) DO NOTHING;

INSERT INTO material_process
  (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
   duration_minutes, domain_key, keywords, narration, review_state, reviewed_at, category_key, station_kind)
VALUES
  ('smelt_iron_sand', 'Smelt iron sand', 'iron_bloom', 1, 1, NULL, TRUE, FALSE,
   130, 'items',
   'smelt the iron sand,smelt iron sand,smelt the black sand,smelt black sand,reduce the iron sand,bloom the iron sand',
   'You feed the black sand into the shaft a little at a time between layers of charcoal, so the blast does not carry it straight up and out, and work the bellows until a spongy bloom settles in the hearth — slower than lump ore, and hungrier for sand, but iron all the same.',
   'VERIFIED', now(), 'PROCESS', 'bloomery_furnace')
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
  ('smelt_iron_sand', 'iron_sand', 3), ('smelt_iron_sand', 'charcoal', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
  ('smelt_iron_sand', 'sand'), ('smelt_iron_sand', 'iron sand'), ('smelt_iron_sand', 'black sand'), ('smelt_iron_sand', 'bloom')
ON CONFLICT DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  -- Conservation: a smelt loses slag, never makes iron.
  SELECT count(*) INTO n FROM process_mass_balance WHERE process_key = 'smelt_iron_sand' AND max_output_grams > min_input_grams;
  IF n > 0 THEN RAISE EXCEPTION 'V320: smelting iron sand makes matter'; END IF;

  -- The province can meet the mineral's own affinity (V306's first guard, for this row).
  IF NOT EXISTS (SELECT 1 FROM mineral_province p JOIN mineral_definition m USING (mineral_key),
                 unnest(string_to_array(p.site_biomes, ',')) sb
                 WHERE p.mineral_key = 'iron_sand' AND btrim(sb) = ANY (string_to_array(m.biome_affinity, ','))) THEN
    RAISE EXCEPTION 'V320: the iron sand bar stands on ground iron sand is not declared for';
  END IF;

  -- smelt_iron is untouched: same inputs, same output, same keywords.
  SELECT string_agg(item_key || 'x' || quantity, ',' ORDER BY item_key) INTO bad FROM material_process_input WHERE process_key = 'smelt_iron';
  IF bad IS DISTINCT FROM 'charcoalx3,iron_orex2' THEN RAISE EXCEPTION 'V320: smelt_iron changed (%)', bad; END IF;

  -- No keyword of the sand smelt may already belong to another process, so nothing new can tie.
  SELECT string_agg(DISTINCT k, ', ') INTO bad FROM (
    SELECT trim(unnest(string_to_array(keywords, ','))) k, process_key FROM material_process) x
   WHERE x.process_key <> 'smelt_iron_sand'
     AND k IN (SELECT trim(unnest(string_to_array(keywords, ','))) FROM material_process WHERE process_key = 'smelt_iron_sand');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V320: keywords already taken: %', bad; END IF;
END $$;
