-- #37 / #77 — "dig a well" reached nothing, and a Chronicle on dry ground had only the sky to drink from.
--
-- Water today comes from a stream, a spring, standing water, or a rainwater catchment. The catchment's own
-- comment in waterInReach says why that matters: "a camp on dry ground with no stream can still draw water once
-- one stands". A well is the same thought pointed downward, and it was the one way to water a settlement that
-- the world had no answer for -- asked for in act six of the playthrough and answered with nothing.
--
-- WHY THIS IS NOT A TOKEN. A well changes where a Chronicle can live. Surface water decides settlement today:
-- wetland and river bank have it, grassland and forest away from a stream do not, and a catchment only gives
-- what the sky has lately given. A well gives ground its own water, and only where the water table is within
-- reach of a hand-dug shaft -- so it is a real decision about where to settle rather than a free upgrade.
--
-- Across this world that rule (damp enough, and not high above the land that drains into it) admits every one of
-- the 141 temperate-forest chunks, 34 of 85 grassland, 35 of 51 wetland, but only 12 of 100 highland and NONE of
-- the mountain or cave ground. That is the geography doing the deciding, which is the point.

INSERT INTO construction_kind
 (project_kind, display_name, domain_key, decays, proven_in, clarifies_draw)
VALUES
 ('WELL', 'Well', 'construction', TRUE, 'V387', 1)
ON CONFLICT (project_kind) DO NOTHING;

-- Keywords deliberately carry a VERB or the word "shaft". There is no bare "well" keyword and there must never
-- be one: "well" is one of the commonest words in English ("as well", "well enough", "the wound healed well"),
-- and a bare landform or adverb as a keyword is exactly the defect V382 removed from set_ridge_beam.
INSERT INTO assembly_definition
 (assembly_key, display_name, subject_kind, construction_kind, portable, domain_key, keywords, subjects, narration, review_state)
VALUES
 ('well', 'Well', 'STRUCTURE', 'WELL', FALSE, 'construction',
  'dig a well,sink a well,build a well,make a well,work on the well,line the well,well shaft,dig for water',
  'well,shaft,water',
  'The shaft goes down past dry gravel into damp, then into dark water that rises to meet the last of the digging.',
  'VERIFIED')
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (assembly_key, stage_key, stage_order, name, prerequisite_stage_key, tool_class, requires_fire, cure_minutes, narration)
VALUES
 ('well', 'well_sink_shaft',  1, 'Sink the shaft',   NULL,              'STRIKING', FALSE,   0,
  'You break the ground and go down, hauling out spoil by the basketful. The hole becomes a shaft.'),
 ('well', 'well_line_shaft',  2, 'Line the shaft',   'well_sink_shaft',  NULL,      FALSE,   0,
  'You set stone against the walls as you go, so the shaft holds itself open instead of closing on whoever is in it.'),
 ('well', 'well_let_it_fill', 3, 'Let it fill',      'well_line_shaft',  NULL,      FALSE, 720,
  'You leave it overnight. By morning the water has found its own level and stands clear of the silt.')
ON CONFLICT (stage_key) DO NOTHING;

-- Lining is what makes the difference between a well and a pit that falls in, so it is the stage that costs.
INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
 ('well_line_shaft', 'field_stone', 8)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM construction_kind WHERE project_kind='WELL';
    IF n <> 1 THEN RAISE EXCEPTION 'V387: the well has no construction_kind'; END IF;

    SELECT COUNT(*) INTO n FROM assembly_stage WHERE assembly_key='well';
    IF n <> 3 THEN RAISE EXCEPTION 'V387: expected three stages, found %', n; END IF;

    -- A bare "well" keyword would claim half the sentences in the language. Refuse to ship one.
    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE assembly_key='well' AND ',' || keywords || ',' LIKE '%,well,%';
    IF n <> 0 THEN RAISE EXCEPTION 'V387: the well has a bare "well" keyword, which would take any sentence containing it'; END IF;

    -- And no other verified assembly may share a phrase with it unless both can still be named more precisely,
    -- which is the rule #715 left in place.
    SELECT COUNT(*) INTO n FROM assembly_definition a
     JOIN LATERAL (SELECT trim(x) kw FROM unnest(string_to_array(a.keywords, ',')) x) ka ON true
     JOIN assembly_definition b ON b.assembly_key = 'well' AND b.assembly_key <> a.assembly_key
     JOIN LATERAL (SELECT trim(y) kw FROM unnest(string_to_array(b.keywords, ',')) y) kb ON kb.kw = ka.kw
     WHERE a.review_state='VERIFIED';
    IF n > 0 THEN RAISE EXCEPTION 'V387: the well shares a phrase with another assembly (% collisions)', n; END IF;
END $$;
