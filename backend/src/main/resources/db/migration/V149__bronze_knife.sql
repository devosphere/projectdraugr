-- V149: bronze knife — the era's everyday cutting tool (EPIC #180 / #185 bronze objects).
--
-- Bronze made the best weapons (spear) and heavy tools (axe); it also made the keenest small blade. A bronze knife
-- forged from one ingot is a proper CUTTING tool — it serves any knife work, and its fine, edge-holding blade makes
-- cleaner cuts of hide and fish than a knapped or bone edge, lifting the workmanship of that close cutting one
-- grade (read in PhysicalItemService.executeProcess). Completes the bronze tool set beside the spear and axe.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('bronze_knife', 'Bronze knife', 'TOOL', 280, 200, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bronze_knife', 'TECHNIQUE', 'a bronze blade cast, hammered keen, and hafted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('bronze_knife','HAND_RIGHT','CARRIED'), ('bronze_knife','HAND_LEFT','CARRIED')
ON CONFLICT DO NOTHING;

-- Forge it from one ingot over a fire with a striking tool. 'forge'->CRAFT (V144); the 'knife'+'bronze' subjects
-- keep it distinct from the bronze spear (spear) and bronze axe (axe).
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_bronze_knife', 'Forge a bronze knife', 'bronze_knife', 1,1, 'STRIKING', TRUE, FALSE, 45, 'tools', 'CRAFT',
 'forge a bronze knife,forge bronze knife,cast a bronze knife,hammer out a bronze knife,work the bronze into a knife,bronze knife',
 'You cast a slim bronze blade, hammer and grind it to a fine keen edge, and rivet it to a handle — a knife that parts hide and fish cleaner than any stone flake and holds its edge far longer.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_bronze_knife', 'bronze_ingot', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_bronze_knife','bronze'), ('forge_bronze_knife','knife')
ON CONFLICT DO NOTHING;
