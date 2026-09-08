-- The earth oven (#77), and the one thing no other fireplace does: it goes on cooking after the fire is out.
--
-- V289 made which structures hold a fire a fact of the catalogue, so a new fireplace is now a data row rather
-- than five more literals. But a ring hearth, a cooking pit and a stone fireplace would all behave identically
-- to the stone fire pit already in the world — three names for one behaviour, which is the decoration this
-- catalogue is meant not to carry. So this adds the one that is genuinely different.
--
-- An earth oven is a pit lined with stone, filled with fire until the stones are soaked with heat, then raked
-- out and covered. The stones hold that heat for hours and the food cooks in it with no flame at all. That is
-- the whole technology, it is how people have cooked in pits everywhere they have lived, and it is a property
-- nothing else in this world has: work that needs heat still succeeds here after the fire has died.
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS retained_heat_minutes INTEGER NOT NULL DEFAULT 0;

INSERT INTO construction_kind
    (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in, flammable, encloses, is_barrier)
VALUES
    ('EARTH_OVEN', 'Earth oven', 'construction', FALSE, TRUE, TRUE, 'V290', FALSE, FALSE, FALSE)
ON CONFLICT (project_kind) DO NOTHING;

UPDATE construction_kind SET holds_fire = TRUE, retained_heat_minutes = 240 WHERE project_kind = 'EARTH_OVEN';

INSERT INTO assembly_definition
    (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration)
VALUES
    ('earth_oven', 'STRUCTURE', 'Earth oven', FALSE, NULL, 'EARTH_OVEN', 'construction',
     'build an earth oven,dig an earth oven,earth oven,work on the oven,line the oven pit',
     'oven,pit',
     'The pit lies lined with close-set stone, its walls scorched — heat it once and it will hold that heat long after the flame is gone.')
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
    ('oven_dig_pit',    'earth_oven', 1, 'Dig the pit',      NULL,            0, NULL,      FALSE,
     'You open a pit in the ground, straight-sided and deeper than it is wide.'),
    ('oven_line_stone', 'earth_oven', 2, 'Line it with stone','oven_dig_pit',  0, 'STRIKING', FALSE,
     'You set stone close over the floor and up the walls, until the pit is a vessel of rock.'),
    ('oven_seal_earth', 'earth_oven', 3, 'Bank the earth',   'oven_line_stone', 60, NULL,     FALSE,
     'You bank the spoil back around the rim and tamp it down. What is left will hold a heat for half a day.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
    ('oven_line_stone', 'field_stone', 8)
ON CONFLICT (stage_key, item_key) DO NOTHING;

-- Guards, in the shape V288 and V289 use: assert the promises rather than assume them.
DO $$
DECLARE problem text;
BEGIN
    -- The inputs must be real items, or a stage can never be completed and the oven is unbuildable.
    SELECT string_agg(r.item_key, ', ') INTO problem
      FROM assembly_stage_requirement r
     WHERE r.stage_key LIKE 'oven_%'
       AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = r.item_key);
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V290: no such item for an oven stage: %', problem; END IF;

    -- Retained heat is the whole point; without it this is a fire pit with a longer name.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='EARTH_OVEN' AND holds_fire AND retained_heat_minutes > 0) THEN
        RAISE EXCEPTION 'V290: an earth oven that holds no fire, or holds no heat, is a hole in the ground';
    END IF;

    -- And nothing else has claimed retained heat by accident.
    IF (SELECT count(*) FROM construction_kind WHERE retained_heat_minutes > 0) <> 1 THEN
        RAISE EXCEPTION 'V290: retained heat is the earth oven''s alone until something else earns it';
    END IF;

    -- A structure that holds a fire must not itself burn (the V289 rule, re-checked now the set has grown).
    SELECT string_agg(project_kind, ', ') INTO problem FROM construction_kind WHERE holds_fire AND flammable;
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V290: a hearth contains a fire, it does not feed one: %', problem; END IF;

    -- The Auditor's own rule, asserted here where it costs a second rather than an hour in CI.
    SELECT string_agg(d.item_key, ', ') INTO problem
      FROM item_definition d
     WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = d.item_key)
       AND NOT EXISTS (SELECT 1 FROM item_unreachable_known k WHERE k.item_key = d.item_key);
    IF problem IS NOT NULL THEN RAISE EXCEPTION 'V290: item(s) with no declared way to be obtained: %', problem; END IF;
END $$;
