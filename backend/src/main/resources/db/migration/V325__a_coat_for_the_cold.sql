-- #106/#108 — a coat for the cold: grown stock feel the weather, and a blanket answers it.
--
-- #106's remaining table lists `winter_animal_blanket` and `rain_animal_sheet` as waiting on "cold for grown stock
-- (V303 is heat; V297 is young)". V303 made a hot day cost a beast water unless shade or a wallow stood over it.
-- Nothing made a cold day cost it anything at all: a horse stood through a January night on an open hillside and
-- came out of it exactly as fed as one in a byre. So a stock blanket had nothing to be for.
--
-- WHAT COLD COSTS. A beast keeps warm by burning feed. Grown stock do not die of a cold night the way the young do
-- (V297) — they get hungry, and a hungry beast works worse and does not come into condition to breed (V296). So
-- cold is paid in hunger, inside the one set-based hunger statement, exactly as heat is paid in thirst:
--
--   hard cold      at or below freezing                          +4 hunger a turn
--   cold rain      raining or storming, under 8 C                +2 hunger a turn — a wet coat loses its warmth
--
-- WHAT ANSWERS IT, and the answers are not interchangeable:
--
--   * An ENCLOSED stock shelter (a byre, a coop, a barn) keeps off both. A fence does neither.
--   * A roof over stock that does not enclose them keeps off the rain but not the frost.
--   * A WINTER STOCK BLANKET — wool cloth over a hide backing — keeps a beast warm in hard cold and dry in the rain.
--   * A RAIN SHEET — linen waxed with beeswax — sheds the wet. It is no warmer than the beast's own coat, so it does
--     nothing against a hard frost.
--
-- A covering is worn by ONE beast. A keeper with two blankets and three horses keeps two of them warm.
--
-- What counts as a cover is declared here (stock_cover) and read by the hunger tick through it — not a list of
-- item keys in Java, which is the defect this project keeps finding.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable) VALUES
  ('winter_stock_blanket', 'Winter stock blanket', 'TOOL', 1600, 6000, FALSE),
  ('stock_rain_sheet',     'Stock rain sheet',     'TOOL',  900, 3000, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
  ('winter_stock_blanket', 'TECHNIQUE', 'wool cloth sewn over a hide backing, cut to lie over a beast''s back'),
  ('stock_rain_sheet',     'TECHNIQUE', 'linen cloth rubbed with melted beeswax until it sheds water')
ON CONFLICT (item_key, source_kind) DO NOTHING;

INSERT INTO material_process
  (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water,
   duration_minutes, domain_key, keywords, narration, review_state, reviewed_at, category_key)
VALUES
  ('sew_winter_stock_blanket', 'Sew a winter stock blanket', 'winter_stock_blanket', 1, 1, 'CUTTING', FALSE, FALSE,
   90, 'textiles',
   'sew a winter stock blanket,sew a stock blanket,make a stock blanket,sew a blanket for the stock,winter stock blanket,stock blanket',
   'You cut the hide to the length of a beast''s back and sew the wool cloth over it, with ties at the chest and under the belly so it stays where it is put.',
   'VERIFIED', now(), 'CRAFT'),
  ('wax_stock_rain_sheet', 'Wax a stock rain sheet', 'stock_rain_sheet', 1, 1, NULL, TRUE, FALSE,
   60, 'textiles',
   'wax a stock rain sheet,make a stock rain sheet,wax a rain sheet for the stock,stock rain sheet,rain sheet for the stock',
   'You warm the beeswax until it runs and work it into the linen with your palms, fold after fold, until water beads on it and rolls off.',
   'VERIFIED', now(), 'CRAFT')
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
  ('sew_winter_stock_blanket', 'wool_cloth', 2), ('sew_winter_stock_blanket', 'animal_hide', 1),
  ('wax_stock_rain_sheet', 'linen_cloth', 2), ('wax_stock_rain_sheet', 'beeswax', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
  ('sew_winter_stock_blanket', 'stock blanket'), ('sew_winter_stock_blanket', 'blanket'), ('sew_winter_stock_blanket', 'stock'),
  ('wax_stock_rain_sheet', 'rain sheet'), ('wax_stock_rain_sheet', 'sheet'), ('wax_stock_rain_sheet', 'stock')
ON CONFLICT DO NOTHING;

CREATE TABLE stock_cover (
    item_key          VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    against_hard_cold BOOLEAN NOT NULL,
    against_wet_cold  BOOLEAN NOT NULL,
    notes             TEXT NOT NULL,
    CONSTRAINT stock_cover_covers_something CHECK (against_hard_cold OR against_wet_cold)
);

COMMENT ON TABLE stock_cover IS
  'What a keeper can put over a grown beast against the weather (#106, V325), and what it answers. One cover serves '
  'one beast. Read by PhysicalItemService.advanceDraftHunger.';

INSERT INTO stock_cover (item_key, against_hard_cold, against_wet_cold, notes) VALUES
  ('winter_stock_blanket', TRUE,  TRUE,  'Wool over hide: warm in a frost, and the hide keeps the wet off.'),
  ('stock_rain_sheet',     FALSE, TRUE,  'Waxed linen sheds rain, and is no warmer than the beast''s own coat.');

DO $$
DECLARE bad text; n int;
BEGIN
  -- Conservation: sewing and waxing lose offcuts and drips, never make cloth.
  SELECT string_agg(process_key, ', ') INTO bad FROM process_mass_balance
   WHERE process_key IN ('sew_winter_stock_blanket','wax_stock_rain_sheet') AND max_output_grams > min_input_grams;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V325: these make matter: %', bad; END IF;

  -- Every cover must be obtainable, or the answer to the cold is a word.
  SELECT string_agg(sc.item_key, ', ') INTO bad FROM stock_cover sc
   WHERE NOT EXISTS (SELECT 1 FROM material_process mp WHERE mp.output_item_key = sc.item_key AND mp.review_state = 'VERIFIED');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V325: no one can make these covers: %', bad; END IF;

  -- Every input must itself be obtainable.
  SELECT string_agg(DISTINCT i.item_key, ', ') INTO bad FROM material_process_input i
   WHERE i.process_key IN ('sew_winter_stock_blanket','wax_stock_rain_sheet')
     AND NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = i.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V325: inputs nobody can obtain: %', bad; END IF;

  -- The two covers answer different weather, or they are one thing with two names.
  SELECT count(DISTINCT (against_hard_cold, against_wet_cold)) INTO n FROM stock_cover;
  IF n < 2 THEN RAISE EXCEPTION 'V325: every cover answers the same weather'; END IF;

  -- No new keyword may already belong to another process, so nothing new can tie (#38).
  SELECT string_agg(DISTINCT k, ', ') INTO bad FROM (
    SELECT trim(unnest(string_to_array(keywords, ','))) k, process_key FROM material_process) x
   WHERE x.process_key NOT IN ('sew_winter_stock_blanket','wax_stock_rain_sheet')
     AND k IN (SELECT trim(unnest(string_to_array(keywords, ','))) FROM material_process
               WHERE process_key IN ('sew_winter_stock_blanket','wax_stock_rain_sheet'));
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V325: keywords already taken: %', bad; END IF;
END $$;
