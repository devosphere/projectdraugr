-- #108/#106 — stock in the heat.
--
-- `advanceDraftThirst` has raised every kept beast's thirst by a flat 4 a turn since V267, in every weather there
-- is. A buffalo standing in a July heatwave dries out at exactly the same rate as a reindeer in a cold drizzle.
-- Weather is the one thing that decides how much water an animal needs, and it was the one thing the rule did not
-- look at.
--
-- That is also why #108's `swine_wallow`, `buffalo_wallow` and `shade_shelter` sat blocked for the whole cycle:
-- shade and a wallow are answers to heat, and there was no heat.
--
-- TWO ANSWERS, AND THEY ARE NOT INTERCHANGEABLE — which is what makes both structures real rather than two names
-- for one thing:
--
--   * Most stock lose heat by sweating and panting, and what they want is **shade**. Any roofed stock shelter is
--     shade; a keeper who has built a byre has already solved it.
--   * **Pigs and buffalo cannot sweat.** That is not a flourish, it is why both species wallow: wet mud is the
--     only way they shed heat. Shade alone does not do it for them, and a **wallow** is the only thing that does.
--
-- So `needs_a_wallow` is a fact about a species, sitting beside temperament and the rest.
--
-- ONE WALLOW, NOT TWO. #108 names `swine_wallow` and `buffalo_wallow`. They are one thing — a dug hollow that
-- holds water and mud — and the animals differ, not the structure. `MUD_WALLOW` is built, both names are
-- keywords on it, and the second row is deliberately absent for the same reason `quarantine_pen` was.
--
-- ON REACH, stated honestly: thirst is tracked for `draft_species`, so the wallow serves **water buffalo** today.
-- `wild_boar` is tamable and now reads DANGEROUS, but it pulls nothing and yields nothing, so nothing tracks its
-- thirst — a tamed boar is a keepable animal with no purpose yet. When pigs become productive stock the wallow
-- is already waiting for them; that is a gap in the boar, not in this.

ALTER TABLE wildlife_species ADD COLUMN IF NOT EXISTS needs_a_wallow BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN wildlife_species.needs_a_wallow IS
  'Cannot sweat, so shade does not shed its heat — only wet mud does. Pigs and buffalo. Shade relieves every '
  'other kept species; for these it does nothing and a wallow is the only answer.';

UPDATE wildlife_species SET needs_a_wallow = TRUE
 WHERE species_key IN ('water_buffalo','wild_boar','thornhide_boar');

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS gives_shade BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS is_wallow   BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.gives_shade IS
  'Keeps the sun off stock standing under it. Every roofed stock shelter does; a purpose-built shade screen does '
  'it without being a building.';
COMMENT ON COLUMN construction_kind.is_wallow IS
  'A dug hollow holding water and mud — the only way a species that cannot sweat sheds its heat.';

-- Anything already roofed over stock is shade; that is what a roof is in summer as well as winter.
UPDATE construction_kind SET gives_shade = TRUE WHERE shelters_stock AND encloses;

INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
  ('SHADE_SHELTER', 'Shade shelter', 'construction', FALSE, FALSE, TRUE, 'V303'),
  ('MUD_WALLOW',    'Mud wallow',    'construction', FALSE, FALSE, TRUE, 'V303')
ON CONFLICT (project_kind) DO NOTHING;

-- A shade screen is a roof on posts: it keeps the sun off and does nothing else — no walls, nothing in a wolf's
-- way, and it is not somewhere stock are kept.
UPDATE construction_kind SET gives_shade = TRUE, flammable = TRUE WHERE project_kind = 'SHADE_SHELTER';
-- A wallow is a hole full of mud. It burns no better than a puddle.
UPDATE construction_kind SET is_wallow = TRUE, flammable = FALSE WHERE project_kind = 'MUD_WALLOW';

INSERT INTO assembly_definition
  (assembly_key, subject_kind, display_name, portable, produces_item_key, construction_kind, domain_key, keywords, subjects, narration, review_state, reviewed_at)
VALUES
  ('shade_shelter','STRUCTURE','Shade shelter',FALSE,NULL,'SHADE_SHELTER','construction',
   'build a shade shelter,raise a shade shelter,build a shade screen,build a sun shade,build a shade roof,shade shelter,shade screen,sun shade',
   'shade shelter,shade screen,sun shade,shade roof',
   'Four posts and a thatched top, open on every side so the air moves through and the sun does not reach under.',
   'VERIFIED', now()),
  ('mud_wallow','STRUCTURE','Mud wallow',FALSE,NULL,'MUD_WALLOW','construction',
   'dig a mud wallow,build a mud wallow,dig a wallow,build a wallow,dig a swine wallow,dig a buffalo wallow,mud wallow,swine wallow,buffalo wallow',
   'mud wallow,swine wallow,buffalo wallow,wallow',
   'You dig a broad shallow hollow where the ground already lies wet, and let it fill and turn to mud.',
   'VERIFIED', now())
