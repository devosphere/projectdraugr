-- #114 / #37 — "How do they regard me?"
--
-- Act twenty swept the PEOPLE, standing on a real isle so that every sentence had somebody to be about. Thirty-
-- seven things a person says to or about a community they have met: TWENTY-ONE REACHED NOTHING.
--
-- And the gap was concentrated in one place. SIX OF SIX QUESTIONS ABOUT THE RELATIONSHIP reached nothing:
--
--   how do they regard me     do they trust me          am I welcome here
--   what do they think of me  have I wronged them       what do they remember
--
-- Over a memory system that is fully built. community_relation carries the STANDING, the LAST EVENT KIND that
-- moved it and when, an OBLIGATION in plain words, how much of each other's speech has been worked out
-- (UNDERSTANDING, which "listen to them" raises and nothing ever read), and the date of FIRST CONTACT.
-- native_event keeps every offence, every amends and every approach, witnessed or not. ConductService.offence
-- spends the standing; DRIVEN_OFF (-60) decides when they meet you with stones; the hostile threshold (-30)
-- shuts the store and the gates; contact and trade both read the same number and refuse by it.
--
-- So a Chronicle could be driven off an isle by a people whose opinion of them was computed to the point, and
-- have no way whatever of asking what that opinion was, or what they had done, or what would mend it.
--
-- CHECK_STANDING says it as a MANNER rather than a number — what a stranger would read off how they are treated
-- — names the last thing that passed between them, what is held against them, what the people have of them from
-- their OWN doing, how far either can follow the other's speech, and, while the standing is negative, the three
-- things that mend it: say you were wrong, make it good, work alongside them. Each a little, and only while
-- there is something to answer for, which is what ConductService already enforces.
--
-- Recognised BEFORE the conduct pre-pass, so that a question about the relationship can never be carried out as
-- an act upon it. Read-only, and it discloses nothing a stranger could not work out from how they are treated.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('CHECK_STANDING', NULL, 0, FALSE, FALSE,
  'Taking the measure of how a people hold you costs nothing and changes nothing. They think what they think '
  'whether or not anybody wonders about it.',
  1, 0, 'ATTENTION', 5)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'CHECK_STANDING';
    IF n <> 1 THEN RAISE EXCEPTION 'V407: the standing reading needs exactly one impact card, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'CHECK_STANDING'
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V407: wondering how you stand may not leave a mark on the world'; END IF;

    -- Every column the answer is made of. Each was already maintained and none was readable.
    SELECT string_agg(c, ', ') INTO bad FROM (
        VALUES ('standing'), ('last_event_kind'), ('last_event_at'), ('obligation'), ('understanding'),
               ('first_contact_at')
    ) AS want(c)
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'community_relation' AND column_name = want.c);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V407: the standing reading is built from community_relation columns that do not exist: %', bad;
    END IF;

    SELECT string_agg(c, ', ') INTO bad FROM (VALUES ('community_id'), ('subject_id'), ('event_kind'), ('occurred_at'))
        AS want(c)
     WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name = 'native_event' AND column_name = want.c);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V407: what a people REMEMBER is built from native_event columns that do not exist: %', bad;
    END IF;

    -- THE STANDING MUST BE ABLE TO GO BOTH WAYS, or the reading describes a scale with one end. offence() clamps
    -- to -100..100 and the amends raise it; a column that could not hold a negative would make every one of the
    -- hostile branches unreachable prose, which this project counts as a catalogue token in another form.
    IF EXISTS (SELECT 1 FROM community_relation) THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_name='community_relation' AND column_name='standing'
                          AND data_type IN ('integer','smallint','bigint')) THEN
            RAISE EXCEPTION 'V407: standing must be a signed number for a people to think ill of anybody';
        END IF;
    END IF;

    -- And a people must exist to have a view, or this is a reading about nobody. Gated on a world existing,
    -- because communities are settled by genesis and a fresh database has none — the rule V400 taught.
    IF EXISTS (SELECT 1 FROM world_chunk) AND NOT EXISTS (SELECT 1 FROM native_community) THEN
        RAISE EXCEPTION 'V407: this world has ground but no people, so there is nobody to stand with';
    END IF;
END $$;
