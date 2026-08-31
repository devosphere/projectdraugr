-- V213 — story #93 catalogue batch 6: projectile accessories. Six items that make the ranged kit complete and
-- carriable, each functional through an existing system — pure data:
--   sling_stone          — WEAPON ammunition, weapon_profile THROWN_STONE (cast harder from a sling in confront)
--   sling_stone_pouch     } CONTAINER — carry the ammunition/arrows (container_capacity_default), worn at waist/back
--   simple_quiver / back_quiver }
--   arrow_straightener   — carried tool that EASES shaft-straightening (station_kind on shave_arrow_shafts)
--   arrow_fletching_jig  — carried tool that EASES arrow-making (station_kind on the V211 arrow recipes)
-- The two tools and the sling stone are equippable (dead-end-clean); the pouch/quivers are CONTAINERs (exempt).
-- station_kind only ever EASES a process, never gates it. Routing verified locally. Keywords hyphen-free.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('sling_stone',         'Sling stone',         'WEAPON',    60,  40,  TRUE,  TRUE, 0),
('sling_stone_pouch',   'Sling stone pouch',   'CONTAINER', 200, 700, FALSE, TRUE, 0),
('simple_quiver',       'Simple quiver',       'CONTAINER', 250, 3000,FALSE, TRUE, 0),
('back_quiver',         'Back quiver',         'CONTAINER', 400, 5000,FALSE, TRUE, 0),
('arrow_straightener',  'Arrow straightener',  'TOOL',      80,  60,  FALSE, TRUE, 0),
('arrow_fletching_jig', 'Arrow fletching jig', 'TOOL',      200, 500, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('sling_stone',        'TECHNIQUE', 'a rounded stone shaped to fly true from a sling'),
('sling_stone_pouch',  'TECHNIQUE', 'a small sewn pouch for sling stones'),
('simple_quiver',      'TECHNIQUE', 'a sewn tube to carry arrows at the waist'),
('back_quiver',        'TECHNIQUE', 'a larger sewn quiver worn across the back'),
('arrow_straightener', 'TECHNIQUE', 'a grooved bone tool for truing a warped shaft'),
('arrow_fletching_jig','TECHNIQUE', 'a notched jig that holds a shaft square for fletching')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('sling_stone_pouch', 2000, 1500),
('simple_quiver',     2000, 2500),
('back_quiver',       4000, 5000)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('sling_stone','HAND_RIGHT','CARRIED'),('sling_stone','HAND_LEFT','CARRIED'),
('sling_stone_pouch','WAIST','ATTACHED'),('sling_stone_pouch','BACK','CARRIED'),
('simple_quiver','WAIST','ATTACHED'),('simple_quiver','BACK','CARRIED'),
('back_quiver','BACK','CARRIED'),('back_quiver','WAIST','ATTACHED'),
('arrow_straightener','HAND_RIGHT','CARRIED'),('arrow_straightener','HAND_LEFT','CARRIED'),
('arrow_fletching_jig','HAND_RIGHT','CARRIED'),('arrow_fletching_jig','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('shape_sling_stone',      'Shape sling stones',       'sling_stone',        1,3,NULL,FALSE,FALSE,15,'tools','PROCESS',
 'shape sling stones,shape a sling stone,sling stone',
 'You knock a hard stone round and smooth so it flies true — a sling stone that carries farther than a rough pebble.','VERIFIED',now()),
('sew_sling_pouch',        'Sew a sling stone pouch',  'sling_stone_pouch',  1,1,NULL,FALSE,FALSE,30,'tools','CRAFT',
 'sew a sling stone pouch,sew a sling pouch,sling stone pouch,sling pouch',
 'You cut and stitch a small leather pouch to hold a handful of sling stones ready at the waist.','VERIFIED',now()),
('sew_simple_quiver',      'Sew a simple quiver',      'simple_quiver',      1,1,NULL,FALSE,FALSE,40,'tools','CRAFT',
 'sew a simple quiver,stitch a simple quiver,simple quiver',
 'You sew a leather tube with a strap — a plain quiver to carry arrows at the hip.','VERIFIED',now()),
('sew_back_quiver',        'Sew a back quiver',        'back_quiver',        1,1,NULL,FALSE,FALSE,50,'tools','CRAFT',
 'sew a back quiver,stitch a back quiver,back quiver',
 'You sew a longer, stiffened quiver with a shoulder strap to carry many arrows across the back.','VERIFIED',now()),
('carve_arrow_straightener','Carve an arrow straightener','arrow_straightener',1,1,NULL,FALSE,FALSE,30,'tools','CRAFT',
 'carve an arrow straightener,carve a shaft straightener,arrow straightener,shaft straightener',
 'You groove a length of bone into a straightener — worked warm over a shaft, it trues the wood dead straight.','VERIFIED',now()),
('carve_fletching_jig',    'Carve a fletching jig',    'arrow_fletching_jig',1,1,NULL,FALSE,FALSE,35,'tools','CRAFT',
 'carve a fletching jig,carve an arrow fletching jig,fletching jig,arrow fletching jig',
 'You notch a jig that grips a shaft square and spaces the vanes evenly — fletching comes out true and quick.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('shape_sling_stone',       'field_stone',1),
('sew_sling_pouch',         'tanned_leather',1),
('sew_simple_quiver',       'tanned_leather',1),
('sew_back_quiver',         'tanned_leather',1),
('carve_arrow_straightener','animal_bone',1),
('carve_fletching_jig',     'dry_branch',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('shape_sling_stone','sling stone'),
('sew_sling_pouch','sling stone pouch'),('sew_sling_pouch','sling pouch'),
('sew_simple_quiver','simple quiver'),
('sew_back_quiver','back quiver'),
('carve_arrow_straightener','arrow straightener'),('carve_arrow_straightener','shaft straightener'),
('carve_fletching_jig','fletching jig'),('carve_fletching_jig','arrow fletching jig')
ON CONFLICT DO NOTHING;

-- Combat function: a shaped sling stone is thrown-stone ammunition (a sling multiplies its cast in confront).
INSERT INTO weapon_profile (item_key, combat_role, edge_tier, envenomed) VALUES
('sling_stone', 'THROWN_STONE', 'PLAIN', FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- Tool functions: the straightener and jig EASE the shaft/arrow work (station_kind never gates, only eases).
UPDATE material_process SET station_kind='arrow_straightener' WHERE process_key='shave_arrow_shafts';
UPDATE material_process SET station_kind='arrow_fletching_jig'
 WHERE process_key IN ('fletch_fletched_arrow','fit_broadhead_arrow','make_blunt_arrow');
