-- #134 — the iron pickaxe and the iron fish hook.
--
-- Mining and angling code have read these two for as long as the bronze ones: gatherMineral grants a metal pick's
-- finer nodule and its striking gate to 'bronze_pickaxe' OR 'iron_pickaxe', the ore-grade step up reads both, and a
-- fishing line's hook check and its +10 catch read 'bronze_fish_hook' OR 'iron_fish_hook'. Neither iron key was ever in
-- the catalogue. Iron is smelted to a bloom and forged into an axe (V151), so the metal was there and the reads were
-- there, and the one step between them — forging the tool — was missing. These are forged exactly as the bronze ones
-- are (V153, V157), from the iron bloom the axe is forged from.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('iron_pickaxe',   'Iron pickaxe',   'TOOL', 1150, 800, FALSE, TRUE,  0),
('iron_fish_hook', 'Iron fish hook', 'TOOL',   40,  15, TRUE,  FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('iron_pickaxe',   'TECHNIQUE', 'an iron bloom hammered free of slag, drawn to a pick-head, and hafted'),
('iron_fish_hook', 'TECHNIQUE', 'iron drawn to wire, bent, and filed to a barbed hook')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('iron_pickaxe','HAND_RIGHT','CARRIED'), ('iron_pickaxe','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- 'forge' -> CRAFT (V144). The iron subject keeps each clear of its bronze sibling; neither says 'make'/'craft' +
-- 'pickaxe', so the CRAFT_PICKAXE intent is not in play (V153).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_iron_pickaxe', 'Forge an iron pickaxe', 'iron_pickaxe', 1,1, 'STRIKING', TRUE, FALSE, 120, 'tools', 'CRAFT',
 'forge an iron pickaxe,forge a iron pickaxe,forge iron pickaxe,forge an iron pick,hammer out an iron pickaxe,work the iron into a pick,iron pickaxe',
 'You hammer the glowing bloom until the slag runs out of it, draw the iron to a heavy pick-head with a hard point, and haft it tight — it breaks a nodule out of the rock whole.', 'VERIFIED', now()),
('forge_iron_fish_hook', 'Forge iron fish hooks', 'iron_fish_hook', 3,6, 'STRIKING', TRUE, FALSE, 60, 'tools', 'CRAFT',
 'forge an iron fish hook,forge iron fish hooks,make iron fish hooks,hammer out iron fish hooks,file iron fish hooks,iron fish hook',
 'You draw the iron out to a fine wire, cut and bend it, and file each piece to a barbed point — a handful of hooks that will not straighten on a heavy fish.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_iron_pickaxe', 'iron_bloom', 1), ('forge_iron_pickaxe', 'dry_branch', 1),
('forge_iron_fish_hook', 'iron_bloom', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_iron_pickaxe','iron'), ('forge_iron_pickaxe','pickaxe'), ('forge_iron_pickaxe','pick'),
('forge_iron_fish_hook','iron'), ('forge_iron_fish_hook','hook'), ('forge_iron_fish_hook','hooks')
ON CONFLICT DO NOTHING;

DO $$
DECLARE bad text;
BEGIN
  -- Conservation: a forge may lose slag and scale, never make iron.
  SELECT string_agg(process_key, ', ') INTO bad FROM process_mass_balance
   WHERE process_key IN ('forge_iron_pickaxe','forge_iron_fish_hook') AND max_output_grams > min_input_grams;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V317: forging makes matter: %', bad; END IF;

  -- No keyword of the new forges may already belong to another process.
  SELECT string_agg(DISTINCT k, ', ') INTO bad FROM (
    SELECT trim(unnest(string_to_array(keywords, ','))) k, process_key FROM material_process) x
   WHERE x.process_key NOT IN ('forge_iron_pickaxe','forge_iron_fish_hook')
     AND k IN (SELECT trim(unnest(string_to_array(keywords, ','))) FROM material_process WHERE process_key IN ('forge_iron_pickaxe','forge_iron_fish_hook'));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V317: keywords already taken: %', bad; END IF;
END $$;

-- Worn iron tools reforge back to a bloom like the axe and the cuirass (V160), so neither is a dead end. Reforging
-- only ever loses mass: two pickaxes (2300 g) or thirty-five hooks (1400 g) consolidate into one 1400 g bloom.
INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
('reforge_iron_scrap','iron_object','iron_pickaxe',2),
('reforge_iron_scrap','iron_object','iron_fish_hook',35)
ON CONFLICT (process_key, group_name, item_key) DO NOTHING;
