-- V267 — kept animals get thirsty. wildlife_bond tracked fatigue, conditioning and hunger, but nothing for water, so
-- a watering trough had nothing to relieve and every named watering structure in #106/#108 was inert by construction.
-- An animal that never needs a drink is also the one welfare pressure a keeper could ignore entirely.
--
-- Thirst mirrors hunger exactly: it rises as the world turns, and falls where the keeper's ground actually has water
-- — wet ground, a freshwater site, or a built trough or catchment. It joins fatigue and hunger in the haul formula,
-- so a thirsty beast pulls less, and stock kept on dry ground must have water brought to them.
ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS draft_thirst integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN wildlife_bond.draft_thirst IS
    'How thirsty this tamed animal is (0-100). Rises each turn away from water, falls on wet ground or at a built trough; reduces haul alongside fatigue and hunger.';

-- A trough is the answer to it on dry ground: a sited structure that holds water for stock.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('WATERING_STATION', 'Watering trough', 'construction', FALSE, FALSE, TRUE, 'V267')
ON CONFLICT (project_kind) DO NOTHING;

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at) VALUES
('watering_station','STRUCTURE','Watering trough',FALSE,NULL,'WATERING_STATION','construction',
 'build a watering trough,raise a watering trough,build a watering station,watering trough,watering station,stock trough',
 'watering trough,watering station,stock trough',
 'A hollowed log set level on packed ground and kept filled, low enough for stock to drink from without fouling it — the difference between animals that can be kept on dry ground and animals that cannot.','VERIFIED',now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
('watering_station_build','watering_station',1,'Hollow and set the trough',NULL,0,'CUTTING',FALSE,'You hollow out a length of log and set it level on packed ground, low enough for stock to reach without treading it over.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
('watering_station_build','timber_log',1)
ON CONFLICT (stage_key, item_key) DO NOTHING;
