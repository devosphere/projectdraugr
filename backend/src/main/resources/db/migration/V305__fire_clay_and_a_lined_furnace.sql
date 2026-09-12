-- #160 — fire clay, and a furnace that will take the heat.
--
-- This is the one candidate on #160 with a real chain behind it, and it arrives after two corrections to my own
-- reading of that ticket, both recorded there:
--
--   * I first reported `bloomery_furnace` as consumed by NOTHING. Wrong. It is the `station_kind` of every smelt
--     in the catalogue. A station is a THIRD way an item can be required, alongside material_process_input and
--     material_process_input_group, and I had checked only the two input tables.
--   * I then said a lining could not be added without a Java change, because `station_kind` is a single-key
--     equality and a lined furnace would not satisfy the processes that name the plain one. That much is true —
--     and reaching for it was the wrong shape. A better furnace does not need to REPLACE the station of an
--     existing process; it needs to make a NEW one possible. Then nothing existing changes, nothing is taken
--     away, and the catalogue half of the work is pure data.
--
-- SO: fire clay is dug like any other mineral, a lined bloomery is raised from it, and that lined furnace is the
-- station for a hotter smelt returning two blooms where the plain one returns one, from the same ore and the same
-- charcoal. A keeper who never finds fire clay smelts iron exactly as they always did.
--
-- WHY THAT IS THE TRUE CHAIN. A bloomery is lined with refractory clay precisely because ordinary clay fails at
-- smelting heat: a furnace that holds its heat instead of shedding it through a slumping wall reduces more of the
-- ore and loses less of it to the slag. "More iron from the same ore" is exactly what a lining buys.
--
-- The Java half is one method and one clause, in the commit alongside this file: the GATHER_MINERAL rule in
-- ChronicleActionService names its minerals as literals (flint, chert, obsidian, pyrite, ...), so a mineral row
-- added here would be dug by nobody. That is the declared-but-ignored defect again, in the classifier this time.
-- The clause added there asks the catalogue instead. It is narrowed to minerals with a `tool_required`, which is
-- what makes it additive: those are the ones you must break out of rock and therefore must ask for by name, and
-- the names of the toolless ones (Field stone, Surface clay, River sand) are the ones that would otherwise have
-- been stolen from GATHER_STONE and GATHER_CLAY. Limestone, which the literal list never covered, becomes
-- diggable by name as a side effect — a fix, not a risk: nothing `calcine_quicklime` answers to is a gather verb.
--
-- Deliberately NOT delivered, recorded here so it is not thought forgotten. `kaolin` wants high-fired ceramics
-- this era does not reach; `sulphur` has no use in this world to be terminal in; `chalk`'s honest role is the
-- rock flint occurs in, which `limestone_chunk` and `flint_stone` already cover. `mica_vein` stays out too — its
-- consumer `stone_lantern_cover` is genuinely read, but the light check it feeds is a boolean, so a mica pane
-- would only be a second way to produce the same `true`. It wants a light-quality model before it is worth
-- anything, and adding it now would be the catalogue token this project keeps finding.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable) VALUES
  ('fire_clay', 'Fire clay', 'MATERIAL', 1400, 800, TRUE),
  -- Heavier and bulkier than the plain shaft: the same stone, plus the lining packed inside it.
  ('lined_bloomery_furnace', 'Lined bloomery furnace', 'FURNITURE', 96000, 124000, FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- The ground that already carries iron carries the clay to smelt it in. Rarer than the surface clay any bank
-- gives up, and behind a tool, because it comes out of a bed rather than off the top.
INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
  ('fire_clay', 'Fire clay', 'HIGHLAND,MOUNTAIN,RIVER_BANK', 0.30, 'STRIKING', 1, 2,
   'A pale clay out of deeper beds. Ordinary clay slumps at smelting heat and takes the furnace wall with it; this holds, which is why a bloomery is lined with it.')
ON CONFLICT (mineral_key) DO NOTHING;

-- TECHNIQUE is how the plain furnace records its origin; the lined one is had the same way.
INSERT INTO item_source (item_key, source_kind, detail) VALUES
  ('fire_clay', 'MINERAL', 'pale refractory clay from deeper beds in highland, mountain and river-bank ground'),
  ('lined_bloomery_furnace', 'TECHNIQUE', 'a bloomery shaft whose hearth is packed with fire clay before firing')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- CONSTRUCT, not the CRAFT the plain furnace was filed under. A smelting shaft is raised on a site and stays
-- there, which is what CONSTRUCT means, and it is also what the activity vocabulary decides for the phrasings
-- anyone actually uses: "build"/"raise"/"erect" all score CONSTRUCT, and a CRAFT process would be skipped
-- outright for such a text before its keywords were even read. The plain furnace's own unreachable
-- "build a bloomery furnace"/"raise a bloomery furnace" keywords are that mismatch showing; they are left alone
-- here because changing its category would take "make a bloomery furnace" away from everyone mid-chronicle.
INSERT INTO material_process
  (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
   duration_minutes, domain_key, keywords, narration, review_state, reviewed_at, category_key, station_kind)
VALUES
  ('make_lined_bloomery_furnace', 'Raise a lined bloomery furnace', 'lined_bloomery_furnace', 1, 1, NULL, FALSE, FALSE,
   180, 'stoneworking',
   'build a lined bloomery furnace,raise a lined bloomery furnace,build a lined bloomery,raise a lined bloomery,build a lined furnace,lined bloomery furnace',
   'You raise the shaft as you would any bloomery, and then pack the hearth with pale fire clay before it is ever fired — a lining that will hold its heat where ordinary clay would slump and take the wall down with it.',
   'VERIFIED', now(), 'CONSTRUCT', NULL),
  -- Same ore, same charcoal, twice the bloom, and longer at it: heat kept is heat that has to be raised first.
  ('smelt_iron_hot', 'Smelt iron in a lined furnace', 'iron_bloom', 2, 2, NULL, TRUE, FALSE,
   240, 'stoneworking',
   -- Every phrase here names iron on purpose. The matcher's second axis is the subject, so a keyword carrying
   -- only the verb ("run a hot smelt") passes the keyword gate and then fails the subject gate against every
   -- text there is — a phrase that looks like vocabulary and answers to nothing. Checked, not assumed.
   'smelt iron in the lined furnace,smelt the iron in the lined furnace,smelt iron in a lined furnace,smelt iron hot,hot smelt of iron',
   'The lined hearth holds its heat instead of bleeding it out through the wall, and the charge comes down further than it ever did in the bare shaft — two blooms out of what gave you one.',
   'VERIFIED', now(), 'PROCESS', 'lined_bloomery_furnace')
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
  -- The plain shaft is 6 clay and 8 stone. This is the same stone, with fire clay doing the hearth.
  ('make_lined_bloomery_furnace', 'fire_clay', 4),
  ('make_lined_bloomery_furnace', 'clay_lump', 3),
  ('make_lined_bloomery_furnace', 'field_stone', 8),
  ('smelt_iron_hot', 'iron_ore', 2),
  ('smelt_iron_hot', 'charcoal', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

-- Without these the two-axis matcher can never resolve either process, however good its keywords are: a process
-- with no subject term fails the subject gate against every text there is. Nothing derives them — no trigger, no
-- service — so a migration that forgets them ships two recipes that are unreachable by construction.
INSERT INTO process_subject (process_key, subject_term) VALUES
  ('make_lined_bloomery_furnace', 'bloomery'),
  ('make_lined_bloomery_furnace', 'furnace'),
  ('smelt_iron_hot', 'iron'),
  ('smelt_iron_hot', 'ore'),
  ('smelt_iron_hot', 'bloom')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE bad text; plain int; hot int;
BEGIN
  -- Obtainability. A thing nothing can yield is a catalogue token, which is the defect this project exists to
  -- keep out.
  SELECT string_agg(k, ', ') INTO bad FROM unnest(ARRAY['fire_clay','lined_bloomery_furnace']) k
   WHERE k NOT IN (SELECT item_key FROM item_source);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V305: nothing can obtain these: %', bad; END IF;

  -- Terminal usefulness, the other half of the same rule: fire clay must be consumed by something, and the
  -- furnace it builds must be the station of something, or the chain stops at an object with nowhere to go.
  IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE item_key='fire_clay') THEN
    RAISE EXCEPTION 'V305: fire clay is dug and then used for nothing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM material_process WHERE station_kind='lined_bloomery_furnace') THEN
    RAISE EXCEPTION 'V305: the lined furnace is built and then stands there doing nothing';
  END IF;

  -- Every biome fire clay names must be one the mineral system already places things in. A typo here
  -- ("HIGHLANDS") would not fail anything — it would simply never be found, which is the worst outcome
  -- available, so it is asserted rather than trusted.
  SELECT string_agg(b, ', ') INTO bad
    FROM unnest(string_to_array((SELECT biome_affinity FROM mineral_definition WHERE mineral_key='fire_clay'), ',')) b
   WHERE NOT EXISTS (SELECT 1 FROM mineral_definition m
                      WHERE m.mineral_key <> 'fire_clay' AND position(b in m.biome_affinity) > 0);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V305: no other mineral is found in this ground, so it is probably a typo: %', bad; END IF;

  -- Iron and its clay must share country, or the chain is reachable only by a journey between two biomes for
  -- every single smelt.
  IF NOT EXISTS (
      SELECT 1 FROM unnest(string_to_array((SELECT biome_affinity FROM mineral_definition WHERE mineral_key='fire_clay'), ',')) b
       WHERE position(b in (SELECT biome_affinity FROM mineral_definition WHERE mineral_key='iron_ore')) > 0) THEN
    RAISE EXCEPTION 'V305: fire clay is found nowhere iron is';
  END IF;

  -- The Java clause added in this commit reads minerals that have a tool_required. If fire clay ever loses that,
  -- it silently stops being diggable by name.
  IF (SELECT tool_required FROM mineral_definition WHERE mineral_key='fire_clay') IS NULL THEN
    RAISE EXCEPTION 'V305: fire clay must need a tool, or the classifier will not hear it named';
  END IF;

  -- Inputs must exist.
  SELECT string_agg(DISTINCT i.item_key, ', ') INTO bad FROM material_process_input i
   WHERE i.process_key IN ('make_lined_bloomery_furnace','smelt_iron_hot')
     AND i.item_key NOT IN (SELECT item_key FROM item_definition);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V305: these inputs do not exist: %', bad; END IF;

  -- Both gates of the two-axis matcher, asserted together: each process must carry at least one subject term,
  -- and that term must appear in one of its own keywords. A recipe whose subjects and keywords disagree passes
  -- every schema check there is and still cannot be run by anybody.
  SELECT string_agg(p.process_key, ', ') INTO bad
    FROM material_process p
   WHERE p.process_key IN ('make_lined_bloomery_furnace','smelt_iron_hot')
     AND NOT EXISTS (SELECT 1 FROM process_subject s
                      WHERE s.process_key = p.process_key
                        AND position(s.subject_term in lower(p.keywords)) > 0);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V305: keywords and subject terms disagree, so these can never match: %', bad; END IF;

  -- Stronger, and it caught a real one: EVERY keyword must carry a subject, not just one of them. "run a hot
  -- smelt" passed the keyword gate and then failed the subject gate against every text there is — a phrase that
  -- reads like vocabulary and answers to nothing. It was written here, found by this check, and removed.
  SELECT string_agg(k, ', ') INTO bad
    FROM material_process p, LATERAL unnest(string_to_array(p.keywords, ',')) k
   WHERE p.process_key IN ('make_lined_bloomery_furnace','smelt_iron_hot') AND btrim(k) <> ''
     AND NOT EXISTS (SELECT 1 FROM process_subject s
                      WHERE s.process_key = p.process_key
                        AND position(' ' || s.subject_term || ' ' in ' ' || btrim(k) || ' ') > 0);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V305: these keywords name no subject and so can never fire: %', bad; END IF;

  -- The point of the whole slice: the hot smelt must beat the plain one.
  SELECT output_max INTO plain FROM material_process WHERE process_key='smelt_iron';
  SELECT output_max INTO hot   FROM material_process WHERE process_key='smelt_iron_hot';
  IF hot IS NULL OR plain IS NULL OR hot <= plain THEN
    RAISE EXCEPTION 'V305: a lined furnace must return more iron than a plain one (% against %)', hot, plain;
  END IF;

  -- And it must beat it on the same ore, or it is not a better furnace, only a bigger charge.
  IF (SELECT quantity FROM material_process_input WHERE process_key='smelt_iron_hot' AND item_key='iron_ore')
     > (SELECT quantity FROM material_process_input WHERE process_key='smelt_iron' AND item_key='iron_ore') THEN
    RAISE EXCEPTION 'V305: the hot smelt must not eat more ore for its extra iron';
  END IF;

  -- Purely additive: the ordinary smelt keeps working exactly as it did, for everyone who never finds fire clay.
  IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='smelt_iron' AND station_kind='bloomery_furnace' AND output_max=1) THEN
    RAISE EXCEPTION 'V305: the ordinary smelt must be left untouched';
  END IF;

  -- And you must not need a lined furnace in order to build one.
  IF (SELECT station_kind FROM material_process WHERE process_key='make_lined_bloomery_furnace') IS NOT NULL THEN
    RAISE EXCEPTION 'V305: raising the furnace cannot itself require the furnace';
  END IF;
END $$;
