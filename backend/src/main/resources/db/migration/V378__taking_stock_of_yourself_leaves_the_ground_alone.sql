-- #37 — SENSE_BODY must say what it does to the ground it is done on.
--
-- ActivityImpactCardIntegrationTest.everyIntentHasACardAndEveryCardHasAnIntent holds every intent to the same
-- promise: somebody has said what performing it does to the place it is performed in, and when the answer is
-- "nothing" the notes have to say WHY it is nothing. A new intent without a row is a procedure the world cannot
-- account for, so the guard fails the build rather than let it through unexamined — which is what it did to
-- SENSE_BODY, correctly.
--
-- Reading your own body is perception and belongs with the senses it sits beside in the classifier. A Chronicle
-- who takes stock of themselves looks inward and puts nothing back: no footprint, no drift, no trace on the
-- ground, and none of it needs a fire. The wording is the one FEEL, LISTEN, SMELL, EXAMINE and MEASURE already
-- share, because it is the same reason.
--
-- The cost is theirs rather than the ground's: five minutes of standing still and the small attention it takes,
-- matched to FEEL, which is the sense this most resembles. It cannot dirty you — you are not handling anything.
INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('SENSE_BODY', NULL, 0, FALSE, FALSE,
  'Perception alters nothing it touches. Looking, listening, smelling, reading, measuring and following sign all take the world exactly as it is and put nothing back.',
  2, 0, 'ATTENTION', 5)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'SENSE_BODY';
    IF n <> 1 THEN RAISE EXCEPTION 'V378: SENSE_BODY must have exactly one impact card, found %', n; END IF;

    -- It must cost the ground nothing, the way the senses beside it do. If a later edit gives it a footprint,
    -- that is a claim that asking after your own body marks the place you stood, and it should be argued for.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'SENSE_BODY' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V378: taking stock of yourself must leave the ground exactly as it was'; END IF;
END $$;
