-- #52/#79/#106 — what a tamed animal gives is written down, and nothing reads it.
--
-- `tamed_yield` is a three-column catalogue of exactly this: which species gives which item, how often. V266
-- wired the taking of produce and then decided all three of those things in Java instead:
--
--     if (v.contains("milk"))  { itemKey="goat_milk"; wanted="MILK"; restHours=YIELD_REST_HOURS; }
--     ...
--     case "MILK" -> "ws.species_key IN ('mountain_goat','aurochs','ox')"
--     default     -> "ws.kingdom_class = 'AVES'"
--
-- so the catalogue was consulted once, at the moment of taming, to seed `tamed_production` — and then
-- `tamed_production` was never read by anything at all. Three consequences, none of them anyone's decision:
--
--   * A REINDEER cannot be milked. The catalogue has said since V45 that a reindeer gives milk every 36 hours.
--     The Java list does not name it, so the answer is "you have nothing tamed here that gives milk".
--   * ANY TAMED BIRD lays eating-eggs, daily, because the egg clause is `kingdom_class = 'AVES'`. Tame a
--     peregrine falcon, a barn owl, a vulture or a golden eagle and gather its breakfast eggs. The catalogue
--     names the four fowl a keeper would actually gather from; the code asked the taxonomy instead.
--   * PER-SPECIES INTERVALS ARE IGNORED. Two constants stand in for the whole column, so a goat's fleece and a
--     duck's eggs are on the same clock as each other's, and one `last_yield_at` on the bond covers every
--     product at once — milking a goat makes it unshearable and shearing it makes it unmilkable.
--
-- This makes the catalogue authoritative and gives each product its own clock, which is what `tamed_production`
-- was built for. It also has to carry across the knowledge that only existed in the Java, rather than dropping it
-- on the way into the data:
--
--   * FLEECE_REST_HOURS was 720 — "shearing is a once-a-season job, not a chore" — and it was right. The
--     catalogue's 72 hours for a goat's fleece is not a shearable interval for any animal, and is corrected here.
--   * The species the Java allowed and the data never carried are added rather than lost: an aurochs and a water
--     buffalo are milked, a bighorn sheep is shorn.
--   * `ox` is dropped from the milk set deliberately. An ox is a castrated draft bull. It gives no milk, and the
--     only reason it was ever in that list is that it sits beside `aurochs` in the draft catalogue.
--
-- On the item key: `goat_milk` has stood for milk generally since V45, where a reindeer was already given it.
-- That is a name worth correcting one day; it is not this migration's business, and inventing a second milk item
-- here would leave two of them to keep in step.

ALTER TABLE tamed_yield ADD COLUMN IF NOT EXISTS yield_kind VARCHAR(20);

UPDATE tamed_yield SET yield_kind = CASE
    WHEN item_key LIKE '%egg%'  THEN 'EGG'
    WHEN item_key LIKE '%milk%' THEN 'MILK'
    WHEN item_key LIKE '%wool%' OR item_key LIKE '%fleece%' THEN 'WOOL'
    ELSE yield_kind END
WHERE yield_kind IS NULL;

-- The species the Java allowed and the catalogue never carried. Nothing a keeper could do before becomes
-- impossible now; what was impossible and should not have been becomes possible.
INSERT INTO tamed_yield (species_key, item_key, interval_hours, yield_kind) VALUES
    ('aurochs',        'goat_milk', 24,  'MILK'),
    ('water_buffalo',  'goat_milk', 24,  'MILK'),
    ('bighorn_sheep',  'wool_tuft', 720, 'WOOL'),
    -- The fowl a keeper actually keeps, so narrowing eggs from "every bird alive" loses nobody's flock.
    ('greylag_goose',  'fowl_egg',  36,  'EGG'),
    ('pheasant',       'fowl_egg',  36,  'EGG'),
    ('partridge',      'fowl_egg',  36,  'EGG'),
    ('quail',          'fowl_egg',  24,  'EGG'),
    ('grouse',         'fowl_egg',  48,  'EGG')
