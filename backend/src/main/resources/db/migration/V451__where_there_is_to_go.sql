-- #37/#99 — the card for the last miss on the release-gate sweep.
--
-- 76 ordinary sentences, replayed on a booted world, went from 15 reaching nothing to 2 over this cycle. One of
-- the two was `let the animals out`, which V450 answers. This is the other:
--
--     where should I go / where could I go / where have I not been  ->  nothing at all
--
-- Three records hold the answer and every one of them is already read by something else:
--
--     chronicle_chunk_visit      which ground this Chronicle has stood on   (the place reading, travel)
--     chronicle_named_location   what they named and where it lies          (travel, the HUD, the map)
--     world_chunk's grid         what kind of country lies each way         (the bearing reading, movement)
--
-- A keeper could ask the bearing TO a named place, and could ask what kind of ground lay somewhere, and could
-- not ask the plainest question of all: what there is.
--
-- IT REPORTS AND DOES NOT ADVISE. "You should go north" is a judgement this game has no business making —
-- the player brings their own reasons. What is known each way, which ways are unwalked, and where the places
-- they named lie is the same information with the choice left where it belongs. The unwalked half is the part
-- a player cannot get any other way: nothing else in the game will say "you have not been that way at all".
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Casting round the ground you stand on is attention and a few minutes of it.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_WHERE_TO_GO', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 4,
        'Perception alters nothing it touches. Casting round the ground you stand on — what lies each way of '
        || 'the ground you have walked, which ways you have not been at all, and where the places you named '
        || 'lie from here — costs a little attention and marks nothing. No ground is walked by asking.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_WHERE_TO_GO')
    THEN RAISE EXCEPTION 'V451: JUDGE_WHERE_TO_GO has no activity_impact row'; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_WHERE_TO_GO' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V451: a reading must not mark the ground — no ground is walked by asking'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_WHERE_TO_GO';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V451: the reading costs nothing and takes no time'; END IF;

    -- THE THREE RECORDS. The visit count is what tells walked ground from unwalked, the grid is what makes a
    -- bearing possible at all, and the named places are the half a keeper remembers by name rather than by
    -- coordinate. Any of them missing and the reading would quietly answer less than it claims.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle_chunk_visit' AND column_name='visit_count')
    THEN RAISE EXCEPTION 'V451: chronicle_chunk_visit.visit_count is gone; walked ground cannot be told from unwalked'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='world_chunk' AND column_name='grid_x')
    THEN RAISE EXCEPTION 'V451: world_chunk.grid_x is gone; no bearing can be reckoned'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='chronicle_named_location')
    THEN RAISE EXCEPTION 'V451: chronicle_named_location is gone; the places a keeper named cannot be placed'; END IF;

    -- AND THE GROUND IS A GRID WITH NEIGHBOURS IN IT, which the reading walks four ways from where it stands.
    -- A world of one chunk would make every direction "where the country ends", which is true and useless, so
    -- this says out loud that the generated world is not that.
    SELECT COUNT(*) INTO n FROM world_chunk;
    IF n > 0 THEN
        SELECT COUNT(*) INTO n FROM world_chunk a
         WHERE EXISTS (SELECT 1 FROM world_chunk b WHERE b.world_id=a.world_id
                         AND abs(b.grid_x-a.grid_x) + abs(b.grid_y-a.grid_y) = 1);
        IF n = 0 THEN RAISE EXCEPTION 'V451: no chunk in this world has a neighbour; the reading has nothing to look at'; END IF;
    END IF;
END $$;
