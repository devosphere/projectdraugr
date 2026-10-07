-- #37 — "Which way is the water?"
--
-- The movement axis had never been swept, and a player moves more often than they do anything else. Forty-two
-- sentences about going somewhere: TWENTY-SIX REACHED NOTHING.
--
-- Among them, every question about where a kind of ground lies — and every piece of the answer was already in
-- world_chunk: the neighbouring biomes, their elevations, and the grid offsets that say which way each one lies.
-- planTravel only knows places the Chronicle has NAMED and can locate (by map, by marker and memory, or by
-- having walked there five times), so "go to the river" is recognised and refused — "you cannot call the way to
-- mind clearly enough" — and a KIND of ground could be asked for and never found at all:
--
--   which way is the water      where is the nearest high ground      how far is the river
--   which way to the woods      is there open ground near here        what direction is the sea
--
-- WHICH_WAY answers from the one ring of ground a person can see the shape of, in daylight, which is the same
-- bound the sky reading and the visual context already keep: what lies two chunks off is a thing to be SCOUTED
-- rather than known, and the answer says so instead of reading the world's map out loud. Inside a cave or after
-- dark it reports that it cannot see, which is the truth and not a refusal.
--
-- It also takes "how far is the river" off MEASURE, which answered "You pace it out and reckon by eye. Without
-- any instrument the figure is rough — near enough to plan by, not to build to" and then gave no figure at all:
-- a reckoning announced and not delivered, in the even voice of a true answer.
--
-- Read-only. The bearings come from Compass, the one place that convention lives (V403).

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('WHICH_WAY', NULL, 0, FALSE, FALSE,
  'Turning on the spot and reading the country takes nothing out of the ground and leaves nothing on it. The '
  'hills are where they are whether or not anybody looks for them.',
  1, 0, 'ATTENTION', 5)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'WHICH_WAY';
    IF n <> 1 THEN RAISE EXCEPTION 'V405: looking about needs exactly one impact card, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'WHICH_WAY'
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V405: looking about may not leave a mark on the world'; END IF;

    -- What the answer is made of. elevation is what makes "the ground rises to it" true rather than decorative.
    SELECT string_agg(c, ', ') INTO bad FROM (VALUES ('biome'), ('elevation'), ('grid_x'), ('grid_y'), ('world_id'))
        AS want(c)
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'world_chunk' AND column_name = want.c);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V405: the bearing answer is built from world_chunk columns that do not exist: %', bad;
    END IF;

    -- EVERY KIND OF GROUND THE GENERATOR MAKES MUST HAVE A WORD SOMEBODY COULD ASK IT BY. A biome the player
    -- can stand on and cannot ask after is a place with no name — the generic-word defect at the scale of the
    -- map. The Java side holds the spoken words; this asserts the world has no kind they do not cover.
    IF EXISTS (SELECT 1 FROM world_chunk) THEN
        SELECT string_agg(DISTINCT biome, ', ') INTO bad FROM world_chunk
         WHERE biome NOT IN ('OCEAN','COAST','RIVER_BANK','WETLAND','GRASSLAND','TEMPERATE_FOREST',
                             'HIGHLAND','MOUNTAIN','CAVE_MOUTH','CAVE_INTERIOR');
        IF bad IS NOT NULL THEN
            RAISE EXCEPTION 'V405: these biomes exist in the world and WHICH_WAY has no word for them: % — add them to GROUND_ASKED_FOR and groundSpoken', bad;
        END IF;
    END IF;
END $$;