ON CONFLICT (species_key, item_key) DO NOTHING;

-- A fleece is not a chore. 72 hours was never a shearable interval for any animal.
UPDATE tamed_yield SET interval_hours = 720 WHERE yield_kind = 'WOOL' AND interval_hours < 240;

ALTER TABLE tamed_yield ALTER COLUMN yield_kind SET NOT NULL;
ALTER TABLE tamed_yield DROP CONSTRAINT IF EXISTS tamed_yield_kind_check;
ALTER TABLE tamed_yield ADD CONSTRAINT tamed_yield_kind_check CHECK (yield_kind IN ('EGG','MILK','WOOL'));

COMMENT ON COLUMN tamed_yield.yield_kind IS
  'What a keeper is asking for when they say "milk it", "shear it", "gather the eggs". The code maps the phrase '
  'to a kind; this table decides which species give that kind, as what item, and how often.';

-- Each product keeps its own clock, which is what tamed_production exists for and what it needs to be written
-- against. Without this a bond cannot record "milked today, not yet shorn".
DELETE FROM tamed_production a USING tamed_production b
 WHERE a.bond_id = b.bond_id AND a.item_key = b.item_key AND a.id > b.id;
ALTER TABLE tamed_production DROP CONSTRAINT IF EXISTS tamed_production_bond_item_key;
ALTER TABLE tamed_production ADD CONSTRAINT tamed_production_bond_item_key UNIQUE (bond_id, item_key);

DO $$
DECLARE wrong text; n int;
BEGIN
  -- Every kind the code can ask for must be answerable by something, or a phrase resolves to nothing forever.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['EGG','MILK','WOOL']) k
   WHERE k NOT IN (SELECT DISTINCT yield_kind FROM tamed_yield);
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V294: nothing in the world gives: %', wrong; END IF;

  -- The three the ticket started from must still be reachable.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['fowl_egg','goat_milk','wool_tuft']) k
   WHERE k NOT IN (SELECT DISTINCT item_key FROM tamed_yield);
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V294: these are declared TAMED_YIELD and nothing yields them: %', wrong; END IF;

  -- The knowledge that used to live in FLEECE_REST_HOURS, now that the column decides instead.
  SELECT string_agg(species_key || '/' || interval_hours, ', ' ORDER BY species_key) INTO wrong
    FROM tamed_yield WHERE yield_kind = 'WOOL' AND interval_hours < 240;
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V294: shearing is a once-a-season job, not a chore: %', wrong; END IF;

  -- And milk and eggs must stay daily-ish, or a keeper is left waiting a month for a pail.
  SELECT string_agg(species_key || '/' || item_key, ', ' ORDER BY species_key) INTO wrong
    FROM tamed_yield WHERE yield_kind IN ('EGG','MILK') AND interval_hours > 72;
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V294: milk and eggs come daily, not seasonally: %', wrong; END IF;

  -- The reason this migration exists: a reindeer gives milk, and must be milkable.
  IF NOT EXISTS (SELECT 1 FROM tamed_yield WHERE species_key='reindeer' AND yield_kind='MILK') THEN
    RAISE EXCEPTION 'V294: the catalogue has said since V45 that a reindeer gives milk';
  END IF;

  -- A yield nobody can tame is a row that never fires.
  SELECT string_agg(ty.species_key, ', ' ORDER BY ty.species_key) INTO wrong FROM tamed_yield ty
   WHERE NOT EXISTS (SELECT 1 FROM wildlife_species ws WHERE ws.species_key = ty.species_key);
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V294: no such species to tame: %', wrong; END IF;

  SELECT count(*) INTO n FROM tamed_yield WHERE yield_kind='EGG';
  IF n < 6 THEN RAISE EXCEPTION 'V294: only % fowl lay; narrowing eggs from every bird alive must not leave a keeper with none', n; END IF;
END $$;
