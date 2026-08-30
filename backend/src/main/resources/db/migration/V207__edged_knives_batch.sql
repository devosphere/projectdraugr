-- V207 — story #93 catalogue batch 1: the edged knives. Five knives knapped or carved from real local stone and
-- bone, each a functional CUTTING tool (tool_profile, V206) — the honest use of a knife is to cut, scrape, and
-- butcher, NOT to fight big game like a spear (the story's "explicit use cases and limitations": a knife is not a
-- spear, just as it is not a felling axe). Each is a physical, equippable, hand-carried object made through the
-- material-process matcher. Pure data on the V205/V206 enablers — no Java.
--
-- Routing note: these are equippable, so the dead-end guard treats them as terminally useful by nature; their
-- CUTTING tool_profile row makes them work for the carve/scrape/butchery processes that call for a cutting edge.
-- Keyword note: 'knap'/'carve' dodge the Java CRAFT_KNIFE intent (which needs craft|make + knife), so they route to
-- these specific recipes. 'knap' is already a PROCESS category term (the existing knap_scraper/knap_tool_stone are
-- PROCESS), so the knapped knives are PROCESS too and win their category by longest keyword ("knap a flint knife"
-- beats a bare "knap"); the carved bone knife is CRAFT ('carve' is a CRAFT term). No new category term is added —
-- adding 'knap' to CRAFT would make plain "knap ..." ambiguous and could steal the incumbent knapping processes.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('flake_knife',    'Stone flake knife', 'TOOL',  80, 40, FALSE, TRUE, 0),
('flint_knife',    'Flint knife',       'TOOL', 150, 70, FALSE, TRUE, 0),
('chert_knife',    'Chert knife',       'TOOL', 160, 75, FALSE, TRUE, 0),
('obsidian_knife', 'Obsidian knife',    'TOOL', 140, 65, FALSE, TRUE, 0),
('bone_knife',     'Bone knife',        'TOOL', 130, 70, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('flake_knife',    'TECHNIQUE', 'a sharp flake struck from a flint core'),
('flint_knife',    'TECHNIQUE', 'a knapped flint blade hafted in the hand'),
('chert_knife',    'TECHNIQUE', 'a knapped chert blade hafted in the hand'),
('obsidian_knife', 'TECHNIQUE', 'a knapped obsidian blade — the keenest stone edge'),
('bone_knife',     'TECHNIQUE', 'a blade ground and carved from bone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('flake_knife','HAND_RIGHT','CARRIED'),('flake_knife','HAND_LEFT','CARRIED'),
('flint_knife','HAND_RIGHT','CARRIED'),('flint_knife','HAND_LEFT','CARRIED'),
('chert_knife','HAND_RIGHT','CARRIED'),('chert_knife','HAND_LEFT','CARRIED'),
('obsidian_knife','HAND_RIGHT','CARRIED'),('obsidian_knife','HAND_LEFT','CARRIED'),
('bone_knife','HAND_RIGHT','CARRIED'),('bone_knife','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('knap_flake_knife',   'Strike a flake knife',   'flake_knife',   1,1,NULL,FALSE,FALSE,15,'tools','PROCESS',
 'knap a flake knife,strike a flake knife,strike a stone flake,flake knife,flake blade',
 'You strike a long, keen flake from the core — an edge in the hand the moment it parts, sharp enough to cut and skin.','VERIFIED',now()),
('knap_flint_knife',   'Knap a flint knife',     'flint_knife',   1,1,NULL,FALSE,FALSE,30,'tools','PROCESS',
 'knap a flint knife,knap and haft a flint knife,flint knife,flint blade',
 'You knap a flint blade to a fine edge and bind it into a short haft that fills the palm — a knife that will cut and skin for a long while.','VERIFIED',now()),
('knap_chert_knife',   'Knap a chert knife',     'chert_knife',   1,1,NULL,FALSE,FALSE,30,'tools','PROCESS',
 'knap a chert knife,knap and haft a chert knife,chert knife,chert blade',
 'You work a chert nodule down to a hafted blade — a little coarser than flint, but a true cutting edge.','VERIFIED',now()),
('knap_obsidian_knife','Knap an obsidian knife', 'obsidian_knife',1,1,NULL,FALSE,FALSE,30,'tools','PROCESS',
 'knap an obsidian knife,knap and haft an obsidian knife,obsidian knife,obsidian blade',
 'You flake obsidian to an edge keener than any other stone and haft it — it cuts like nothing else, though it chips if abused.','VERIFIED',now()),
('carve_bone_knife',   'Carve a bone knife',     'bone_knife',    1,1,NULL,FALSE,FALSE,35,'tools','CRAFT',
 'carve a bone knife,grind a bone knife,bone knife,bone blade',
 'You split a length of bone and grind it to a slim, hard blade — no stone edge, but it holds a point and a working edge.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('knap_flake_knife',   'flint_stone',    1),
('knap_flint_knife',   'flint_stone',    1),('knap_flint_knife','plant_fiber',1),
('knap_chert_knife',   'chert_nodule',   1),('knap_chert_knife','plant_fiber',1),
('knap_obsidian_knife','obsidian_shard', 1),('knap_obsidian_knife','plant_fiber',1),
('carve_bone_knife',   'animal_bone',    1),('carve_bone_knife','plant_fiber',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('knap_flake_knife','flake knife'),('knap_flake_knife','flake'),
('knap_flint_knife','flint knife'),('knap_flint_knife','flint'),
('knap_chert_knife','chert knife'),('knap_chert_knife','chert'),
('knap_obsidian_knife','obsidian knife'),('knap_obsidian_knife','obsidian'),
('carve_bone_knife','bone knife'),('carve_bone_knife','bone')
ON CONFLICT DO NOTHING;

-- Each knife is a cutting tool (the many carve/scrape/butchery processes that call for a CUTTING edge can use it).
INSERT INTO tool_profile (item_key, tool_class) VALUES
('flake_knife','CUTTING'),('flint_knife','CUTTING'),('chert_knife','CUTTING'),
('obsidian_knife','CUTTING'),('bone_knife','CUTTING')
ON CONFLICT (item_key, tool_class) DO NOTHING;
