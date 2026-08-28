-- V195 — harness and yoke (EPIC #100 / #102 draft equipment, completing the set). A beast hitched to a vehicle pulled
-- it by whatever rough rig was to hand — and paid for it, chafing and straining and tiring fast. The harness and the
-- yoke are the gear that make the pull efficient: a padded breast-harness or a shaped ox-yoke spreads the load across
-- the beast's frame where it can bear it, so the animal works longer for the same haul before it flags. This lays them
-- as craftable draft gear; owning either eases how fast a worked beast tires (PhysicalItemService.workDraftBeasts
-- accrues less fatigue when the keeper has a harness or yoke). Not a gate — a beast will pull in a rough rig — but the
-- reward for making proper gear is a team that keeps working. The last of #102's equipment set: travois, sledge, cart,
-- pack-saddle, harness, yoke.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('draft_harness', 'Draft harness', 'TOOL', 500,  2000, FALSE, FALSE, 0),
('draft_yoke',    'Ox-yoke',       'TOOL', 1200, 3000, FALSE, FALSE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('draft_harness', 'TECHNIQUE', 'stitched and padded into a breast-harness of strap and cord'),
('draft_yoke',    'TECHNIQUE', 'shaped from a beam to sit two beasts'' necks, bows and pins fitted')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('make_draft_harness', 'Make a draft harness', 'draft_harness', 1, 1, NULL, FALSE, FALSE, 55, 'items', 'CRAFT',
 'make a harness,draft harness,make a draught harness,breast harness,harness for the',
 'You cut and stitch strap and cord into a padded breast-harness, shaped to spread the pull across a beast''s chest where it can bear the weight without chafing.', 'VERIFIED', now()),
('make_draft_yoke', 'Make an ox-yoke', 'draft_yoke', 1, 1, NULL, FALSE, FALSE, 70, 'items', 'CRAFT',
 'make a yoke,ox yoke,ox-yoke,make an ox-yoke,make a draught yoke,shape a yoke',
 'You shape a stout beam to sit across two beasts'' necks, fit the bows beneath and pin them fast — a yoke to set a pair pulling as one.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('make_draft_harness', 'fiber_cordage', 3),
('make_draft_harness', 'wooden_component', 1),
('make_draft_yoke', 'wooden_component', 2),
('make_draft_yoke', 'fiber_cordage', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('make_draft_harness', 'harness'),
('make_draft_yoke', 'yoke')
ON CONFLICT DO NOTHING;
