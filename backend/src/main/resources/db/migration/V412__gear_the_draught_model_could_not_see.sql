-- V412 — two pieces of draught gear the draught model could not see (#106)
--
-- THE DECLARED-BUT-IGNORED SHAPE, exactly. `draft_gear` is the table the whole draught model reads: gearOnBeast
-- picks the best-fitting piece a keeper owns and that is what eases a beast's fatigue over a mile; judgeHaulage
-- says it out loud — "the collar harness you keep fits them, and the weight would ride spread rather than on one
-- strap". The table holds TWO rows:
--
--   draft_yoke     HUGE   12   an ox-yoke
--   draft_harness  LARGE  12   a collar harness
--
-- And the catalogue holds four more things a keeper would call draught gear, every one makeable and carryable:
--
--   shoulder_yoke  1500 g   a yoke across one animal's shoulders
--   rope_harness    300 g   a harness improvised from rope
--   pack_saddle    2000 g   CARRYING gear, not pulling gear — see below
--   leg_hobble      220 g   a restraint, already answered by the restraint work
--
-- So a player who makes a shoulder yoke or a rope harness — plainly gear, plainly for the beast, carried on their
-- own back — is told "You keep no gear for them at all, so they would haul against bare rope and pay for every
-- mile of it", and pays the bare-rope fatigue for every mile they walk. The item is attainable, workable and
-- player-reachable, and the one model that exists to read it cannot.
--
-- WHAT THE NUMBERS SAY, and they are not flattering on purpose. eases_fatigue_to is the fatigue a beast takes
-- per unit of work while wearing the piece, so LOWER IS BETTER GEAR, and gearOnBeast takes the MIN across
-- everything that fits the animal. Measured against the model as it stands:
--
--   no gear at all          20 per work on open going, 30 over broken (DRAFT_FATIGUE_PER_WORK, and the x3/2)
--   rope_harness            16 / 24
--   shoulder_yoke           14 / 21
--   draft_yoke, draft_harness   12 / 18
--
-- So both new pieces are strictly better than bare rope and strictly worse than the gear made for the job,
-- which is the whole shape of the thing. A fitted ox-yoke and a padded collar harness both sit at 12 because
-- both are the right answer for their animal. These two are not:
--
--   shoulder_yoke  LARGE  14  — one beam over one animal's shoulders. Honest work and a real piece of gear, but
--                              it bears on a narrower part of the animal than a neck yoke shaped to an ox, so it
--                              is worth having and worth less. Capped at LARGE because an ox's neck is what the
--                              ox-yoke is shaped for; a single shoulder beam is not that.
--   rope_harness   LARGE  16  — rope and knots standing in for a padded collar. It spreads the pull better than
--                              bare rope and markedly worse than leather with padding at the shoulder, which is
--                              the entire difference the collar harness exists to make.
--
-- NEITHER IS A SUBSTITUTE FOR THE RIGHT GEAR, which is the point of giving them worse floors rather than equal
-- ones: a keeper who has only a rope harness is better off than one with nothing and still has a reason to make
-- the collar harness. A better piece of gear means less of the beast spent, never more work got out of it.
--
-- WHAT IS DELIBERATELY NOT ADDED:
--   pack_saddle — it CARRIES and does not PULL. draft_gear is read only by the haulage model, and a row here
--                 would have a pack-saddle easing the fatigue of an ox drawing a cart, which it does not do.
--                 The carrying half wants its own answer; the words for it are a separate slice.
--   leg_hobble  — a restraint. It holds an animal still and draws nothing. The restraint question was already
--                 answered by saying what the gear does rather than inventing a fastened state, and this is that
--                 same gear.

INSERT INTO draft_gear (item_key, fits_up_to_size, eases_fatigue_to, notes) VALUES
  ('shoulder_yoke', 'LARGE', 14,
   'A single beam laid across one animal''s shoulders and tied beneath, which is how one beast is put to a load '
   || 'without a team. It bears on a narrower part of the animal than a neck yoke shaped to an ox, so it is worth '
   || 'having and worth less than the yoke an ox was fitted for.'),
  ('rope_harness', 'LARGE', 16,
   'Rope and knots standing in for a padded collar: it spreads the pull across the chest after a fashion, which '
   || 'is better than a single strap and a long way short of leather padded at the shoulder. The gear a keeper '
   || 'makes before they have the hide to make the real thing.')
ON CONFLICT (item_key) DO NOTHING;

DO $$
DECLARE n int; bad text;
BEGIN
  -- 1. BOTH ROWS MUST BE THERE. ON CONFLICT DO NOTHING is right for re-running and wrong for silence: if the
  --    item keys were ever renamed, the FK would fail loudly — but a future edit that changes the VALUES list
  --    could leave this migration doing nothing at all on a fresh database while looking fine.
  SELECT count(*) INTO n FROM draft_gear WHERE item_key IN ('shoulder_yoke','rope_harness');
  IF n <> 2 THEN
    RAISE EXCEPTION 'V412: expected both shoulder_yoke and rope_harness in draft_gear, found %', n;
  END IF;

  -- 2. THE RIGHT GEAR MUST STAY THE BEST GEAR. gearOnBeast takes MIN(eases_fatigue_to) over everything that
  --    fits, so if either of these ever cost at or below the purpose-made gear, a rope harness would be as good
  --    as a fitted collar and nobody would have reason to make one. That is the inverse of this project's rule:
  --    a better process means better recovery, never more matter — and better gear means less of the beast
  --    spent, never a shortcut past making the better piece.
  SELECT string_agg(g.item_key, ', ') INTO bad
    FROM draft_gear g
   WHERE g.item_key IN ('shoulder_yoke','rope_harness')
     AND g.eases_fatigue_to <= (SELECT MIN(eases_fatigue_to) FROM draft_gear
                                 WHERE item_key IN ('draft_yoke','draft_harness'));
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V412: % eases fatigue as well as or better than the purpose-made gear, which would make the '
      'improvised piece a substitute for the fitted one', bad;
  END IF;

  -- 3. AND EVERY PIECE MUST BE BETTER THAN NOTHING. The ungeared cost is DRAFT_FATIGUE_PER_WORK = 20 in
  --    PhysicalItemService, applied through COALESCE(gearOnBeast, ?) — so a piece at 20 is gear that costs the
  --    keeper the making and gives the beast nothing, and a piece above 20 is gear that TIRES AN ANIMAL FASTER
  --    than hauling bare. Named here because the number lives in Java and nothing else would catch it: if that
  --    constant is ever lowered past these rows, this fails and says so rather than quietly making a harness
  --    worse than no harness.
  SELECT string_agg(item_key, ', ') INTO bad FROM draft_gear WHERE eases_fatigue_to >= 20;
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V412: % costs as much fatigue as hauling with no gear at all (DRAFT_FATIGUE_PER_WORK=20), '
      'so wearing it would be worse than bare rope', bad;
  END IF;

  -- 4. AND EVERY PIECE MUST FIT SOMETHING THAT PULLS. A row that fits nothing in draft_species is gear the model
  --    still cannot see — the very defect this migration exists to close, repeated one level down.
  SELECT string_agg(g.item_key, ', ') INTO bad
    FROM draft_gear g
   WHERE NOT EXISTS (SELECT 1 FROM draft_species ds JOIN wildlife_species ws ON ws.species_key=ds.species_key
                      WHERE body_size_rank(g.fits_up_to_size) >= body_size_rank(ws.size_tier));
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V412: % fits no draught species at all, so it is gear nothing can wear', bad;
  END IF;
END $$;
