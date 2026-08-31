-- V222 — story #93 catalogue batch 15: a fish weir and a shellfish pry. Two more of the #51-75 group made functional:
--   weir_net_panel     — a woven withy weir; carried, fish() fishes the stretch by the NET method (chance 72)
--   shellfish_pry_tool — a stout bone pry for opening shellfish; a CUTTING tool (tool_profile)
-- Craft phrasing: 'carve a fish weir' is CRAFT (carve 2 beats fish's HUNT 1; FISH defers to the matched process),
-- and 'carve a shellfish pry tool' is CRAFT ('shellfish' is not the whole word 'fish'; 'pry' is a weak ACQUIRE term
-- that carve outweighs). Verified locally vs matcher + hard-intent + precedence. The weir is read only by fish()
-- (CODE_TERMINAL); the pry is equippable (dead-end-clean).

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('weir_net_panel',     'Weir panel',        'MATERIAL', 700, 4000, FALSE, FALSE, 0),
('shellfish_pry_tool', 'Shellfish pry',     'TOOL',     120, 100,  FALSE, TRUE,  0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('weir_net_panel',     'TECHNIQUE', 'a woven withy weir panel set across a stretch'),
('shellfish_pry_tool', 'TECHNIQUE', 'a stout bone pry for opening shellfish')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('shellfish_pry_tool','HAND_RIGHT','CARRIED'),('shellfish_pry_tool','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carve_fish_weir',     'Carve a fish weir',        'weir_net_panel',     1,1,NULL,FALSE,FALSE,55,'tools','CRAFT',
 'carve a fish weir,build a fish weir from withies,fish weir,weir panel',
 'You weave withies into a stiff weir panel — set across a stream, it funnels and holds fish like a net.','VERIFIED',now()),
('carve_shellfish_pry', 'Carve a shellfish pry',    'shellfish_pry_tool', 1,1,NULL,FALSE,FALSE,20,'tools','CRAFT',
 'carve a shellfish pry tool,carve a shellfish pry,shellfish pry,shell pry',
 'You grind a stout bone to a blunt wedge — a pry for levering open a mussel or a clam without spilling it.','VERIFIED',now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carve_fish_weir',     'hazel_rod',2),
('carve_shellfish_pry', 'animal_bone',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carve_fish_weir','fish weir'),('carve_fish_weir','weir panel'),
('carve_shellfish_pry','shellfish pry'),('carve_shellfish_pry','shell pry')
ON CONFLICT DO NOTHING;

-- The shellfish pry is a cutting/prying tool (a CUTTING-class edge opens a shell).
INSERT INTO tool_profile (item_key, tool_class) VALUES ('shellfish_pry_tool','CUTTING')
ON CONFLICT (item_key, tool_class) DO NOTHING;
