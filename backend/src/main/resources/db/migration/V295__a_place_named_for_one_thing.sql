-- #64/#215 — a place named for one thing, in a vocabulary nothing shares.
--
-- V47 wrote down the nine purposes the Wolf Kingdom actually used, explicitly so "a future chronicle's settlement
-- planning has the same vocabulary available without re-deriving it". It was re-derived. DESIGNATE writes its own
-- six tags from a chain of literals in ChronicleActionService:
--
--     SLEEPING, SANITATION, WATER, STORAGE, WORKSHOP, KNOWLEDGE
--
-- against a catalogue that holds:
--
--     ARSENAL, DRINKING, HEAVY_MANUFACTURING, LIBRARY, PARK, RESIDENTIAL, SANITATION, TEXTILE, WAREHOUSE
--
-- One word overlaps. `district_purpose` is the only table in the whole schema that no Java file so much as names,
-- and `purpose_tag` is written by one statement and read by none — so a Chronicle who walks to a spot, says "this
-- is the latrine ground", and has it recorded has changed nothing whatsoever about the world.
--
-- Two halves, and the second is the one that matters.
--
-- ONE VOCABULARY. The catalogue becomes the vocabulary, carries the words that reach it, and is enforced by a
-- foreign key so a seventh tag cannot quietly appear beside it again. The six the code wrote are mapped rather
-- than dropped: SLEEPING is RESIDENTIAL, WATER is DRINKING, STORAGE is WAREHOUSE, KNOWLEDGE is LIBRARY. WORKSHOP
-- is added, because a settlement splits heavy work from textile work and a lone Chronicle's camp bench does not,
-- and forcing "the workshop" into HEAVY_MANUFACTURING would be inventing a claim the player never made.
--
-- IT MEANS SOMETHING. `fouls_water` is the first thing anything reads off a purpose, and it answers a defect that
-- has nothing to do with vocabularies: `safeWaterSource` calls a river bank or any moving-freshwater site clean,
-- unconditionally. A keeper can foul their camp to refuse 100 with their own leavings and their livestock's muck,
-- stand on the bank, and drink water that carries no risk at all, for ever. Refuse is already wired to real
-- consequences everywhere else — it draws predators, costs the body condition, docks the shelf life of stored
-- food — and the one place it could not reach was the water, which is the first thing a fouled camp ruins.
--
-- So: fouled ground has no clean draw, and ground the Chronicle themselves designated for waste has no clean draw
-- at any refuse level, because they said what it was for. Neither is a new punishment invented for the occasion —
-- MAINTAIN_CAMP already clears refuse, and a latrine already contains it, so a fouled camp is always recoverable
-- and the water comes back with it.

ALTER TABLE district_purpose ADD COLUMN IF NOT EXISTS keywords    VARCHAR(400) NOT NULL DEFAULT '';
ALTER TABLE district_purpose ADD COLUMN IF NOT EXISTS fouls_water BOOLEAN      NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN district_purpose.keywords IS
  'Comma-separated phrases that name this purpose in an action. Longest match wins, so "drinking water" beats '
  '"water". These are what DESIGNATE reads instead of a chain of literals.';
COMMENT ON COLUMN district_purpose.fouls_water IS
  'Ground given over to this cannot also be a clean draw. Read by safeWaterSource: the Chronicle said what this '
  'place is for, and a latrine upstream of the pot is not made safe by the stream moving.';

-- A lone Chronicle's bench is not a settlement's specialised district. Added rather than folded into
-- HEAVY_MANUFACTURING, which would put words in the player's mouth.
INSERT INTO district_purpose (purpose_tag, display_name, description) VALUES
  ('WORKSHOP', 'Workshop', 'Where tools and materials are worked — a camp bench rather than a settlement''s specialised hall.')
ON CONFLICT (purpose_tag) DO NOTHING;

UPDATE district_purpose SET keywords = CASE purpose_tag
    WHEN 'SANITATION'          THEN 'latrine,sanitation,waste ground,waste area,privy,toilet,midden,dung heap,defecate,urinate'
    WHEN 'DRINKING'            THEN 'drinking water,water draw,drinking place,drinking,well,water point'
    WHEN 'RESIDENTIAL'         THEN 'sleeping ground,sleeping place,sleeping,dwelling,living quarters,bed ground,residential'
    WHEN 'WAREHOUSE'           THEN 'storehouse,store ground,storage,warehouse,stores,larder,granary'
    WHEN 'WORKSHOP'            THEN 'workshop,work ground,crafting ground,craft ground,forge,work bench,workbench'
    WHEN 'HEAVY_MANUFACTURING' THEN 'heavy manufacturing,timber yard,stone yard,sawpit,masonry yard'
    WHEN 'TEXTILE'             THEN 'textile,weaving ground,spinning ground,sewing ground,loom ground'
    WHEN 'LIBRARY'             THEN 'library,archive,knowledge,reading ground,record ground'
    WHEN 'ARSENAL'             THEN 'arsenal,armoury,armory,tool store,tool ground,weapon store'
    WHEN 'PARK'                THEN 'park,open ground,green,commons,left unbuilt'
    ELSE keywords END;

