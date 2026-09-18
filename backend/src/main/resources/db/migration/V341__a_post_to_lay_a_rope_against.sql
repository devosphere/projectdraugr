-- #77 — a post to lay a rope against (`cordage_twisting_post`, from the ticket's own list).
--
-- THE GAP. Two-ply cord for a snare or a lashing is rolled on the thigh and needs nothing but hands. A long rope and
-- a bowstring are different work: the strands must be held under even tension along their whole length while the
-- twist is put in, or the lay comes out slack in one place and kinked in the next. People who make them anchor one
-- end — to a post, a peg, a tree — and walk the twist in against it. The catalogue let a Chronicle lay up forty feet
-- of climbing rope in mid-air exactly as well as against anything, and nothing named a place to do it.
--
-- WHAT IT ADDS. A buildable twisting post: a stout post set in the ground, then a cross-peg the strands are hitched
-- over. And `station_kind = 'CORDAGE_POST'` on the work that is done under tension — the climbing rope and the four
-- bowstrings. The station rule already in the process engine does the rest (#220, the racks' pattern):
--   * the lay is even, so the work comes out one workmanship grade truer, capped by the grade of what goes in;
--   * and it EASES, never gates: a string can still be twisted without one, exactly as before.
--
-- WHAT IT DOES NOT DO. It is not on ordinary cordage. Twisting fibre into cord is thigh-rolling, hand work, and a
-- post changes nothing about it; putting the station on `twist_cordage` would make a structure look worth more than
-- it is. Nor on the carry loop, girdle or harness, which are knotting and tying, not laying up.
--
-- KEYWORDS. The assembly matcher runs first, so a keyword here can never be stolen by a process, but it can steal
-- from one: nothing here may be a phrase a Chronicle uses to MAKE cord, so no bare "twist", "cordage", "rope",
-- "string" or "cord". Every keyword carries "post", and the bare names are "twisting post" and "rope post" — a
-- Chronicle twisting a bowstring at a post that stands is using it, not building another (#640).

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('CORDAGE_POST', 'Twisting post', 'construction', FALSE, TRUE, TRUE, 'V341')
ON CONFLICT (project_kind) DO NOTHING;

-- A post and a peg: it weathers in the ground, and it burns.
UPDATE construction_kind SET flammable = TRUE WHERE project_kind = 'CORDAGE_POST';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('cordage_twisting_post','STRUCTURE','Twisting post',FALSE,NULL,'CORDAGE_POST','construction',
   'build a twisting post,set a twisting post,set up a twisting post,raise a twisting post,make a twisting post,'
   'twisting post,rope post,build a rope post,set a rope post,work on the twisting post',
   'twisting post',
   'A stout post set firm in the ground, a peg through it at waist height, worn smooth where strands have run over it.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('twisting_post_set','cordage_twisting_post',1,'Set the post',NULL,0,'CUTTING',FALSE,
   'You point a length of log, drive it deep, and pack the ground round it until it does not give when you haul on it.'),
  ('twisting_post_peg','cordage_twisting_post',2,'Fit the peg','twisting_post_set',0,'CUTTING',FALSE,
   'You bore through the post at waist height and drive a hazel peg across it, and lash it so it cannot turn.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('twisting_post_set','timber_log',1),
  ('twisting_post_peg','hazel_rod',1), ('twisting_post_peg','fiber_cordage',1)
ON CONFLICT (stage_key, item_key) DO NOTHING;

UPDATE material_process SET station_kind = 'CORDAGE_POST'
 WHERE process_key IN ('make_climbing_rope','twist_bowstring','make_bowstring_bast','make_bowstring_hemp','make_bowstring_sinew');

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'cordage_twisting_post'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V341: the twisting post asks for items nobody can obtain: %', bad; END IF;

  -- Every keyword names the post; none is a way of asking to make cord.
  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'cordage_twisting_post'
     AND (trim(k) !~ '(^|\s)post$' OR trim(k) ~ '(^|\s)(twist|cordage|string|cord|spin|ply|braid|twine)($|\s)');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V341: these keywords could be taken for making cord: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='CORDAGE_POST' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V341: nobody can build a twisting post';
  END IF;

  -- A station nothing asks for is a bench nobody sits at; and ordinary cord stays hand work.
  SELECT count(*) INTO n FROM material_process WHERE station_kind = 'CORDAGE_POST';
  IF n <> 5 THEN RAISE EXCEPTION 'V341: expected the rope and the four bowstrings to ask for the post, found %', n; END IF;
  IF EXISTS (SELECT 1 FROM material_process WHERE process_key='twist_cordage' AND station_kind IS NOT NULL) THEN
    RAISE EXCEPTION 'V341: rolling cord on the thigh needs no station';
  END IF;
END $$;
