-- #77 — a dunged field (compost_pit).
--
-- Fields have fertility (#164): a harvest takes 30, and fallow ground wins back 2 a day, a floodplain 5 (#156). And
-- the camp already has a manure pit and a compost bay (V263) — which today do exactly one thing, contain the muck
-- that kept stock leave on the ground. What they were FOR, for as long as anyone has kept animals and grown grain, is
-- the other half: the rotted dung goes back onto the field. Nothing read that. A keeper could stand a manure pit in
-- the middle of a worn-out field and the field rested exactly as slowly as bare ground.
--
-- WHAT IT DOES: ground where a sound manure pit or compost bay stands wins back fertility at 4 a day instead of 2.
-- Better than resting ground alone, and still short of a floodplain, where the river lays new silt every year —
-- the one kind of ground that renews itself faster than a farmer can. The faster of the two rates applies; they do
-- not add, because a flood already spreads what the heap would.
--
-- It is data, not a name: construction_kind.fertilises_field, so a midden or a byre floor that one day feeds a field
-- is a row, not another literal in PhysicalItemService.
--
-- #77's `compost_pit` IS the compost bay — a heap in a walled bay or a pit, the same thing — so its words reach it
-- rather than a second structure being built under a second name.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS fertilises_field SMALLINT NOT NULL DEFAULT 0;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='construction_kind_fertilises_field_check') THEN
    ALTER TABLE construction_kind ADD CONSTRAINT construction_kind_fertilises_field_check CHECK (fertilises_field BETWEEN 0 AND 10);
  END IF;
END $$;

COMMENT ON COLUMN construction_kind.fertilises_field IS
  'Fertility a field on this ground wins back per fallow day while this stands sound. 0 = none. The faster of this '
  'and the ground''s own rate applies; they do not add.';

UPDATE construction_kind SET fertilises_field = 4 WHERE project_kind IN ('MANURE_PIT','COMPOST_BAY');

-- compost_pit reaches the compost bay. Appended once; the guard below asserts no other assembly answers to it.
UPDATE assembly_definition
   SET keywords = keywords || ',dig a compost pit,build a compost pit,compost pit,compost heap'
 WHERE assembly_key = 'compost_bay' AND keywords NOT LIKE '%compost pit%';

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT count(*) INTO n FROM construction_kind WHERE fertilises_field > 0;
  IF n <> 2 THEN RAISE EXCEPTION 'V332: expected the manure pit and compost bay to feed fields, found % kinds', n; END IF;

  -- Muck does not outrun a river. PhysicalItemService.FERTILITY_RECOVER_PER_DAY_ON_FLOODPLAIN is 5.
  IF EXISTS (SELECT 1 FROM construction_kind WHERE fertilises_field >= 5) THEN
    RAISE EXCEPTION 'V332: a dung heap must not renew a field as fast as a flood does';
  END IF;

  SELECT string_agg(assembly_key, ', ') INTO bad
    FROM assembly_definition WHERE assembly_key <> 'compost_bay' AND keywords ILIKE '%compost pit%';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V332: another assembly also answers to compost pit: %', bad; END IF;

  -- The structures that feed a field must be buildable.
  SELECT string_agg(ck.project_kind, ', ') INTO bad FROM construction_kind ck
   WHERE ck.fertilises_field > 0
     AND NOT EXISTS (SELECT 1 FROM assembly_definition ad WHERE ad.construction_kind=ck.project_kind AND ad.review_state='VERIFIED');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V332: nobody can build these: %', bad; END IF;
END $$;
