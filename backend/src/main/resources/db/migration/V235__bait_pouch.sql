-- V235 — story #145 (first hunting traps and field tools). The trap/field-tool set is otherwise complete: snare_loop
-- (simple snare), deadfall_weight_stone (figure-four deadfall), fish_spear, fish_trap (woven fish trap) are all built,
-- baiting is a full mechanic (bait_profile + the lure action consuming a carried bait into placed_lure), and a
-- tracking marker is the MARK action (a driven stake / carved blaze / stone cairn, sited as a persistent object).
-- The one missing named entry is a distinct *bait pouch*: a prepared, mixed bait a trapper makes and deploys, more
-- potent and longer-lasting than raw food. Pure data: a FOOD bait item (dead-end-exempt like the other food baits)
-- + a make process + a bait_profile row + source. 'make a bait pouch' beats sew_leather_pouch's bare 'pouch' keyword
-- on longest-match; the mix uses no 'in/into' + store verb so it never reads as STORE.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('bait_pouch', 'Bait pouch', 'FOOD', 150, 300, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('bait_pouch','TECHNIQUE','mixed by hand from worms and crushed berries into a scented lure')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('mix_bait_pouch','Mix a bait pouch','bait_pouch',1,1,NULL,FALSE,FALSE,15,'items','CRAFT','bait pouch,make a bait pouch,mix a bait pouch,prepare a bait pouch,scent bait', 'You crush worms and berries together into a rank, scented handful and work it into a small pouch of bait — enough to draw a wary animal in.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('mix_bait_pouch','earthworm',2),('mix_bait_pouch','wild_berries',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('mix_bait_pouch','bait pouch'),('mix_bait_pouch','bait')
ON CONFLICT DO NOTHING;

-- A strong, long-lasting omnivore lure — better than the raw foods (raw_game_meat potency 25/8h).
INSERT INTO bait_profile (item_key, draws_role, potency, hours_active) VALUES
('bait_pouch','OMNIVORE',26,12)
ON CONFLICT (item_key) DO NOTHING;
