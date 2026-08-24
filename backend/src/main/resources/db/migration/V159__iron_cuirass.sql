-- V159: iron cuirass — the top defensive tier of the metal ladder (EPIC #180 heavy industry / #186 iron).
--
-- The bronze cuirass (V158) gave defence its first metal rung; iron gives it the harder one, exactly as the EDGE
-- ladder runs stone < copper < bronze < iron < steel. Iron armour turns a blow better than bronze — harder metal,
-- a keener temper — which is why bronze harness gave way to iron. So the defence side of the ladder now mirrors the
-- offence side: a Chronicle who wins iron protects with it as well as fights with it. (The tiered blunting read is
-- in WildlifeEncounterService: bronze cuirass +12, iron cuirass +20, above scale/war-shield +7 and leather +4.)
--
-- Forged from three iron blooms hammered free of slag and beaten to shape over a form — heavier than the bronze
-- plate, and it loses mass to the fire as slag, so it makes no matter from nothing.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('iron_cuirass', 'Iron cuirass', 'CLOTHING', 2600, 6200, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('iron_cuirass', 'TECHNIQUE', 'iron blooms hammered free of slag, forged in sheets, and fitted over the chest')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Worn over the torso as protection, like the bronze cuirass.
INSERT INTO item_equipment_compatibility (item_key, body_position, layer) VALUES
('iron_cuirass','TORSO','PROTECTION')
ON CONFLICT DO NOTHING;

-- Forged from three iron blooms ('forge'->CRAFT, V144); the cuirass/breastplate subject keeps it distinct from the axe.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('forge_iron_cuirass', 'Forge an iron cuirass', 'iron_cuirass', 1,1, 'STRIKING', TRUE, FALSE, 170, 'tools', 'CRAFT',
 'forge an iron cuirass,forge a iron cuirass,forge iron cuirass,hammer out an iron cuirass,hammer out an iron breastplate,make an iron cuirass,iron cuirass,iron breastplate',
 'You hammer three blooms free of their slag and weld them out into broad sheets, beating each over a form into a breastplate and backplate and riveting them close over the chest — iron plate that turns a blow no bronze could.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('forge_iron_cuirass', 'iron_bloom', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('forge_iron_cuirass','iron'), ('forge_iron_cuirass','cuirass'), ('forge_iron_cuirass','breastplate')
ON CONFLICT DO NOTHING;