ON CONFLICT (assembly_key) DO NOTHING;

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration) VALUES
  ('shade_shelter_posts','shade_shelter',1,'Set the shade posts',NULL,0,'CUTTING',FALSE,
   'You set four posts wide apart, high enough that a full-grown beast stands under them without stooping.'),
  ('shade_shelter_top','shade_shelter',2,'Thatch the top','shade_shelter_posts',0,NULL,FALSE,
   'You lay thatch across the top and leave every side open, so what is under it gets shade and a moving air both.'),
  ('mud_wallow_dig','mud_wallow',1,'Dig the wallow',NULL,0,NULL,FALSE,
   'You dig it broad and shallow on ground that already holds water, and by evening it has turned to mud.')
ON CONFLICT (stage_key) DO NOTHING;

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
  ('shade_shelter_posts','timber_log',4), ('shade_shelter_posts','fiber_cordage',2),
  ('shade_shelter_top','thatch_bundle',4),
  ('mud_wallow_dig','field_stone',2)
ON CONFLICT (stage_key, item_key) DO NOTHING;

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(DISTINCT r.item_key, ', ') INTO bad
    FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key
   WHERE s.assembly_key IN ('shade_shelter','mud_wallow') AND r.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V303: these stages ask for items that do not exist: %', bad; END IF;

  SELECT string_agg(DISTINCT trim(k), ', ') INTO bad
    FROM assembly_definition, unnest(string_to_array(keywords, ',')) k
   WHERE assembly_key IN ('shade_shelter','mud_wallow') AND trim(k) ~ '(^|\s)pens?($|\s)';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V303: BUILD_PEN would swallow these keywords: %', bad; END IF;

  -- Both names #108 asks for must reach the one wallow, or dropping the second row loses a keeper's words.
  IF NOT EXISTS (SELECT 1 FROM assembly_definition WHERE assembly_key='mud_wallow'
                   AND keywords LIKE '%swine wallow%' AND keywords LIKE '%buffalo wallow%') THEN
    RAISE EXCEPTION 'V303: swine_wallow and buffalo_wallow are one structure, so both words must reach it';
  END IF;

  -- Something must need a wallow, and something must not, or the distinction says nothing.
  SELECT count(*) INTO n FROM wildlife_species WHERE needs_a_wallow;
  IF n = 0 THEN RAISE EXCEPTION 'V303: nothing needs a wallow'; END IF;
  SELECT count(*) INTO n FROM wildlife_species WHERE NOT needs_a_wallow;
  IF n = 0 THEN RAISE EXCEPTION 'V303: everything needs a wallow, so shade would never help anything'; END IF;

  -- The wallow must serve at least one animal whose thirst is actually tracked, or it relieves nothing.
  SELECT count(*) INTO n FROM wildlife_species ws JOIN draft_species ds ON ds.species_key = ws.species_key
   WHERE ws.needs_a_wallow;
  IF n = 0 THEN RAISE EXCEPTION 'V303: no wallowing species has tracked thirst, so a wallow would change nothing'; END IF;

  -- And shade must serve at least one, or the same is true of shade.
  SELECT count(*) INTO n FROM wildlife_species ws JOIN draft_species ds ON ds.species_key = ws.species_key
   WHERE NOT ws.needs_a_wallow;
  IF n = 0 THEN RAISE EXCEPTION 'V303: every kept beast wallows, so shade would change nothing'; END IF;

  -- Both must be buildable.
  SELECT string_agg(ck.project_kind, ', ' ORDER BY ck.project_kind) INTO bad FROM construction_kind ck
   WHERE ck.project_kind IN ('SHADE_SHELTER','MUD_WALLOW')
     AND NOT EXISTS (SELECT 1 FROM assembly_definition ad JOIN assembly_stage s ON s.assembly_key=ad.assembly_key
                      WHERE ad.construction_kind=ck.project_kind AND ad.review_state='VERIFIED');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V303: nobody can build these: %', bad; END IF;

  -- A wallow is not shade and shade is not a wallow, or the species distinction collapses.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE is_wallow AND gives_shade) THEN
    RAISE EXCEPTION 'V303: a hole full of mud is not a roof';
  END IF;

  -- And there is exactly one wallow, because two would be two names for one hollow.
  SELECT count(*) INTO n FROM construction_kind WHERE is_wallow;
  IF n <> 1 THEN RAISE EXCEPTION 'V303: expected one wallow, found %', n; END IF;
END $$;
