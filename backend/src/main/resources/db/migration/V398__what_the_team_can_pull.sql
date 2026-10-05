-- #37 — "What can the oxen pull?"
--
-- The draft subsystem is finished and almost entirely invisible.
--
-- draft_gear is SIZED TO THE BODY (V371): a collar harness eases a goat and does nothing whatever for an ox,
-- and a neck yoke is the other way about. draft_vehicle holds four vehicles with four different beds, and the
-- load is capped by the bed rather than by the team. Rough ground tires a team half again as hard. Fatigue,
-- hunger, thirst and conditioning each scale what a beast can draw.
--
-- EVERY BIT OF THAT IS COMPUTED INSIDE AN UPDATE THAT RUNS ONLY WHEN THE CHRONICLE WALKS. The single line of
-- prose about it comes back as part of a journey. A keeper standing in their own camp could not ask what their
-- oxen would pull, whether the strap they own fits them, which of them was blown, or whether the ground they
-- were on was the easy going.
--
-- And asking was worse than silence, because the cart's own assembly answered instead:
--
--   pull the cart              -> "You have not got enough cart wheel within reach"
--   load the cart              -> the same
--   unload the cart            -> the same
--   hitch the ox to the cart   -> the same
--   harness the ox             -> nothing at all
--   yoke the oxen              -> nothing at all
--   load the sled              -> nothing at all
--   put the load on the travois-> nothing at all
--
-- JUDGE_HAULAGE is the question, answered from the same gearOnBeast expression the haul charges by and the same
-- bestBed cap the load uses. Read-only: it adds no capability and moves nothing. It asks a LOOSER clause than
-- the haul does — the haul requires a vehicle to exist, and a keeper with two tamed oxen and no cart has the
-- most to be told.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('JUDGE_HAULAGE', NULL, 0, FALSE, FALSE,
  'Looking over your own team and the gear you keep for it takes the world exactly as it is and puts nothing '
  'back. Walking round an animal does not tire it, and does not mark the ground it stands on.',
  3, 0, 'ATTENTION', 10)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key='JUDGE_HAULAGE';
    IF n <> 1 THEN RAISE EXCEPTION 'V398: JUDGE_HAULAGE must have exactly one impact card, found %', n; END IF;

    -- Looking at a team neither tires it nor marks the ground. A footprint here would be a claim otherwise.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key='JUDGE_HAULAGE' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V398: judging your own team must leave it exactly as it was'; END IF;

    -- What the report reads must exist, or it is a sentence about nothing. These four ARE the answer: the gear
    -- and its fit, the vehicles and their beds, the species that can draw at all, and the bond that is tamed.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='draft_gear' AND column_name='fits_up_to_size') THEN
        RAISE EXCEPTION 'V398: fits_up_to_size is what decides whether the strap you own goes on the beast you have';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='draft_gear' AND column_name='eases_fatigue_to') THEN
        RAISE EXCEPTION 'V398: eases_fatigue_to is what gear is worth';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM draft_vehicle) THEN
        RAISE EXCEPTION 'V398: nothing in the catalogue is a draft vehicle, so there is nothing to pull';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_bond' AND column_name='draft_fatigue') THEN
        RAISE EXCEPTION 'V398: draft_fatigue is how the report knows a beast is blown';
    END IF;

    -- And both pieces of gear must still declare a fit. A gear row that fits nothing is a strap no beast can
    -- wear, and the report would then say "nothing you have will fit" for a reason nobody intended.
    SELECT COUNT(*) INTO n FROM draft_gear WHERE fits_up_to_size IS NULL OR trim(fits_up_to_size) = '';
    IF n > 0 THEN RAISE EXCEPTION 'V398: % draft gear row(s) fit no size at all', n; END IF;
END $$;
