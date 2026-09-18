-- #77 — a frame to stretch a hide on.
--
-- THE GAP. Fleshing and dehairing are the two hardest, most skilled steps between a carcass and leather, and every
-- people who has ever worked hides has done them on something: a beam the hide is draped over, or a frame it is
-- laced into under tension, so the scraper meets a taut surface instead of a sliding, bunching skin. #77 names
-- `hide_scraping_frame` and `tanning_rack`, and the catalogue had neither. `flesh_hide` and `dehair_hide` declared
-- no station at all, so a Chronicle worked a hide on the ground exactly as well as anyone ever could.
--
-- WHAT IT ADDS. A buildable hide frame, and `station_kind = 'HIDE_FRAME'` on the two processes that are done on
-- one. The station rule already in the process engine does the rest (#220, the drying rack's pattern):
--   * the work is done under tension, so it comes out one workmanship grade truer — capped, as every station
--     assist is, by the grade of the hide going in, so a frame cannot make a spoiled skin into fine leather;
--   * and it EASES, never gates: a hide can still be fleshed on the bare ground, exactly as before.
--
-- WHAT IT DOES NOT DO. It is not the tanning pit. Tanning itself is a soak, and nothing here changes how long a
-- hide sits in the bark liquor. And `lay_out_hide` — cutting finished leather into pattern pieces — is bench work,
-- so it is left with the bench.
--
-- KEYWORDS avoid "rack": CRAFT_SHELF takes a build verb beside "rack" unless the rack is one of a short list it
-- excludes, and a hide frame is not in it. They avoid "tan" on its own too, which reads as a colour as easily as a
-- craft. And they avoid every word the two processes answer to — "flesh", "scrape", "dehair", "strip" — so that
-- building the frame can never be taken for doing the work on it; "fleshing frame", the natural name, is left out
-- for exactly that reason. And "stretching frame" — the other natural name — is out because STRETCH takes any
-- phrase containing "stretch", and would have a Chronicle loosening their back instead of building anything. The
-- classifier test caught that one in a tenth of a second.

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('HIDE_FRAME', 'Hide frame', 'construction', FALSE, TRUE, TRUE, 'V340')
ON CONFLICT (project_kind) DO NOTHING;

-- Poles and cordage: it weathers, and it burns.
UPDATE construction_kind SET flammable = TRUE WHERE project_kind = 'HIDE_FRAME';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('hide_frame','STRUCTURE','Hide frame',FALSE,NULL,'HIDE_FRAME','construction',
   'build a hide frame,make a hide frame,raise a hide frame,set up a hide frame,hide frame,'
   'lash a hide frame,work on the hide frame',
   'hide frame',
   'A square of lashed poles standing upright, a hide laced into it on every side and drawn drum-tight.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('hide_frame_poles','hide_frame',1,'Lash the frame',NULL,0,'CUTTING',FALSE,
   'You lash four stout poles into a square and brace the corners, so that it will not rack when a hide is drawn tight in it.'),
  ('hide_frame_lacing','hide_frame',2,'Rig the lacing','hide_frame_poles',0,NULL,FALSE,
   'You rig cords round every side, ready to lace a hide in and take it up until it rings when you tap it.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('hide_frame_poles','hazel_rod',4), ('hide_frame_poles','fiber_cordage',2),
  ('hide_frame_lacing','fiber_cordage',3)
ON CONFLICT (stage_key, item_key) DO NOTHING;

UPDATE material_process SET station_kind = 'HIDE_FRAME' WHERE process_key IN ('flesh_hide','dehair_hide');

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key = 'hide_frame'
     AND (r.item_key NOT IN (SELECT item_key FROM item_definition)
          OR NOT EXISTS (SELECT 1 FROM item_source src WHERE src.item_key=r.item_key));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V340: the hide frame asks for items nobody can obtain: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key = 'hide_frame'
     AND trim(k) ~ '(rack|shelf|shelves|desk|table|workbench|bench|stretch|(^|\s)tan($|\s)|flesh|scrape|dehair|strip)';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V340: a Java intent or a process would take these keywords first: %', bad; END IF;

  IF NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                  WHERE ad.construction_kind='HIDE_FRAME' AND ad.review_state='VERIFIED') THEN
    RAISE EXCEPTION 'V340: nobody can build a hide frame';
  END IF;

  -- A station nothing asks for is a bench nobody sits at.
  SELECT count(*) INTO n FROM material_process WHERE station_kind = 'HIDE_FRAME';
  IF n <> 2 THEN RAISE EXCEPTION 'V340: expected fleshing and dehairing to ask for the frame, found %', n; END IF;
END $$;
