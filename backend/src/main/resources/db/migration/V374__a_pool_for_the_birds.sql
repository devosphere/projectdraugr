-- #108 — a pool for the birds: what a keeper DOES about V373.
--
-- V373 said a duck kept from water does not lay, and left the keeper one answer: move. That is a real answer —
-- ducks belong near the marsh — but it is the only one, and "go somewhere else" is a poor mechanic to be the
-- whole of a rule. A keeper who has built a steading on good dry ground and wants ducks in it should be able to
-- dig them somewhere to swim, the way a keeper who wants a wallow digs one.
--
-- THE WALLOW IS THE PRECEDENT, and it is exact. `MUD_WALLOW` is a dug and puddled hollow that relieves an animal
-- which `needs_a_wallow` in the heat: a structure standing in for a natural feature, read by the rule that cares.
-- `holds_open_water` is the same shape for the same reason, which is why this is a column on construction_kind
-- rather than a new table — a second vocabulary for "this building supplies a natural thing" would be the trap
-- V313's wearable positions fell into.
--
-- WHAT IT IS NOT. Not a trough. `WATERING_STATION` and `RAINWATER_CATCHMENT` already answer thirst, and they
-- deliberately do not answer this: what a duck is missing on dry ground is somewhere to get in, not a drink.
-- The pool is dug wide and lined so it holds, and it is worth the work precisely because a trough is not.
--
-- AND IT WEARS. A lined pool silts up, the lining fails, and the birds puddle the edges down. It decays like
-- anything else a keeper builds, which is what keeps it a thing you look after rather than a thing you place.

ALTER TABLE construction_kind ADD COLUMN holds_open_water BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.holds_open_water IS
  '#108: this structure holds standing water a bird can get into — dug wide and lined, not a trough to drink from. Read by the waterfowl laying rule (V373) alongside the wet biomes and the freshwater sites. A watering station is deliberately NOT one of these.';

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays,
                               proven_in, flammable, encloses, is_barrier, holds_fire, retained_heat_minutes,
                               barrier_strength, holds_open_water)
VALUES ('WATERFOWL_POOL', 'Waterfowl pool', 'construction', FALSE, FALSE, TRUE,
        'V374', FALSE, FALSE, FALSE, FALSE, 0,
        0, TRUE);

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, construction_kind, domain_key,
                                 keywords, subjects, narration, review_state, reviewed_at)
VALUES ('waterfowl_pool', 'STRUCTURE', 'Waterfowl pool', FALSE, 'WATERFOWL_POOL', 'construction',
        'dig a waterfowl pool,dig a duck pool,dig a pool for the ducks,build a waterfowl pool,line a duck pool,waterfowl pool,duck pool,duck pond',
        'waterfowl pool,duck pool,duck pond',
        'A hollow dug wide and shallow and puddled tight with clay, holding water enough that a bird can get right into it and come out clean.',
        'VERIFIED', now());

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration)
VALUES
 -- No tool class: the registry knows CUTTING, STRIKING and AXE, and a shallow hollow in soft ground is none of
 -- them. It is hands, a stick and a long afternoon, which is what the narration says.
 ('waterfowl_pool_dig', 'waterfowl_pool', 1, 'Dig the hollow', NULL, 0, NULL, FALSE,
  'You take out a wide shallow bowl of earth, sloping one side down to nothing so a bird can walk in rather than fall in. The spoil goes up as a low bank behind it.'),
 ('waterfowl_pool_line', 'waterfowl_pool', 2, 'Puddle it tight', 'waterfowl_pool_dig', 0, NULL, FALSE,
  'You tread clay into the floor and up the sides until the water stops going away as fast as you can carry it, and then you tread it some more. By the end of it the bowl holds what you pour in.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
 ('waterfowl_pool_line', 'clay_lump',  6),
 ('waterfowl_pool_line', 'stone_slab', 2);

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM construction_kind WHERE holds_open_water;
    IF n <> 1 THEN RAISE EXCEPTION 'V374: expected exactly the pool, found %', n; END IF;
    -- A trough is not a pool, and the day someone marks one it should be on purpose and not by accident.
    SELECT COUNT(*) INTO n FROM construction_kind
     WHERE holds_open_water AND project_kind IN ('WATERING_STATION', 'RAINWATER_CATCHMENT');
    IF n > 0 THEN RAISE EXCEPTION 'V374: a trough answers thirst, not somewhere to swim'; END IF;
END $$;
