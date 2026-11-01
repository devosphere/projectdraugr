-- #37/#93 — the card for the reading of how unseen a keeper is, and the gear it is about.
--
-- The ambush odds have read three pieces of gear since #93: a camouflage cloak or a set hide screen takes
-- twelve off the chance of being closed on unseen, and a scent-mask bundle takes eight. chronicle.trail_hidden_at
-- keeps the four hours a brushed-out trail stays covered. Not one of those could be asked about — measured
-- live, `how close can I get`, `am I hidden` and `will they see me` all reached nothing — so gear made
-- expressly for not being seen was invisible in both senses: a keeper who had spent a hide and an evening on a
-- cloak had no way to learn it was doing anything at all.
--
-- AND IT WAS DOING LESS THAN THE CATALOGUE CLAIMS. Those three were read by the ambush rule and by NOTHING
-- ELSE, so the hide screen — whose own keywords include "stalking screen" — did nothing whatever for a stalk.
-- The hunt's tactic now reads the same number the ambush reads, named once in
-- WildlifeEncounterService.unseenBonus, so the gear made for closing with an animal helps you close with one.
--
-- The reading answers in the model's own numbers and refuses to give a distance: nothing in this world turns
-- concealment into paces, and inventing a figure would be the catalogue token in prose. It says what the gear
-- is worth against being noticed and says plainly that the rest is cover, wind and patience.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Taking stock of what hides you is attention and nothing else.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_UNSEEN', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 2,
        'Perception alters nothing it touches. Taking stock of what hides you — a cloak that breaks your '
        || 'outline, a scent mask, a trail brushed out behind you — costs a little attention and marks '
        || 'nothing. Asking does not hide you.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_UNSEEN')
    THEN RAISE EXCEPTION 'V449: JUDGE_UNSEEN has no activity_impact row'; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_UNSEEN' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V449: a reading must not mark the ground — asking does not hide you'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_UNSEEN';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V449: the reading costs nothing and takes no time'; END IF;

    -- THE THREE ITEMS THE WHOLE SLICE IS ABOUT. If any of them left the catalogue, both the ambush rule and the
    -- hunt would quietly stop reading it and the reading would quietly stop mentioning it — a silent loss of a
    -- mechanic, which is the shape this ticket exists to stop. The gear is #93's and these keys are what three
    -- places in the Java now agree on.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'camouflage_cloak')
    THEN RAISE EXCEPTION 'V449: camouflage_cloak is gone; nothing breaks the outline any more'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'hide_screen')
    THEN RAISE EXCEPTION 'V449: hide_screen is gone; the stalking screen cannot help a stalk'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key = 'scent_mask_bundle')
    THEN RAISE EXCEPTION 'V449: scent_mask_bundle is gone; nothing covers the scent'; END IF;

    -- ...and each of them is still something a keeper can actually get, or the reading would be about gear
    -- nobody can have. Either the catalogue makes it or the world provides it.
    SELECT COUNT(*) INTO n FROM (VALUES ('camouflage_cloak'), ('hide_screen'), ('scent_mask_bundle')) v(k)
     WHERE NOT EXISTS (SELECT 1 FROM material_process mp WHERE mp.output_item_key = v.k AND mp.review_state='VERIFIED')
       AND NOT EXISTS (SELECT 1 FROM assembly_definition ad WHERE ad.produces_item_key = v.k)
       AND NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = v.k);
    IF n > 0 THEN RAISE EXCEPTION 'V449: % of the concealment items cannot be obtained at all', n; END IF;

    -- AND THE TRAIL RECORD THE READING COUNTS THE HOURS FROM.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chronicle' AND column_name='trail_hidden_at')
    THEN RAISE EXCEPTION 'V449: chronicle.trail_hidden_at is gone; a covered trail cannot be reported'; END IF;
END $$;