UPDATE district_purpose SET fouls_water = TRUE WHERE purpose_tag = 'SANITATION';

-- Carry across whatever a live save already recorded, then let the key hold the line.
UPDATE chronicle_named_location SET purpose_tag = CASE upper(purpose_tag)
    WHEN 'SLEEPING'  THEN 'RESIDENTIAL'
    WHEN 'WATER'     THEN 'DRINKING'
    WHEN 'STORAGE'   THEN 'WAREHOUSE'
    WHEN 'KNOWLEDGE' THEN 'LIBRARY'
    ELSE upper(purpose_tag) END
WHERE purpose_tag IS NOT NULL;

-- Anything still unrecognised was never a purpose anything could act on; it becomes an unqualified name rather
-- than a tag that blocks the key. The name itself is untouched — nobody loses a place they named.
UPDATE chronicle_named_location SET purpose_tag = NULL
 WHERE purpose_tag IS NOT NULL
   AND purpose_tag NOT IN (SELECT purpose_tag FROM district_purpose);

ALTER TABLE chronicle_named_location DROP CONSTRAINT IF EXISTS chronicle_named_location_purpose_fkey;
ALTER TABLE chronicle_named_location
  ADD CONSTRAINT chronicle_named_location_purpose_fkey
  FOREIGN KEY (purpose_tag) REFERENCES district_purpose(purpose_tag);

DO $$
DECLARE wrong text; n int;
BEGIN
  -- A purpose no phrase reaches is a row that can never be chosen.
  SELECT string_agg(purpose_tag, ', ' ORDER BY purpose_tag) INTO wrong
    FROM district_purpose WHERE btrim(keywords) = '';
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V295: nothing a Chronicle could say reaches: %', wrong; END IF;

  -- Every meaning the old literal chain could express must still be expressible, or DESIGNATE has lost a word.
  SELECT string_agg(k, ', ') INTO wrong FROM unnest(ARRAY['SANITATION','DRINKING','RESIDENTIAL','WAREHOUSE','WORKSHOP','LIBRARY']) k
   WHERE k NOT IN (SELECT purpose_tag FROM district_purpose);
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V295: DESIGNATE could say these before and must still: %', wrong; END IF;

  -- Substring collisions steal designations the way they steal processes: if one purpose's phrase contains
  -- another's, the longest-match rule is what saves it, and two purposes sharing an identical phrase is not
  -- recoverable by any rule. Caught here rather than discovered by a player naming their latrine the well.
  SELECT string_agg(DISTINCT a.purpose_tag || '/' || b.purpose_tag || ' both claim "' || ka || '"', ', ') INTO wrong
    FROM (SELECT purpose_tag, btrim(unnest(string_to_array(keywords, ','))) AS ka FROM district_purpose) a
    JOIN (SELECT purpose_tag, btrim(unnest(string_to_array(keywords, ','))) AS kb FROM district_purpose) b
      ON a.ka = b.kb AND a.purpose_tag < b.purpose_tag;
  IF wrong IS NOT NULL THEN RAISE EXCEPTION 'V295: %', wrong; END IF;

  -- The half that makes any of this matter: something must read a purpose.
  SELECT count(*) INTO n FROM district_purpose WHERE fouls_water;
  IF n <> 1 THEN
    RAISE EXCEPTION 'V295: exactly one purpose should rule out a clean draw, found %', n;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM district_purpose WHERE purpose_tag='SANITATION' AND fouls_water) THEN
    RAISE EXCEPTION 'V295: the waste ground is the one that fouls the water';
  END IF;

  -- And no designation may survive that the key cannot vouch for.
  SELECT count(*) INTO n FROM chronicle_named_location nl WHERE nl.purpose_tag IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM district_purpose dp WHERE dp.purpose_tag = nl.purpose_tag);
  IF n > 0 THEN RAISE EXCEPTION 'V295: % designation(s) name a purpose that does not exist', n; END IF;
END $$;
